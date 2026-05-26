package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 / Plan 03 (D-03 dependency). Batch enrichment service cho cross-svc.
 *
 * <p>Dùng bởi POST /admin/products/batch endpoint — order-svc ProductBatchClient
 * (Plan 19-01) gọi để enrich top-products chart với name/brand/thumbnailUrl.
 *
 * <p>Wire format `ProductSummary(id, name, brand, thumbnailUrl)` phải khớp Plan 01
 * `ProductBatchClient.ProductSummary` deserialization (cùng field names).
 *
 * <p>Soft-delete: JpaRepository.findAllById tự honor @SQLRestriction("deleted=false")
 * trên ProductEntity → loại records bị soft-delete khỏi kết quả.
 *
 * <p>Empty input KHÔNG query DB (early return) — defensive performance.
 */
@Service
public class ProductBatchService {

  private final ProductRepository productRepo;

  public ProductBatchService(ProductRepository productRepo) {
    this.productRepo = productRepo;
  }

  @Transactional(readOnly = true)
  public List<ProductSummary> findByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) return List.of();
    return productRepo.findAllById(ids).stream()
        .map(p -> new ProductSummary(p.id(), p.name(), p.brand(), p.thumbnailUrl()))
        .toList();
  }

  /** Wire format match Plan 19-01 ProductBatchClient.ProductSummary. */
  public record ProductSummary(String id, String name, String brand, String thumbnailUrl) {}
}
