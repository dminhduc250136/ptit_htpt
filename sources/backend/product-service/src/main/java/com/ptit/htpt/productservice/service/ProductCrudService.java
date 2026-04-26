package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductCrudService {
  private final ProductRepository productRepo;
  private final CategoryRepository categoryRepo;

  public ProductCrudService(ProductRepository productRepo, CategoryRepository categoryRepo) {
    this.productRepo = productRepo;
    this.categoryRepo = categoryRepo;
  }

  public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted) {
    // Note: @SQLRestriction("deleted = false") filters soft-deleted at SQL layer.
    // includeDeleted=true path không trả về deleted records nữa (acceptable Phase 5 — admin
    // soft-delete recovery defer Phase 8). Filter giữ lại để keep API contract.
    List<ProductEntity> all = productRepo.findAll().stream()
        .filter(product -> includeDeleted || !product.deleted())
        .sorted(productComparator(sort))
        .toList();
    Map<String, Object> page0 = paginate(all, page, size);
    @SuppressWarnings("unchecked")
    List<ProductEntity> content = (List<ProductEntity>) page0.get("content");
    page0.put("content", content.stream().map(this::toResponse).toList());
    return page0;
  }

  public ProductEntity getProduct(String id, boolean includeDeleted) {
    ProductEntity product = productRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    if (!includeDeleted && product.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
    }
    return product;
  }

  public ProductEntity getProductBySlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
    }
    return productRepo.findBySlug(slug)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  public ProductEntity createProduct(ProductUpsertRequest request) {
    ProductEntity product = ProductEntity.create(
        request.name(),
        request.slug(),
        request.categoryId(),
        request.price(),
        request.status()
    );
    return productRepo.save(product);
  }

  public ProductEntity updateProduct(String id, ProductUpsertRequest request) {
    ProductEntity current = getProduct(id, true);
    current.update(
        request.name(),
        request.slug(),
        request.categoryId(),
        request.price(),
        request.status()
    );
    return productRepo.save(current);
  }

  public ProductEntity updateProductStatus(String id, ProductStatusRequest request) {
    ProductEntity current = getProduct(id, true);
    current.setStatus(request.status());
    return productRepo.save(current);
  }

  public void deleteProduct(String id) {
    ProductEntity current = getProduct(id, true);
    current.softDelete();
    productRepo.save(current);
  }

  public Map<String, Object> listCategories(int page, int size, String sort, boolean includeDeleted) {
    List<CategoryEntity> all = categoryRepo.findAll().stream()
        .filter(category -> includeDeleted || !category.deleted())
        .sorted(categoryComparator(sort))
        .toList();
    return paginate(all, page, size);
  }

  public CategoryEntity getCategory(String id, boolean includeDeleted) {
    CategoryEntity category = categoryRepo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    if (!includeDeleted && category.deleted()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
    }
    return category;
  }

  public CategoryEntity createCategory(CategoryUpsertRequest request) {
    CategoryEntity category = CategoryEntity.create(request.name(), request.slug());
    return categoryRepo.save(category);
  }

  public CategoryEntity updateCategory(String id, CategoryUpsertRequest request) {
    CategoryEntity current = getCategory(id, true);
    current.update(request.name(), request.slug());
    return categoryRepo.save(current);
  }

  public void deleteCategory(String id) {
    CategoryEntity current = getCategory(id, true);
    current.softDelete();
    categoryRepo.save(current);
  }

  /**
   * Map a ProductEntity to the rich ProductResponse the frontend consumes. Defaults
   * applied for fields not yet persisted on the entity. Category lookup uses
   * categoryRepo.findById; if the category has been deleted or is missing, emit a
   * placeholder CategoryRef with the raw categoryId so the FE can still render.
   */
  public ProductResponse toResponse(ProductEntity product) {
    CategoryRef categoryRef = categoryRepo.findById(product.categoryId())
        .map(c -> new CategoryRef(c.id(), c.name(), c.slug()))
        .orElseGet(() -> new CategoryRef(product.categoryId(), "—", product.categoryId()));

    return new ProductResponse(
        product.id(),
        product.name(),
        product.slug(),
        "",                                            // description default
        "",                                            // shortDescription default
        product.price(),
        null,                                          // originalPrice
        null,                                          // discount
        Collections.emptyList(),                       // images default
        "",                                            // thumbnailUrl default
        categoryRef,
        null,                                          // brand default
        BigDecimal.ZERO,                               // rating default
        0,                                             // reviewCount default
        0,                                             // stock default — read from inventory-service
        product.status(),
        Collections.emptyList(),                       // tags default
        product.createdAt(),
        product.updatedAt()
    );
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

  /** Phase 5: schema mới cho category — drop {@code parentId, status}, thêm {@code slug}. */
  public record CategoryUpsertRequest(@NotBlank String name, @NotBlank String slug) {}

  public record ProductResponse(
      String id,
      String name,
      String slug,
      String description,
      String shortDescription,
      BigDecimal price,
      BigDecimal originalPrice,
      Integer discount,
      List<String> images,
      String thumbnailUrl,
      CategoryRef category,
      String brand,
      BigDecimal rating,
      int reviewCount,
      int stock,
      String status,
      List<String> tags,
      Instant createdAt,
      Instant updatedAt
  ) {}

  public record CategoryRef(
      String id,
      String name,
      String slug
  ) {}
}
