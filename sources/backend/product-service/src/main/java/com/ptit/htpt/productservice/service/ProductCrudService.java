package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.CategoryEntity;
import com.ptit.htpt.productservice.domain.CategoryMapper;
import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.CategoryRepository;
import com.ptit.htpt.productservice.repository.ProductRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    return listProducts(page, size, sort, includeDeleted, null, null, null, null);
  }

  public Map<String, Object> listProducts(int page, int size, String sort,
                                          boolean includeDeleted, String keyword) {
    return listProducts(page, size, sort, includeDeleted, keyword, null, null, null);
  }

  /**
   * Phase 14 / Plan 01 (D-06, D-07, D-08): list products với JPQL filters.
   *
   * <p>{@code includeDeleted} giữ trong chữ ký để KHÔNG break callers cũ — không còn ý nghĩa
   * vì @SQLRestriction filter ở SQL layer (đồng bộ comment cũ Phase 5/8).
   *
   * <p>Normalize: empty/blank keyword → null, empty brands list → null để JPQL `IS NULL` clause skip.
   */
  public Map<String, Object> listProducts(int page, int size, String sort,
                                          boolean includeDeleted, String keyword,
                                          List<String> brands, BigDecimal priceMin,
                                          BigDecimal priceMax) {
    String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword;
    List<String> normalizedBrands = (brands == null || brands.isEmpty()) ? null : brands;

    Pageable pageable = PageRequest.of(Math.max(page, 0),
        size <= 0 ? 20 : Math.min(size, 100), parseSort(sort));

    Page<ProductEntity> resultPage = productRepo.findWithFilters(
        normalizedKeyword, normalizedBrands, priceMin, priceMax, pageable);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("content", resultPage.getContent().stream().map(this::toResponse).toList());
    response.put("totalElements", resultPage.getTotalElements());
    response.put("totalPages", resultPage.getTotalPages());
    response.put("currentPage", resultPage.getNumber());
    response.put("pageSize", resultPage.getSize());
    response.put("isFirst", resultPage.isFirst());
    response.put("isLast", resultPage.isLast());
    return response;
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
        request.status(),
        request.brand(),
        request.thumbnailUrl(),
        request.shortDescription(),
        request.originalPrice()
    );
    product.setStock(request.stock());
    return productRepo.save(product);
  }

  public ProductEntity updateProduct(String id, ProductUpsertRequest request) {
    ProductEntity current = getProduct(id, true);
    current.update(
        request.name(),
        request.slug(),
        request.categoryId(),
        request.price(),
        request.status(),
        request.brand(),
        request.thumbnailUrl(),
        request.shortDescription(),
        request.originalPrice(),
        request.stock()
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
    Map<String, Object> page0 = paginate(all, page, size);
    @SuppressWarnings("unchecked")
    List<CategoryEntity> content = (List<CategoryEntity>) page0.get("content");
    page0.put("content", content.stream().map(CategoryMapper::toDto).toList());
    return page0;
  }

  /**
   * Phase 14 / Plan 01 (D-03): trả danh sách thương hiệu DISTINCT alphabetical
   * cho FE FilterSidebar. Delegate trực tiếp sang repository.
   */
  public List<String> listBrands() {
    return productRepo.findDistinctBrands();
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
        product.shortDescription() != null ? product.shortDescription() : "",
        product.price(),
        product.originalPrice(),
        null,                                          // discount
        Collections.emptyList(),                       // images default
        product.thumbnailUrl() != null ? product.thumbnailUrl() : "",
        categoryRef,
        product.brand(),
        product.avgRating() != null ? product.avgRating() : BigDecimal.ZERO,
        product.reviewCount(),
        product.stock(),                               // D-02: đọc từ ProductEntity.stock (Phase 8 PERSIST-01)
        product.status(),
        Collections.emptyList(),                       // tags default
        product.createdAt(),
        product.updatedAt()
    );
  }

  /**
   * Phase 14 / Plan 01: parse sort param "field,dir" → Spring Sort (dùng cho Pageable).
   * Default updatedAt DESC nếu sort null/blank.
   */
  private static Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return Sort.by(Sort.Direction.DESC, "updatedAt");
    }
    String[] parts = sort.split(",");
    String field = parts[0].trim();
    Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
        ? Sort.Direction.DESC : Sort.Direction.ASC;
    return Sort.by(dir, field);
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
      @NotBlank String status,
      String brand,               // nullable — D-03
      String thumbnailUrl,        // nullable — D-03
      String shortDescription,    // nullable — D-03
      BigDecimal originalPrice,   // nullable — D-03
      @Min(0) int stock           // D-01: stock field cho admin set/update (Phase 8)
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
