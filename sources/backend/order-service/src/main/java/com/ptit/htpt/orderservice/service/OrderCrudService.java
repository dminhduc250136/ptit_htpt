package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CartEntity;
import com.ptit.htpt.orderservice.domain.OrderDto;
import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.domain.OrderMapper;
import com.ptit.htpt.orderservice.repository.InMemoryCartRepository;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderCrudService {
  private final InMemoryCartRepository cartRepository;
  private final OrderRepository orderRepository;

  public OrderCrudService(InMemoryCartRepository cartRepository, OrderRepository orderRepository) {
    this.cartRepository = cartRepository;
    this.orderRepository = orderRepository;
  }

  public Map<String, Object> listCarts(int page, int size, String sort, boolean includeDeleted) {
    List<CartEntity> all = cartRepository.findAllCarts().stream()
        .filter(cart -> includeDeleted || !cart.deleted())
        .sorted(cartComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public CartEntity getCart(String id, boolean includeDeleted) {
    CartEntity cart = cartRepository.findCartById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
    if (!includeDeleted && cart.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
    }
    return cart;
  }

  public CartEntity createCart(CartUpsertRequest request) {
    CartEntity cart = CartEntity.create(request.userId(), request.productId(), request.quantity(), request.status());
    return cartRepository.saveCart(cart);
  }

  public CartEntity updateCart(String id, CartUpsertRequest request) {
    CartEntity current = getCart(id, true);
    CartEntity updated = current.update(request.userId(), request.productId(), request.quantity(), request.status());
    return cartRepository.saveCart(updated);
  }

  public void deleteCart(String id) {
    CartEntity current = getCart(id, true);
    cartRepository.saveCart(current.softDelete());
  }

  public Map<String, Object> listOrders(int page, int size, String sort, boolean includeDeleted) {
    // @SQLRestriction filters deleted=false at JPA layer; includeDeleted=true cần native query.
    // Phase 5: nếu includeDeleted=true vẫn chỉ trả non-deleted (admin endpoints có thể dùng
    // native query Phase 8). Behavior này document trong SUMMARY.
    List<OrderEntity> all = orderRepository.findAll().stream()
        .sorted(orderComparator(sort))
        .toList();
    return paginate(all.stream().map(OrderMapper::toDto).toList(), page, size);
  }

  public OrderDto getOrder(String id, boolean includeDeleted) {
    OrderEntity order = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    return OrderMapper.toDto(order);
  }

  public OrderDto createOrder(OrderUpsertRequest request) {
    OrderEntity order = OrderEntity.create(request.userId(), request.totalAmount(), request.status(), request.note());
    return OrderMapper.toDto(orderRepository.save(order));
  }

  /**
   * Domain entry point for the FE checkout flow. Derives totalAmount from items, defaults status
   * to PENDING, persists via OrderEntity factory. userId supplied by controller after reading
   * X-User-Id session header — Phase 6 sẽ replace với JWT claim verification at gateway.
   */
  public OrderDto createOrderFromCommand(String userId, CreateOrderCommand command) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-User-Id session header");
    }

    BigDecimal totalAmount = command.items().stream()
        .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    String note = command.note();   // null OK — OrderEntity.note nullable

    OrderEntity order = OrderEntity.create(userId, totalAmount, "PENDING", note);
    return OrderMapper.toDto(orderRepository.save(order));
  }

  public OrderDto updateOrder(String id, OrderUpsertRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.update(request.userId(), request.totalAmount(), request.status(), request.note());
    return OrderMapper.toDto(orderRepository.save(current));
  }

  public OrderDto updateOrderState(String id, OrderStateRequest request) {
    OrderEntity current = orderRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    current.setStatus(request.state());
    return OrderMapper.toDto(orderRepository.save(current));
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

  private Comparator<CartEntity> cartComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(CartEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<CartEntity> comparator = sort.startsWith("userId")
        ? Comparator.comparing(CartEntity::userId, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(CartEntity::id);
    return desc ? comparator.reversed() : comparator;
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

  public record CartUpsertRequest(
      @NotBlank String userId,
      @NotBlank String productId,
      @Min(1) int quantity,
      @NotBlank String status
  ) {}

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
      String note
  ) {}

  public record OrderItemRequest(
      @NotBlank String productId,
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
}
