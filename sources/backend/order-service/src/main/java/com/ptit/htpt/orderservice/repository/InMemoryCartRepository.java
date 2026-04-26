package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CartEntity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Cart in-memory store — Phase 5 chỉ refactor Order sang JPA. Cart giữ in-memory,
 * Phase 8 sẽ migrate sang DB cùng với OrderItemEntity (PERSIST-02).
 */
@Repository
public class InMemoryCartRepository {
  private final Map<String, CartEntity> carts = new LinkedHashMap<>();

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
}
