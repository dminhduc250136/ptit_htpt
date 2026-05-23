package com.ptit.htpt.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptit.htpt.orderservice.domain.CouponEntity;
import com.ptit.htpt.orderservice.domain.OrderDto;
import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderItemEntity;
import com.ptit.htpt.orderservice.domain.OrderMapper;
import com.ptit.htpt.orderservice.exception.StockShortageException;
import com.ptit.htpt.orderservice.exception.StockShortageException.StockShortageItem;
import com.ptit.htpt.orderservice.messaging.event.OrderEventEnvelope;
import com.ptit.htpt.orderservice.messaging.publisher.OrderEventPublisher;
import com.ptit.htpt.orderservice.repository.OrderItemRepository;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderCrudService {
  private static final Logger log = LoggerFactory.getLogger(OrderCrudService.class);
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final CouponRedemptionService couponRedemptionService;
  private final OrderEventPublisher orderEventPublisher;
  private final PaymentSessionClient paymentSessionClient;

  public OrderCrudService(OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          ObjectMapper objectMapper,
                          RestTemplate restTemplate,
                          CouponRedemptionService couponRedemptionService,
                          OrderEventPublisher orderEventPublisher,
                          PaymentSessionClient paymentSessionClient) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
    this.couponRedemptionService = couponRedemptionService;
    this.orderEventPublisher = orderEventPublisher;
    this.paymentSessionClient = paymentSessionClient;
  }

  /**
   * Bug fix (orders-api-500): dùng findAllWithItems() để LEFT JOIN FETCH items.
   * Trước đây findAll() trả LAZY collection → OrderMapper.toDto() iterate items
   * ngoài transaction (open-in-view=false) → LazyInitializationException → 500.
   * @Transactional(readOnly=true) thêm như defense-in-depth: giữ session mở
   * suốt method nếu sau này có lazy access khác.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> listOrders(int page, int size, String sort, boolean includeDeleted) {
    // @SQLRestriction filters deleted=false at JPA layer; includeDeleted=true cần native query.
    // Phase 5: nếu includeDeleted=true vẫn chỉ trả non-deleted (admin endpoints có thể dùng
    // native query Phase 8). Behavior này document trong SUMMARY.
    List<OrderEntity> all = orderRepository.findAllWithItems().stream()
        .sorted(orderComparator(sort))
        .toList();
    return paginate(all.stream().map(OrderMapper::toDto).toList(), page, size);
  }

  /**
   * Bug fix (orders-api-500): dùng findByIdWithItems() để fetch-join items —
   * tránh LazyInitializationException khi map sang DTO ngoài transaction.
   */
  @Transactional(readOnly = true)
  public OrderDto getOrder(String id, boolean includeDeleted) {
    OrderEntity order = orderRepository.findByIdWithItems(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    return OrderMapper.toDto(order);
  }

  /**
   * BUG-FIX (orders-cross-user-leak): User-facing GET /orders/{id} phải kiểm tra ownership.
   * Trả 404 (không 403) để tránh leak existence của order của user khác.
   */
  @Transactional(readOnly = true)
  public OrderDto getOrderForUser(String id, String userId) {
    OrderEntity order = orderRepository.findByIdWithItems(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!userId.equals(order.userId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    return OrderMapper.toDto(order);
  }

  public OrderDto createOrder(OrderUpsertRequest request) {
    OrderEntity order = OrderEntity.create(request.userId(), request.totalAmount(), request.status(), request.note());
    return OrderMapper.toDto(orderRepository.save(order));
  }

  /**
   * Domain entry point for the FE checkout flow. Derives totalAmount from items, defaults status
   * to PENDING, persists via OrderEntity factory + per-item OrderItemEntity cascade.
   * userId supplied by controller after reading X-User-Id session header.
   *
   * <p>Phase 8 Plan 02: persist items (D-06 productName snapshot) + shippingAddress (D-08) + paymentMethod (D-09).
   * D-04 stock validate + D-05 stock deduct wired in Task 4.
   *
   * <p>Bug fix (orders-api-500): @Transactional bao toàn bộ persist + map flow.
   * Trước đây OrderMapper.toDto(saved) chạy ngoài tx → nếu Hibernate detach entity và
   * mapper iterate items → có thể trigger lazy proxy. items vừa được addItem() trong cùng
   * scope nên hiện tại an toàn, nhưng @Transactional là defense-in-depth.
   */
  /**
   * Backward-compat overload cho code cũ và test không truyền authHeader (COD path).
   */
  @Transactional
  public OrderDto createOrderFromCommand(String userId, CreateOrderCommand command) {
    return createOrderFromCommand(userId, command, null);
  }

  /**
   * Phase 26 / Plan 26-03 (D-04, D-09, D-10): thêm authHeader để forward Bearer JWT
   * cho PaymentSessionClient khi tạo đơn VNPAY (T-26-10).
   */
  @Transactional
  public OrderDto createOrderFromCommand(String userId, CreateOrderCommand command,
                                         String authHeader) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-User-Id session header");
    }

    // D-04: Validate stock trước khi persist — throw 409 STOCK_SHORTAGE nếu quantity > stock
    validateStockOrThrow(command.items());

    // D-10: Server compute subtotal từ items — KHÔNG tin client cartTotal
    BigDecimal subtotal = command.items().stream()
        .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Serialize shippingAddress thành JSON string để lưu JSONB (D-08)
    String shippingAddressJson;
    try {
      shippingAddressJson = objectMapper.writeValueAsString(command.shippingAddress());
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid shippingAddress format");
    }

    // Tạo OrderEntity với subtotal làm total mặc định (no coupon path)
    OrderEntity order = OrderEntity.create(userId, subtotal, "PENDING", command.note());
    order.setShippingAddress(shippingAddressJson);
    order.setPaymentMethod(command.paymentMethod());

    // === COUPON STEP (D-08, D-09, D-10, D-12) ===
    // KHÔNG tin client discountAmount — server compute lại từ subtotal qua
    // CouponPreviewService.computeDiscount. atomicRedeem trong cùng @Transactional
    // → fail (CONFLICT_OR_EXHAUSTED hoặc ALREADY_REDEEMED) → toàn transaction rollback.
    if (command.couponCode() != null && !command.couponCode().isBlank()) {
      // D-09: re-fetch coupon by code (KHÔNG dùng id từ preview — tránh TOCTOU window)
      CouponEntity coupon = couponRedemptionService.atomicRedeem(
          command.couponCode(), userId, order.id());
      // D-10: server-side discount math từ subtotal (cap ≤ subtotal)
      BigDecimal discountAmount = CouponPreviewService.computeDiscount(coupon, subtotal);
      order.setDiscountAmount(discountAmount);
      order.setCouponCode(coupon.code());
      order.setTotal(subtotal.subtract(discountAmount));
    }
    // No coupon path: discountAmount giữ default BigDecimal.ZERO, couponCode null

    // Tạo OrderItemEntity cho từng item — cascade ALL sẽ persist cùng order
    // D-06: productName snapshot lấy trực tiếp từ command (FE truyền item.name từ cart state)
    for (OrderItemRequest itemReq : command.items()) {
      OrderItemEntity item = OrderItemEntity.create(
          order,
          itemReq.productId(),
          itemReq.productName(),   // D-06: productName snapshot — KHÔNG dùng productId làm placeholder
          itemReq.quantity(),
          itemReq.unitPrice()
      );
      order.addItem(item);
    }

    OrderEntity saved = orderRepository.save(order);

    // Phase 26 / Plan 26-03 (D-09, D-10): rẽ nhánh theo paymentMethod.
    // Phase 26.1 (D-08): đổi nhánh VNPAY → MOMO + dùng createMomoSession.
    // MOMO → gọi payment-service tạo session, KHÔNG publish OrderPlaced ngay (D-09).
    // non-MOMO (COD...) → publish OrderPlaced ngay như cũ (D-10).
    if ("MOMO".equalsIgnoreCase(command.paymentMethod())) {
      // D-09: set PENDING + gọi payment-service lấy paymentUrl. KHÔNG publish OrderPlaced.
      // amountVnd = total (đã trừ discount)
      saved.setPaymentStatus("PENDING");
      String paymentUrl = paymentSessionClient.createMomoSession(
          saved.id(),
          saved.total().longValue(),
          saved.id(),   // orderInfo = mã đơn
          authHeader
      );
      // Build DTO thủ công với paymentUrl (transient field KHÔNG trong entity)
      OrderDto baseDto = OrderMapper.toDto(saved);
      return new OrderDto(
          baseDto.id(), baseDto.userId(), baseDto.total(), baseDto.status(), baseDto.note(),
          baseDto.items(), baseDto.shippingAddress(), baseDto.paymentMethod(),
          baseDto.discountAmount(), baseDto.couponCode(),
          baseDto.paymentStatus(), baseDto.paymentTransactionNo(), paymentUrl,
          baseDto.createdAt(), baseDto.updatedAt()
      );
    } else {
      // D-10: COD và non-MOMO → publish OrderPlaced ngay (inventory trừ kho async).
      // Phase 23 D-03/D-12: thay vì gọi REST deductStock đồng bộ (đã XÓA), publish event
      // OrderPlaced SAU khi DB commit. inventory-service consumer trừ kho async (Wave 2).
      // OrderEventPublisher tự defer publish qua TransactionSynchronizationManager.afterCommit;
      // capture MDC traceId NGAY tại thread này (Pitfall 1 — afterCommit có thể MDC empty).
      publishOrderPlacedForOrder(saved);
      return OrderMapper.toDto(saved);
    }
  }

  /**
   * Phase 26 / Plan 26-03 (D-09): helper DRY publish OrderPlaced cho 1 OrderEntity.
   * Dùng chung bởi:
   *   - createOrderFromCommand nhánh non-MOMO (COD) ngay sau save
   *   - PaymentEventListener nhánh PaymentSucceeded (trừ kho khi IPN xác nhận PAID)
   * Phase 27 D-11: payload gồm productName per item + customerEmail (resolveCustomerEmail).
   */
  public void publishOrderPlacedForOrder(OrderEntity order) {
    List<OrderEventEnvelope.Item> payloadItems = order.items().stream()
        .map(it -> new OrderEventEnvelope.Item(it.productId(), it.productName(), it.quantity(), it.unitPrice()))
        .toList();
    // Phase 27 D-11: customerEmail fetch từ user-service qua resolveCustomerEmail()
    // KHÔNG gọi REST từ notification-service — mọi dữ liệu phải có sẵn trong event payload
    OrderEventEnvelope.OrderPlacedPayload payload = new OrderEventEnvelope.OrderPlacedPayload(
        order.id(),
        order.userId(),
        resolveCustomerEmail(order),
        payloadItems,
        order.total(),
        "VND"
    );
    orderEventPublisher.publishOrderPlaced(payload);
  }

  public OrderDto updateOrder(String id, OrderUpsertRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.update(request.userId(), request.totalAmount(), request.status(), request.note());
    return OrderMapper.toDto(orderRepository.save(current));
  }

  @Transactional
  public OrderDto updateOrderState(String id, OrderStateRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.setStatus(request.state());
    OrderEntity saved = orderRepository.save(current);

    // Phase 27 D-12: publish OrderStatusChanged afterCommit khi admin đổi trạng thái
    // notification-service consumer sẽ lọc chỉ gửi email cho shipped/delivered/cancelled
    OrderEventEnvelope.OrderStatusChangedPayload statusPayload =
        new OrderEventEnvelope.OrderStatusChangedPayload(
            saved.id(),
            saved.userId(),
            resolveCustomerEmail(saved),
            request.state(),
            null  // customerName optional — notification-service có thể bỏ qua null
        );
    orderEventPublisher.publishOrderStatusChanged(statusPayload);

    return OrderMapper.toDto(saved);
  }

  public void deleteOrder(String id) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    // @SQLDelete annotation → soft-delete (UPDATE deleted=true)
    orderRepository.delete(current);
  }

  public List<OrderDto> findOrdersByUserId(String userId) {
    return orderRepository.findByUserId(userId).stream()
        .map(OrderMapper::toDto)
        .toList();
  }

  /**
   * Phase 11 / ACCT-02 (D-12, D-13, D-14, D-15): Filter orders server-side theo userId + optional params.
   * D-14: Date string "YYYY-MM-DD" → from = start-of-day UTC+7, to = 23:59:59 UTC+7.
   * D-13: q keyword tìm trên order.id ILIKE — không join order items.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> listMyOrders(ListMyOrdersQuery query) {
    // Parse status: null hoặc "ALL" → pass null vào repository (bỏ qua filter)
    String statusFilter = (query.status() == null || "ALL".equalsIgnoreCase(query.status()))
        ? null : query.status();

    // D-14: parse date string "YYYY-MM-DD" → Instant UTC+7
    // from → 2026-04-01T00:00:00+07:00 (start of day Saigon)
    // to   → 2026-04-30T23:59:59+07:00 (end of day Saigon — SC-5: không miss đơn 23:59 GMT+7)
    ZoneOffset saigon = ZoneOffset.of("+07:00");
    Instant fromInstant = null;
    Instant toInstant = null;
    if (query.from() != null && !query.from().isBlank()) {
      fromInstant = LocalDate.parse(query.from()).atStartOfDay().toInstant(saigon);
    }
    if (query.to() != null && !query.to().isBlank()) {
      // end of day: 23:59:59 (+07:00)
      toInstant = LocalDate.parse(query.to()).atTime(23, 59, 59).toInstant(saigon);
    }

    // D-13: q = null/blank → pass null (bỏ qua filter)
    String qFilter = (query.q() == null || query.q().isBlank()) ? null : query.q().trim();

    List<OrderEntity> filtered = orderRepository.findByUserIdWithFilters(
        query.userId(), statusFilter, fromInstant, toInstant, qFilter
    );

    // Map sang DTO trước khi paginate
    List<OrderDto> dtos = filtered.stream().map(OrderMapper::toDto).toList();
    return paginate(dtos, query.page(), query.size());
  }

  private Comparator<OrderEntity> orderComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(OrderEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<OrderEntity> comparator = sort.startsWith("status")
        ? Comparator.comparing(OrderEntity::status, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(OrderEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record OrderUpsertRequest(
      @NotBlank String userId,
      @DecimalMin("0.0") BigDecimal totalAmount,
      @NotBlank String status,
      String note
  ) {}

  public record OrderStateRequest(@NotBlank String state) {}

  /**
   * Domain command shape consumed by FE checkout page (sources/frontend/src/app/checkout/page.tsx).
   * Phase 5 chỉ persist OrderEntity basic — shippingAddress + paymentMethod KHÔNG persist (Phase 8
   * PERSIST-02 sẽ thêm OrderItemEntity per-row + shippingAddress + paymentMethod).
   */
  public record CreateOrderCommand(
      @NotEmpty List<@Valid OrderItemRequest> items,
      @NotNull @Valid ShippingAddressRequest shippingAddress,
      @NotBlank String paymentMethod,
      String note,
      // Phase 20 / Plan 20-03 (D-12): optional coupon code. null/blank → tạo order như cũ
      // (backward compat). Validation lỏng — server sẽ tra DB qua atomicRedeem để xác thực.
      String couponCode
  ) {}

  public record OrderItemRequest(
      @NotBlank String productId,
      @NotBlank String productName,
      @Min(1) int quantity,
      @NotNull @DecimalMin("0.0") BigDecimal unitPrice
  ) {}

  public record ShippingAddressRequest(
      @NotBlank String street,
      @NotBlank String ward,
      @NotBlank String district,
      @NotBlank String city,
      String zipCode
  ) {}

  /**
   * Phase 11 / ACCT-02 (D-10, D-11, D-12, D-13, D-14, D-15).
   * Filter params cho GET /orders từ user đang đăng nhập.
   * status=null → ALL. from/to = YYYY-MM-DD string → convert sang Instant UTC+7 (D-14).
   * q = order ID keyword → ILIKE (D-13).
   */
  public record ListMyOrdersQuery(
      String userId,    // required — từ X-User-Id header (Phase 11 giữ nguyên pattern cũ)
      String status,    // nullable — "PENDING"/"CONFIRMED"/"SHIPPING"/"DELIVERED"/"CANCELLED"
      String from,      // nullable — "YYYY-MM-DD"
      String to,        // nullable — "YYYY-MM-DD"
      String q,         // nullable — keyword search on order.id
      int page,
      int size,
      String sort
  ) {}

  /**
   * D-04: Validate stock cho từng item trước khi persist order.
   * Gọi GET /api/products/{productId} qua gateway để lấy stock thật.
   * Nếu bất kỳ item nào có quantity > stock → throw StockShortageException (→ 409 CONFLICT).
   * Nếu product-service không trả được (timeout, 404) → log.warn và bỏ qua item đó (best-effort MVP).
   */
  private void validateStockOrThrow(List<OrderItemRequest> items) {
    List<StockShortageItem> shortages = new ArrayList<>();
    for (OrderItemRequest item : items) {
      try {
        String url = "http://api-gateway:8080/api/products/" + item.productId();
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = restTemplate.getForObject(url, Map.class);
        Map<String, Object> product = unwrapEnvelope(raw);
        if (product == null) {
          log.warn("[D-04] product-service returned null for productId={}", item.productId());
          continue;
        }
        // product-service response (sau unwrap envelope): {id, name, stock, ...} — Plan 08-01
        Object stockObj = product.get("stock");
        int stock = stockObj == null ? 0 : ((Number) stockObj).intValue();
        if (item.quantity() > stock) {
          shortages.add(new StockShortageItem(
              item.productId(),
              item.productName(),
              item.quantity(),
              stock
          ));
        }
      } catch (Exception ex) {
        // product-service không available — log và bỏ qua validate cho item này (MVP best-effort)
        log.warn("[D-04] Could not fetch stock for productId={}: {}", item.productId(), ex.getMessage());
      }
    }
    if (!shortages.isEmpty()) {
      throw new StockShortageException(shortages);
    }
  }

  /**
   * Product-service trả ApiResponse envelope { status, message, data: { ... } }.
   * BUG-FIX (cart-stock-shortage-false-positive): unwrap `data` trước khi đọc field
   * — nếu không, mọi product.get("stock") trả null → false STOCK_SHORTAGE.
   *
   * GIỮ LẠI vì validateStockOrThrow vẫn cần unwrap envelope của product-service (D-11).
   * Phase 23 D-12 chỉ XÓA stock-deduct REST, NOT stock-validate REST.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrapEnvelope(Map<String, Object> raw) {
    if (raw == null) return null;
    Object data = raw.get("data");
    if (data instanceof Map) return (Map<String, Object>) data;
    return raw; // fallback: response không phải envelope (legacy/contract test)
  }

  /**
   * Phase 27 D-11: Lấy customerEmail từ user-service qua REST.
   * order-service đã có RestTemplate (dùng cho stock validate Phase 23 D-11).
   *
   * Strategy (Phase 27 fix bug #5):
   *  1. Gọi user-service GET /internal/users/{userId}/email TRỰC TIẾP qua docker network
   *     (KHÔNG qua gateway — gateway yêu cầu JWT mà order-service nội bộ không có).
   *     Endpoint /internal/** KHÔNG expose ngoài (Phase 25 đã bỏ port mapping user-service).
   *  2. Nếu không lấy được (timeout/404/exception) → fallback "" + log WARN
   *     → notification-service sẽ ghi SKIPPED trong dispatch_log (không crash).
   *
   * @param order OrderEntity đã save — dùng userId() để fetch
   * @return email string hoặc "" nếu không lấy được
   */
  private String resolveCustomerEmail(OrderEntity order) {
    try {
      String url = "http://user-service:8080/internal/users/" + order.userId() + "/email";
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = restTemplate.getForObject(url, Map.class);
      // user-service wrap response qua ApiResponseAdvice → {status, message, data: {email, fullName}}
      Map<String, Object> user = unwrapEnvelope(raw);
      if (user == null) {
        log.warn("[EMAIL-RESOLVE] user-service returned null for userId={}", order.userId());
        return "";
      }
      Object email = user.get("email");
      if (email == null || email.toString().isBlank()) {
        log.warn("[EMAIL-RESOLVE] user-service returned blank email for userId={}", order.userId());
        return "";
      }
      return email.toString();
    } catch (Exception ex) {
      // user-service không available hoặc endpoint không tồn tại — fallback an toàn
      log.warn("[EMAIL-RESOLVE] Cannot fetch email for userId={}: {}", order.userId(), ex.getMessage());
      return "";
    }
  }
}
