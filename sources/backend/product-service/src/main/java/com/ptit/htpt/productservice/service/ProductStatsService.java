package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 9 / Plan 09-02 (UI-02): Admin stats service cho product-service.
 * Cung cấp số liệu thật cho admin dashboard KPI cards.
 */
@Service
public class ProductStatsService {

  private final ProductRepository productRepo;

  public ProductStatsService(ProductRepository productRepo) {
    this.productRepo = productRepo;
  }

  @Transactional(readOnly = true)
  public long totalProducts() {
    return productRepo.count();
  }
}
