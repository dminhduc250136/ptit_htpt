package com.ptit.htpt.orderservice.repository;

import com.ptit.htpt.orderservice.domain.CartItemEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItemEntity, String> {

  Optional<CartItemEntity> findByCartIdAndProductId(@Param("cartId") String cartId,
                                                    @Param("productId") String productId);

  /**
   * Idempotent ADD: INSERT new line, hoac neu (cart_id, product_id) da ton tai
   * thi cong don quantity. Native SQL atomic single-statement -> race-safe.
   *
   * D-05 (CONTEXT): POST /cart/items semantics = ADD (cong don). Phase 24: DB tach
   * rieng -> bo schema prefix, dung table name truc tiep.
   *
   * Caller PHAI flush + clear EntityManager sau khi upsert neu sau do query lai
   * cart vi native bypass JPA persistence context cache.
   */
  @Modifying
  @Query(value = """
      INSERT INTO cart_items (id, cart_id, product_id, quantity, created_at, updated_at)
      VALUES (:id, :cartId, :productId, :quantity, now(), now())
      ON CONFLICT (cart_id, product_id)
      DO UPDATE SET
        quantity = cart_items.quantity + EXCLUDED.quantity,
        updated_at = now()
      """, nativeQuery = true)
  int upsertAddQuantity(@Param("id") String id,
                        @Param("cartId") String cartId,
                        @Param("productId") String productId,
                        @Param("quantity") int quantity);
}
