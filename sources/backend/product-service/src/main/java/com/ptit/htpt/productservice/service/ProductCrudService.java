package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.InMemoryProductRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductCrudService {
  private final InMemoryProductRepository repository;

  public ProductCrudService(InMemoryProductRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted) {
    List<ProductEntity> all = repository.findAllProducts().stream()
        .filter(product -> includeDeleted || !product.deleted())
        .sorted(productComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public ProductEntity getProduct(String id, boolean includeDeleted) {
    ProductEntity product = repository.findProductById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    if (!includeDeleted && product.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
    }
    return product;
  }

  public ProductEntity createProduct(ProductUpsertRequest request) {
    ProductEntity product = ProductEntity.create(
        request.name(),
        request.slug(),
        request.categoryId(),
        request.price(),
        request.status()
    );
    return repository.saveProduct(product);
  }

  public ProductEntity updateProduct(String id, ProductUpsertRequest request) {
    ProductEntity current = getProduct(id, true);
    ProductEntity updated = current.update(
        request.name(),
        request.slug(),
        request.categoryId(),
        request.price(),
        request.status()
    );
    return repository.saveProduct(updated);
  }

  public ProductEntity updateProductStatus(String id, ProductStatusRequest request) {
    ProductEntity current = getProduct(id, true);
    return repository.saveProduct(current.setStatus(request.status()));
  }

  public void deleteProduct(String id) {
    ProductEntity current = getProduct(id, true);
    repository.saveProduct(current.softDelete());
  }

  public Map<String, Object> listCategories(int page, int size, String sort, boolean includeDeleted) {
    List<CategoryEntity> all = repository.findAllCategories().stream()
        .filter(category -> includeDeleted || !category.deleted())
        .sorted(categoryComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public CategoryEntity getCategory(String id, boolean includeDeleted) {
    CategoryEntity category = repository.findCategoryById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    if (!includeDeleted && category.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }
    return category;
  }

  public CategoryEntity createCategory(CategoryUpsertRequest request) {
    CategoryEntity category = CategoryEntity.create(request.name(), request.parentId(), request.status());
    return repository.saveCategory(category);
  }

  public CategoryEntity updateCategory(String id, CategoryUpsertRequest request) {
    CategoryEntity current = getCategory(id, true);
    CategoryEntity updated = current.update(request.name(), request.parentId(), request.status());
    return repository.saveCategory(updated);
  }

  public void deleteCategory(String id) {
    CategoryEntity current = getCategory(id, true);
    repository.saveCategory(current.softDelete());
  }

  private Comparator<ProductEntity> productComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(ProductEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<ProductEntity> comparator = sort.startsWith("name")
        ? Comparator.comparing(ProductEntity::name, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(ProductEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private Comparator<CategoryEntity> categoryComparator(String sort) {
    if (sort == null || sort.isBlank()) {
      return Comparator.comparing(CategoryEntity::updatedAt).reversed();
    }
    boolean desc = sort.endsWith(",desc");
    Comparator<CategoryEntity> comparator = sort.startsWith("name")
        ? Comparator.comparing(CategoryEntity::name, String.CASE_INSENSITIVE_ORDER)
        : Comparator.comparing(CategoryEntity::id);
    return desc ? comparator.reversed() : comparator;
  }

  private <T> Map<String, Object> paginate(List<T> source, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, 100);
    int totalElements = source.size();
    int from = Math.min(safePage * safeSize, totalElements);
    int to = Math.min(from + safeSize, totalElements);
    List<T> content = new ArrayList<>(source.subList(from, to));
    int totalPages = totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / safeSize);

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("totalElements", totalElements);
    result.put("totalPages", totalPages);
    result.put("currentPage", safePage);
    result.put("pageSize", safeSize);
    result.put("isFirst", safePage <= 0);
    result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
    return result;
  }

  public record ProductUpsertRequest(
      @NotBlank String name,
      @NotBlank String slug,
      @NotBlank String categoryId,
      @DecimalMin("0.0") BigDecimal price,
      @NotBlank String status
  ) {}

  public record ProductStatusRequest(@NotBlank String status) {}

  public record CategoryUpsertRequest(@NotBlank String name, String parentId, @NotBlank String status) {}
}
