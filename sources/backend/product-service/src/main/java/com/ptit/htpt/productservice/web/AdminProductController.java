package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ProductCrudService;
import com.ptit.htpt.productservice.service.ProductCrudService.CategoryUpsertRequest;
import com.ptit.htpt.productservice.service.ProductCrudService.ProductStatusRequest;
import com.ptit.htpt.productservice.service.ProductCrudService.ProductUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {
  private final ProductCrudService productCrudService;

  public AdminProductController(ProductCrudService productCrudService) {
    this.productCrudService = productCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listProducts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(200, "Admin products listed", productCrudService.listProducts(page, size, sort, includeDeleted));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
    return ApiResponse.of(201, "Admin product created", productCrudService.createProduct(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateProduct(@PathVariable String id, @Valid @RequestBody ProductUpsertRequest request) {
    return ApiResponse.of(200, "Admin product updated", productCrudService.updateProduct(id, request));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Object> updateStatus(@PathVariable String id, @Valid @RequestBody ProductStatusRequest request) {
    return ApiResponse.of(200, "Admin product status updated", productCrudService.updateProductStatus(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteProduct(@PathVariable String id) {
    productCrudService.deleteProduct(id);
    return ApiResponse.of(200, "Admin product soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/categories")
  public ApiResponse<Map<String, Object>> listCategories(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(
        200,
        "Admin categories listed",
        productCrudService.listCategories(page, size, sort, includeDeleted)
    );
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createCategory(@Valid @RequestBody CategoryUpsertRequest request) {
    return ApiResponse.of(201, "Admin category created", productCrudService.createCategory(request));
  }

  @PutMapping("/categories/{id}")
  public ApiResponse<Object> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryUpsertRequest request) {
    return ApiResponse.of(200, "Admin category updated", productCrudService.updateCategory(id, request));
  }

  @DeleteMapping("/categories/{id}")
  public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable String id) {
    productCrudService.deleteCategory(id);
    return ApiResponse.of(200, "Admin category soft deleted", Map.of("id", id, "deleted", true));
  }
}
