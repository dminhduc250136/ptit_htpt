package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.repository.ProductRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 19 / Plan 03 (ADMIN-05). Low-stock alert service cho admin dashboard.
 *
 * <p>D-08: LOW_STOCK_THRESHOLD = 10 hardcoded constant (KHÔNG env var giai đoạn này —
 * requirement nói "threshold configurable trong code" = constant để dễ đổi sau).
 *
 * <p>D-09: Cap 50 rows tránh response quá lớn nếu seed catalog có nhiều SP low-stock.
 * Sort by stock ASC để admin thấy SP cấp thiết nhất trước. Empty list trả [] (FE
 * render placeholder "Tất cả sản phẩm đủ hàng ✓").
 *
 * <p>D-10: Response shape `{id, name, brand, thumbnailUrl, stock}` — minimal payload
 * đủ cho FE render row + nút "Sửa" điều hướng admin/products.
 */
@Service
public class LowStockService {

  public static final int LOW_STOCK_THRESHOLD = 10; // D-08
  public static final int CAP = 50;                 // D-09

  private final ProductRepository productRepo;

  public LowStockService(ProductRepository productRepo) {
    this.productRepo = productRepo;
  }

  @Transactional(readOnly = true)
  public List<LowStockItem> list() {
    return productRepo.findLowStock(LOW_STOCK_THRESHOLD, PageRequest.of(0, CAP)).stream()
        .map(p -> new LowStockItem(p.id(), p.name(), p.brand(), p.thumbnailUrl(), p.stock()))
        .toList();
  }

  /** D-10: minimal projection cho FE LowStockSection. */
  public record LowStockItem(String id, String name, String brand, String thumbnailUrl, int stock) {}
}
