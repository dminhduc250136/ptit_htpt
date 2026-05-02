package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  Optional<ProductEntity> findBySlug(String slug);

  /**
   * Phase 14 / Plan 01 (D-06, D-07, D-08): list products với optional filters.
   * keyword=null → bỏ qua keyword. brands=null/empty → bỏ qua brand filter.
   * priceMin / priceMax = null → bỏ qua price bound tương ứng.
   *
   * <p>Sort/order do Pageable quyết định — KHÔNG hardcode ORDER BY trong JPQL.
   * @SQLRestriction("deleted = false") trên ProductEntity tự loại deleted records.
   *
   * <p>cast(:param as type) IS NULL pattern (analog OrderRepository.findByUserIdWithFilters)
   * để Hibernate bind nullable param đúng kiểu khi user truyền null.
   */
  @Query("SELECT p FROM ProductEntity p WHERE "
      + "(cast(:keyword as string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))) "
      + "AND (:brands IS NULL OR p.brand IN :brands) "
      + "AND (cast(:priceMin as big_decimal) IS NULL OR p.price >= :priceMin) "
      + "AND (cast(:priceMax as big_decimal) IS NULL OR p.price <= :priceMax)")
  Page<ProductEntity> findWithFilters(
      @Param("keyword") String keyword,
      @Param("brands") List<String> brands,
      @Param("priceMin") BigDecimal priceMin,
      @Param("priceMax") BigDecimal priceMax,
      Pageable pageable);

  /**
   * Phase 14 / Plan 01 (D-03): trả danh sách brand DISTINCT alphabetical, không null/empty.
   * Dùng cho FE FilterSidebar fetch danh sách thương hiệu.
   */
  @Query("SELECT DISTINCT p.brand FROM ProductEntity p "
      + "WHERE p.brand IS NOT NULL AND p.brand <> '' ORDER BY p.brand ASC")
  List<String> findDistinctBrands();
}
