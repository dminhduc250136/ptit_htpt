package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ProductCrudService;
import com.ptit.htpt.productservice.service.ProductCrudService.CategoryUpsertRequest;
import com.ptit.htpt.productservice.service.ProductCrudService.ProductResponse;
import com.ptit.htpt.productservice.service.ProductCrudService.ProductUpsertRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {
  private final ProductCrudService productCrudService;

  public ProductController(ProductCrudService productCrudService) {
    this.productCrudService = productCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listProducts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(required = false) String keyword,                  // D-02
      @RequestParam(required = false) List<String> brands,              // Phase 14 D-07/D-08: repeatable param
      @RequestParam(required = false) BigDecimal priceMin,              // Phase 14 D-08
      @RequestParam(required = false) BigDecimal priceMax               // Phase 14 D-08
  ) {
    return ApiResponse.of(200, "Products listed",
        productCrudService.listProducts(page, size, sort, false, keyword, brands, priceMin, priceMax));
  }

  @GetMapping("/{id}")
  public ApiResponse<ProductResponse> getProduct(@PathVariable String id) {
    return ApiResponse.of(200, "Product loaded",
        productCrudService.toResponse(productCrudService.getProduct(id, false)));
  }

  @GetMapping("/slug/{slug}")
  public ApiResponse<ProductResponse> getProductBySlug(@PathVariable String slug) {
    return ApiResponse.of(200, "Product loaded",
        productCrudService.toResponse(productCrudService.getProductBySlug(slug)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
    return ApiResponse.of(201, "Product created", productCrudService.createProduct(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateProduct(@PathVariable String id, @Valid @RequestBody ProductUpsertRequest request) {
    return ApiResponse.of(200, "Product updated", productCrudService.updateProduct(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteProduct(@PathVariable String id) {
    productCrudService.deleteProduct(id);
    return ApiResponse.of(200, "Product soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/categories")
  public ApiResponse<Map<String, Object>> listCategories(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "Categories listed", productCrudService.listCategories(page, size, sort, false));
  }

  /** Phase 14 / Plan 01 (D-03): danh sách thương hiệu DISTINCT cho FE FilterSidebar. */
  @GetMapping("/brands")
  public ApiResponse<List<String>> listBrands() {
    return ApiResponse.of(200, "Brands listed", productCrudService.listBrands());
  }

  @GetMapping("/categories/{id}")
  public ApiResponse<Object> getCategory(@PathVariable String id) {
    return ApiResponse.of(200, "Category loaded", productCrudService.getCategory(id, false));
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
    return ApiResponse.of(201, "Category created", productCrudService.createCategory(request));
  }

  @PutMapping("/categories/{id}")
  public ApiResponse<Object> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryUpsertRequest request) {
    return ApiResponse.of(200, "Category updated", productCrudService.updateCategory(id, request));
  }

  @DeleteMapping("/categories/{id}")
  public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable String id) {
    productCrudService.deleteCategory(id);
    return ApiResponse.of(200, "Category soft deleted", Map.of("id", id, "deleted", true));
  }
}
