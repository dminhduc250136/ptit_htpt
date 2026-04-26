package com.ptit.htpt.productservice.domain;

public final class CategoryMapper {
  private CategoryMapper() {}

  public static CategoryDto toDto(CategoryEntity e) {
    return new CategoryDto(e.id(), e.name(), e.slug(), e.createdAt(), e.updatedAt());
  }
}
