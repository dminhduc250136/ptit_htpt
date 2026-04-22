package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderCrudService;
import com.ptit.htpt.orderservice.service.OrderCrudService.OrderStateRequest;
import com.ptit.htpt.orderservice.service.OrderCrudService.OrderUpsertRequest;
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
@RequestMapping("/admin/orders")
public class AdminOrderController {
  private final OrderCrudService orderCrudService;

  public AdminOrderController(OrderCrudService orderCrudService) {
    this.orderCrudService = orderCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listOrders(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(200, "Admin orders listed", orderCrudService.listOrders(page, size, sort, includeDeleted));
  }

  @GetMapping("/{id}")
  public ApiResponse<Object> getOrder(@PathVariable String id) {
    return ApiResponse.of(200, "Admin order loaded", orderCrudService.getOrder(id, true));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createOrder(@Valid @RequestBody OrderUpsertRequest request) {
    return ApiResponse.of(201, "Admin order created", orderCrudService.createOrder(request));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateOrder(@PathVariable String id, @Valid @RequestBody OrderUpsertRequest request) {
    return ApiResponse.of(200, "Admin order updated", orderCrudService.updateOrder(id, request));
  }

  @PatchMapping("/{id}/state")
  public ApiResponse<Object> updateState(@PathVariable String id, @Valid @RequestBody OrderStateRequest request) {
    return ApiResponse.of(200, "Admin order state updated", orderCrudService.updateOrderState(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteOrder(@PathVariable String id) {
    orderCrudService.deleteOrder(id);
    return ApiResponse.of(200, "Admin order soft deleted", Map.of("id", id, "deleted", true));
  }
}
