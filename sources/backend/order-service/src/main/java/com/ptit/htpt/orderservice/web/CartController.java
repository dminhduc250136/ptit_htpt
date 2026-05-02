package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.domain.CartDto;
import com.ptit.htpt.orderservice.service.CartCrudService;
import com.ptit.htpt.orderservice.service.CartCrudService.AddItemRequest;
import com.ptit.htpt.orderservice.service.CartCrudService.MergeCartRequest;
import com.ptit.htpt.orderservice.service.CartCrudService.SetQuantityRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 18 / STORE-02: Cart REST endpoints.
 *
 * Gateway rewrite: client gọi /api/orders/cart/** → gateway rewrite → /orders/cart/** ở service này.
 * X-User-Id header forward từ gateway sau khi parse JWT sub claim (pattern Phase 6 + Phase 8).
 *
 * Error contract: 401 missing X-User-Id; 409 STOCK_SHORTAGE qua StockShortageException
 * → GlobalExceptionHandler (Phase 8) trả ApiErrorResponse với code=STOCK_SHORTAGE + details.items.
 */
@RestController
@RequestMapping("/orders/cart")
public class CartController {

  private final CartCrudService cartService;

  public CartController(CartCrudService cartService) {
    this.cartService = cartService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<CartDto>> getCart(
      @RequestHeader(name = "X-User-Id", required = false) String userId) {
    return ResponseEntity.ok(ApiResponse.of(200, "Cart loaded", cartService.getOrCreateCart(userId)));
  }

  @PostMapping("/items")
  public ResponseEntity<ApiResponse<CartDto>> addItem(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @Valid @RequestBody AddItemRequest body) {
    return ResponseEntity.ok(ApiResponse.of(200, "Item added", cartService.addItem(userId, body)));
  }

  @PatchMapping("/items/{productId}")
  public ResponseEntity<ApiResponse<CartDto>> setItem(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @PathVariable String productId,
      @Valid @RequestBody SetQuantityRequest body) {
    return ResponseEntity.ok(ApiResponse.of(200, "Item updated", cartService.setItemQuantity(userId, productId, body)));
  }

  @DeleteMapping("/items/{productId}")
  public ResponseEntity<ApiResponse<CartDto>> removeItem(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @PathVariable String productId) {
    return ResponseEntity.ok(ApiResponse.of(200, "Item removed", cartService.removeItem(userId, productId)));
  }

  @DeleteMapping
  public ResponseEntity<ApiResponse<CartDto>> clearCart(
      @RequestHeader(name = "X-User-Id", required = false) String userId) {
    return ResponseEntity.ok(ApiResponse.of(200, "Cart cleared", cartService.clearItems(userId)));
  }

  @PostMapping("/merge")
  public ResponseEntity<ApiResponse<CartDto>> merge(
      @RequestHeader(name = "X-User-Id", required = false) String userId,
      @Valid @RequestBody MergeCartRequest body) {
    return ResponseEntity.ok(ApiResponse.of(200, "Cart merged", cartService.mergeFromGuest(userId, body)));
  }
}
