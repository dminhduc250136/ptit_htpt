package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.domain.CartDto;
import com.ptit.htpt.orderservice.domain.CartEntity;
import com.ptit.htpt.orderservice.domain.CartItemEntity;
import com.ptit.htpt.orderservice.domain.CartMapper;
import com.ptit.htpt.orderservice.exception.StockShortageException;
import com.ptit.htpt.orderservice.exception.StockShortageException.StockShortageItem;
import com.ptit.htpt.orderservice.repository.CartItemRepository;
import com.ptit.htpt.orderservice.repository.CartRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartCrudService {
  private static final Logger log = LoggerFactory.getLogger(CartCrudService.class);

  private final CartRepository cartRepository;
  private final CartItemRepository cartItemRepository;
  private final RestTemplate restTemplate;
  @PersistenceContext private EntityManager entityManager;

  public CartCrudService(CartRepository cartRepository,
                         CartItemRepository cartItemRepository,
                         RestTemplate restTemplate) {
    this.cartRepository = cartRepository;
    this.cartItemRepository = cartItemRepository;
    this.restTemplate = restTemplate;
  }

  /** GET /cart — lazy-create empty cart row nếu chưa có (D-04). */
  @Transactional
  public CartDto getOrCreateCart(String userId) {
    requireUserId(userId);
    return CartMapper.toDto(getOrCreateEntity(userId));
  }

  // NOTE: Race window — concurrent ADD requests có thể tích lũy quantity vượt stock vì pre-validate dùng SELECT non-locking.
  // Mitigation cuối là Phase 8 OrderCrudService re-validate stock tại checkout (D-04 Phase 8). Acceptable cho MVP.
  /** POST /cart/items — idempotent ADD (cộng dồn quantity). D-05. */
  @Transactional
  public CartDto addItem(String userId, AddItemRequest req) {
    requireUserId(userId);
    CartEntity cart = getOrCreateEntity(userId);

    int currentQty = cartItemRepository.findByCartIdAndProductId(cart.id(), req.productId())
        .map(CartItemEntity::quantity).orElse(0);
    int newTotalQty = currentQty + req.quantity();
    validateStockOrThrow(req.productId(), newTotalQty);

    String newItemId = UUID.randomUUID().toString();
    cartItemRepository.upsertAddQuantity(newItemId, cart.id(), req.productId(), req.quantity());
    cart.touch();
    cartRepository.save(cart);

    // Native bypass JPA cache → flush+clear để query lại thấy giá trị mới
    entityManager.flush();
    entityManager.clear();
    return CartMapper.toDto(cartRepository.findByUserId(userId).orElseThrow());
  }

  /** PATCH /cart/items/{productId} — SET absolute quantity. D-06. quantity <= 0 → DELETE alias. */
  @Transactional
  public CartDto setItemQuantity(String userId, String productId, SetQuantityRequest req) {
    requireUserId(userId);
    if (req.quantity() <= 0) {
      return removeItem(userId, productId);
    }
    CartEntity cart = getOrCreateEntity(userId);
    validateStockOrThrow(productId, req.quantity());

    Optional<CartItemEntity> existing = cartItemRepository.findByCartIdAndProductId(cart.id(), productId);
    if (existing.isPresent()) {
      existing.get().setQuantity(req.quantity());
      cartItemRepository.save(existing.get());
    } else {
      // SET trên item chưa tồn tại → tạo mới
      CartItemEntity item = CartItemEntity.create(cart, productId, req.quantity());
      cart.addItem(item);
      cartItemRepository.save(item);
    }
    cart.touch();
    cartRepository.save(cart);
    return CartMapper.toDto(getOrCreateEntity(userId));
  }

  /** DELETE /cart/items/{productId} — remove single. D-07. */
  @Transactional
  public CartDto removeItem(String userId, String productId) {
    requireUserId(userId);
    CartEntity cart = getOrCreateEntity(userId);
    cartItemRepository.findByCartIdAndProductId(cart.id(), productId)
        .ifPresent(item -> {
          cart.removeItem(item);
          cartItemRepository.delete(item);
        });
    cart.touch();
    cartRepository.save(cart);
    return CartMapper.toDto(cart);
  }

  /** DELETE /cart — clear toàn bộ items, giữ cart row. D-07. */
  @Transactional
  public CartDto clearItems(String userId) {
    requireUserId(userId);
    CartEntity cart = getOrCreateEntity(userId);
    cart.items().clear(); // orphanRemoval=true → cascade DELETE
    cart.touch();
    cartRepository.save(cart);
    return CartMapper.toDto(cart);
  }

  /**
   * POST /cart/merge — idempotent merge guest cart vào DB cart. D-08.
   * Cho mỗi item: cộng dồn quantity với DB hiện tại, clamp by stock (best-effort).
   * Stock fail cho 1 item KHÔNG block toàn bộ merge — clamp về stock available.
   */
  @Transactional
  public CartDto mergeFromGuest(String userId, MergeCartRequest req) {
    requireUserId(userId);
    CartEntity cart = getOrCreateEntity(userId);

    for (MergeItem item : req.items()) {
      int currentQty = cartItemRepository.findByCartIdAndProductId(cart.id(), item.productId())
          .map(CartItemEntity::quantity).orElse(0);
      int desiredTotal = currentQty + item.quantity();
      int clampedTotal = clampToStock(item.productId(), desiredTotal);
      int delta = clampedTotal - currentQty;
      if (delta <= 0) continue; // đã >= stock, skip

      String newItemId = UUID.randomUUID().toString();
      cartItemRepository.upsertAddQuantity(newItemId, cart.id(), item.productId(), delta);
    }
    cart.touch();
    cartRepository.save(cart);
    entityManager.flush();
    entityManager.clear();
    return CartMapper.toDto(cartRepository.findByUserId(userId).orElseThrow());
  }

  // ===== helpers =====

  private CartEntity getOrCreateEntity(String userId) {
    Optional<CartEntity> existing = cartRepository.findByUserId(userId);
    if (existing.isPresent()) return existing.get();
    try {
      return cartRepository.save(CartEntity.create(userId));
    } catch (DataIntegrityViolationException race) {
      // 2 request cùng user_id race INSERT → unique constraint violation → retry findByUserId
      log.warn("[cart] race on getOrCreate userId={}: retrying find", userId);
      return cartRepository.findByUserId(userId).orElseThrow(() -> race);
    }
  }

  private void requireUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id session header");
    }
  }

  /** Validate quantity <= stock. Fail → throw StockShortageException (409). Pattern reuse OrderCrudService. */
  private void validateStockOrThrow(String productId, int requestedQuantity) {
    try {
      String url = "http://api-gateway:8080/api/products/" + productId;
      @SuppressWarnings("unchecked")
      Map<String, Object> product = restTemplate.getForObject(url, Map.class);
      if (product == null) {
        log.warn("[cart] product-service returned null for productId={}", productId);
        return; // best-effort: nếu product-svc không response, KHÔNG block
      }
      Object stockObj = product.get("stock");
      int stock = stockObj == null ? 0 : ((Number) stockObj).intValue();
      String productName = product.get("name") == null ? productId : product.get("name").toString();
      if (requestedQuantity > stock) {
        throw new StockShortageException(List.of(
            new StockShortageItem(productId, productName, requestedQuantity, stock)
        ));
      }
    } catch (StockShortageException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("[cart] stock fetch failed productId={}: {}", productId, ex.getMessage());
      // Best-effort MVP — không block mutation nếu product-svc unreachable
    }
  }

  /** Clamp desired quantity về stock available cho merge flow (no throw). */
  private int clampToStock(String productId, int desired) {
    try {
      String url = "http://api-gateway:8080/api/products/" + productId;
      @SuppressWarnings("unchecked")
      Map<String, Object> product = restTemplate.getForObject(url, Map.class);
      if (product == null) return desired;
      Object stockObj = product.get("stock");
      int stock = stockObj == null ? Integer.MAX_VALUE : ((Number) stockObj).intValue();
      return Math.min(desired, stock);
    } catch (Exception ex) {
      log.warn("[cart-merge] clamp fetch failed productId={}: {}", productId, ex.getMessage());
      return desired;
    }
  }

  // ===== request records =====

  public record AddItemRequest(@NotBlank String productId, @Min(1) int quantity) {}
  public record SetQuantityRequest(@NotNull Integer quantity) {} // allow 0 = delete alias

  public record MergeCartRequest(@NotNull List<@Valid MergeItem> items) {}
  public record MergeItem(@NotBlank String productId, @Min(1) int quantity) {}
}
