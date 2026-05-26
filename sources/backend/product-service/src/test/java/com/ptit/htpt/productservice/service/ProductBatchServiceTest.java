package com.ptit.htpt.productservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

/**
 * Phase 19 / Plan 03 — unit tests cho ProductBatchService.findByIds.
 *
 * <p>Coverage:
 * 6. happy: 5 SP, request [id1,id3,id5] → 3 ProductSummary mapped đúng
 * 7. empty input → empty list, KHÔNG gọi DB
 * 8. missing IDs → chỉ return existing
 */
@ExtendWith(MockitoExtension.class)
class ProductBatchServiceTest {

  @Mock
  ProductRepository productRepo;

  @InjectMocks
  ProductBatchService service;

  @Test
  void findByIds_happyPath_mapsAllFields() throws Exception {
    ProductEntity p1 = stub("id-1", "Phone A", "BrandA", "thumb1.webp");
    ProductEntity p3 = stub("id-3", "Phone C", "BrandB", "thumb3.webp");
    ProductEntity p5 = stub("id-5", "Phone E", null, null);
    when(productRepo.findAllById(List.of("id-1", "id-3", "id-5")))
        .thenReturn(List.of(p1, p3, p5));

    List<ProductBatchService.ProductSummary> result =
        service.findByIds(List.of("id-1", "id-3", "id-5"));
    assertThat(result).hasSize(3);
    assertThat(result.get(0).id()).isEqualTo("id-1");
    assertThat(result.get(0).name()).isEqualTo("Phone A");
    assertThat(result.get(0).brand()).isEqualTo("BrandA");
    assertThat(result.get(0).thumbnailUrl()).isEqualTo("thumb1.webp");
    assertThat(result.get(2).brand()).isNull();
    assertThat(result.get(2).thumbnailUrl()).isNull();
  }

  @Test
  void findByIds_emptyInput_skipsRepoQuery() {
    List<ProductBatchService.ProductSummary> result = service.findByIds(List.of());
    assertThat(result).isEmpty();
    verify(productRepo, never()).findAllById(anyList());
  }

  @Test
  void findByIds_nullInput_returnsEmpty() {
    assertThat(service.findByIds(null)).isEmpty();
    verify(productRepo, never()).findAllById(anyList());
  }

  @Test
  void findByIds_missingIds_returnsOnlyExisting() throws Exception {
    ProductEntity p1 = stub("id-1", "Phone A", "BrandA", "thumb1.webp");
    when(productRepo.findAllById(List.of("id-1", "fake-uuid")))
        .thenReturn(List.of(p1));

    List<ProductBatchService.ProductSummary> result =
        service.findByIds(List.of("id-1", "fake-uuid"));
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("id-1");
  }

  private ProductEntity stub(String id, String name, String brand, String thumb) throws Exception {
    ProductEntity p = ProductEntity.create(name, "slug-" + id, "cat-mock",
        new BigDecimal("1.00"), "ACTIVE", brand, thumb, null, null);
    setField(p, "id", id);
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
