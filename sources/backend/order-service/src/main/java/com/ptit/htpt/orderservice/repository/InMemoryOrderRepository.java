package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CartEntity;
import com.ptit.htpt.orderservice.domain.OrderEntity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryOrderRepository {
  private final Map<String, CartEntity> carts = new LinkedHashMap<>();
  private final Map<String, OrderEntity> orders = new LinkedHashMap<>();

  public Collection<CartEntity> findAllCarts() {
    return carts.values();
  }

  public Optional<CartEntity> findCartById(String id) {
    return Optional.ofNullable(carts.get(id));
  }

  public CartEntity saveCart(CartEntity cart) {
    carts.put(cart.id(), cart);
    return cart;
  }

  public Collection<OrderEntity> findAllOrders() {
    return orders.values();
  }

  public Optional<OrderEntity> findOrderById(String id) {
    return Optional.ofNullable(orders.get(id));
  }

  public OrderEntity saveOrder(OrderEntity order) {
    orders.put(order.id(), order);
    return order;
  }
}
