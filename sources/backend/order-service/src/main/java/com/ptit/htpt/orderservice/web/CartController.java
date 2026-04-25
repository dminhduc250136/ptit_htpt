package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderCrudService;
import com.ptit.htpt.orderservice.service.OrderCrudService.CartUpsertRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/cart")
public class CartController {
  private final OrderCrudService orderCrudService;

  public CartController(OrderCrudService orderCrudService) {
    this.orderCrudService = orderCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listCarts(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "Cart items listed", orderCrudService.listCarts(page, size, sort, false));
  }

  @GetMapping("/{id}")
  public ApiResponse<Object> getCart(@PathVariable String id) {
    return ApiResponse.of(200, "Cart item loaded", orderCrudService.getCart(id, false));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createCart(@Valid @RequestBody CartUpsertRequest request) {
    return ApiResponse.of(201, "Cart item created", orderCrudService.createCart(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateCart(@PathVariable String id, @Valid @RequestBody CartUpsertRequest request) {
    return ApiResponse.of(200, "Cart item updated", orderCrudService.updateCart(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteCart(@PathVariable String id) {
    orderCrudService.deleteCart(id);
    return ApiResponse.of(200, "Cart item soft deleted", Map.of("id", id, "deleted", true));
  }
}
