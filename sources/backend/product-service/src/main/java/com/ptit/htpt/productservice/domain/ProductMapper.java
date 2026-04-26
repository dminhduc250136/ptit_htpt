package com.ptit.htpt.productservice.domain;

/** Entity ↔ DTO transform cho ProductEntity. */
public final class ProductMapper {
  private ProductMapper() {}

  public static ProductDto toDto(ProductEntity e) {
    return new ProductDto(
        e.id(), e.name(), e.slug(), e.categoryId(),
        e.price(), e.status(), e.createdAt(), e.updatedAt()
    );
  }
}
