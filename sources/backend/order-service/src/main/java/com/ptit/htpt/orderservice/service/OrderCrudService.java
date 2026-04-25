package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CartEntity;
import com.ptit.htpt.orderservice.domain.OrderEntity;
import com.ptit.htpt.orderservice.repository.InMemoryOrderRepository;
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
  private final InMemoryOrderRepository repository;

  public OrderCrudService(InMemoryOrderRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listCarts(int page, int size, String sort, boolean includeDeleted) {
    List<CartEntity> all = repository.findAllCarts().stream()
        .filter(cart -> includeDeleted || !cart.deleted())
        .sorted(cartComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public CartEntity getCart(String id, boolean includeDeleted) {
    CartEntity cart = repository.findCartById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found"));
    if (!includeDeleted && cart.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
    }
    return cart;
  }

  public CartEntity createCart(CartUpsertRequest request) {
    CartEntity cart = CartEntity.create(request.userId(), request.productId(), request.quantity(), request.status());
    return repository.saveCart(cart);
  }

  public CartEntity updateCart(String id, CartUpsertRequest request) {
    CartEntity current = getCart(id, true);
    CartEntity updated = current.update(request.userId(), request.productId(), request.quantity(), request.status());
    return repository.saveCart(updated);
  }

  public void deleteCart(String id) {
    CartEntity current = getCart(id, true);
    repository.saveCart(current.softDelete());
  }

  public Map<String, Object> listOrders(int page, int size, String sort, boolean includeDeleted) {
    List<OrderEntity> all = repository.findAllOrders().stream()
        .filter(order -> includeDeleted || !order.deleted())
        .sorted(orderComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public OrderEntity getOrder(String id, boolean includeDeleted) {
    OrderEntity order = repository.findOrderById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (!includeDeleted && order.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    }
    return order;
  }

  public OrderEntity createOrder(OrderUpsertRequest request) {
    OrderEntity order = OrderEntity.create(request.userId(), request.totalAmount(), request.status(), request.note());
    return repository.saveOrder(order);
  }

  /**
   * Domain entry point for the FE checkout flow. Derives totalAmount from items, defaults status
   * to PENDING, persists via the existing OrderEntity factory. The userId is supplied by the
   * controller after reading the X-User-Id session header — the service treats it as already
   * trusted (Phase 5: replace header trust with JWT claim verification at the gateway).
   */
  public OrderEntity createOrderFromCommand(String userId, CreateOrderCommand command) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-User-Id session header");
    }

    BigDecimal totalAmount = command.items().stream()
        .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    String note = command.note();   // null is fine — OrderEntity.note is nullable

    OrderEntity order = OrderEntity.create(userId, totalAmount, "PENDING", note);
    return repository.saveOrder(order);
  }

  public OrderEntity updateOrder(String id, OrderUpsertRequest request) {
    OrderEntity current = getOrder(id, true);
    OrderEntity updated = current.update(request.userId(), request.totalAmount(), request.status(), request.note());
    return repository.saveOrder(updated);
  }

  public OrderEntity updateOrderState(String id, OrderStateRequest request) {
    OrderEntity current = getOrder(id, true);
    return repository.saveOrder(current.setStatus(request.state()));
  }

  public void deleteOrder(String id) {
    OrderEntity current = getOrder(id, true);
    repository.saveOrder(current.softDelete());
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
   * Domain command shape consumed by the FE checkout page (sources/frontend/src/app/checkout/page.tsx).
   * userId is NOT in the body — the controller derives it from the X-User-Id header so the FE never
   * has to know its own user id (consistent with the typical session/JWT pattern; full JWT-claim
   * derivation is a Phase 5 follow-up).
   * status is NOT in the body — service layer defaults it to PENDING.
   * totalAmount is NOT in the body — service layer computes it from items[].quantity * items[].unitPrice
   * (the FE sends the cart's price snapshot; mismatch with current product-service price is allowed for
   * this MVP — Phase 5 candidate to re-fetch authoritative price server-side).
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
