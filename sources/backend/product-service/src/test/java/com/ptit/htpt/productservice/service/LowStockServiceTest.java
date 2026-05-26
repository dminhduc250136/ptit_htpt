package com.ptit.htpt.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.ProductRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Phase 19 / Plan 03 — unit tests cho LowStockService.list (Mockito mock repo).
 *
 * <p>Coverage:
 * 4. empty results → empty list
 * 5. sort ASC mapping → record fields đúng + order giữ nguyên
 */
@ExtendWith(MockitoExtension.class)
class LowStockServiceTest {

  @Mock
  ProductRepository productRepo;

  @InjectMocks
  LowStockService service;

  @Test
  void list_returnsEmpty_whenNoLowStock() {
    when(productRepo.findLowStock(eq(10), any(Pageable.class))).thenReturn(List.of());
    assertThat(service.list()).isEmpty();
  }

  @Test
  void list_mapsAndPreservesAscOrder() throws Exception {
    ProductEntity p2 = stub("id-2", "Name 2", "BrandA", "thumb2.webp", 2);
    ProductEntity p5 = stub("id-5", "Name 5", "BrandB", "thumb5.webp", 5);
    ProductEntity p8 = stub("id-8", "Name 8", null, null, 8);
    when(productRepo.findLowStock(eq(10), any(Pageable.class)))
        .thenReturn(List.of(p2, p5, p8));

    List<LowStockService.LowStockItem> result = service.list();
    assertThat(result).hasSize(3);
    assertThat(result.get(0).id()).isEqualTo("id-2");
    assertThat(result.get(0).stock()).isEqualTo(2);
    assertThat(result.get(0).brand()).isEqualTo("BrandA");
    assertThat(result.get(2).brand()).isNull();
    assertThat(result.get(2).thumbnailUrl()).isNull();
    assertThat(result.stream().map(LowStockService.LowStockItem::stock).toList())
        .containsExactly(2, 5, 8);
  }

  /** Build ProductEntity stub bằng reflection (ProductEntity.create() yêu cầu category FK). */
  private ProductEntity stub(String id, String name, String brand, String thumb, int stock) throws Exception {
    ProductEntity p = ProductEntity.create(name, "slug-" + id, "cat-mock",
        new BigDecimal("1.00"), "ACTIVE", brand, thumb, null, null);
    setField(p, "id", id);
    setField(p, "stock", stock);
    setField(p, "createdAt", Instant.now());
    setField(p, "updatedAt", Instant.now());
    return p;
  }

  private void setField(Object target, String name, Object value) throws Exception {
    Field f = ProductEntity.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(target, value);
  }
}
