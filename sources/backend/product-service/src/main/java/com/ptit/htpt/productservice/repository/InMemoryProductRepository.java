package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProductRepository {
  private final Map<String, ProductEntity> products = new LinkedHashMap<>();
  private final Map<String, CategoryEntity> categories = new LinkedHashMap<>();

  public Collection<ProductEntity> findAllProducts() {
    return products.values();
  }

  public Optional<ProductEntity> findProductById(String id) {
    return Optional.ofNullable(products.get(id));
  }

  public ProductEntity saveProduct(ProductEntity product) {
    products.put(product.id(), product);
    return product;
  }

  public Collection<CategoryEntity> findAllCategories() {
    return categories.values();
  }

  public Optional<CategoryEntity> findCategoryById(String id) {
    return Optional.ofNullable(categories.get(id));
  }

  public CategoryEntity saveCategory(CategoryEntity category) {
    categories.put(category.id(), category);
    return category;
  }
}
