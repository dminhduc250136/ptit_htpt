package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.service.OrderCrudService;
import com.ptit.htpt.orderservice.service.OrderCrudService.CreateOrderCommand;
import com.ptit.htpt.orderservice.service.OrderCrudService.OrderUpsertRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/orders")
public class OrderController {
  private final OrderCrudService orderCrudService;

  public OrderController(OrderCrudService orderCrudService) {
    this.orderCrudService = orderCrudService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> listOrders(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      // Phase 11 / ACCT-02 (D-10, D-11, D-12): filter params
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String q,
      @RequestHeader(value = "X-User-Id", required = false) String userId
  ) {
    // BUG-FIX (orders-cross-user-leak): trước đây thiếu X-User-Id → fallback listOrders
    // trả TẤT CẢ orders → mọi tài khoản thấy chung data. Endpoint này là user-facing
    // (/orders), admin có /admin/orders riêng. Bắt buộc X-User-Id.
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id session header");
    }
    OrderCrudService.ListMyOrdersQuery query = new OrderCrudService.ListMyOrdersQuery(
        userId, status, from, to, q, page, size, sort
    );
    return ApiResponse.of(200, "Orders listed", orderCrudService.listMyOrders(query));
  }

  @GetMapping("/{id}")
  public ApiResponse<Object> getOrder(
      @PathVariable String id,
      @RequestHeader(value = "X-User-Id", required = false) String userId
  ) {
    // BUG-FIX (orders-cross-user-leak): IDOR — trước đây không check ownership.
    // Bất kỳ user nào biết id đều load được order của user khác.
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id session header");
    }
    return ApiResponse.of(200, "Order loaded", orderCrudService.getOrderForUser(id, userId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createOrder(
      @Valid @RequestBody CreateOrderCommand command,
      @RequestHeader(value = "X-User-Id", required = false) String userId
  ) {
    return ApiResponse.of(201, "Order created",
        orderCrudService.createOrderFromCommand(userId, command));
  }

  @PutMapping("/{id}")
  public ApiResponse<Object> updateOrder(@PathVariable String id, @Valid @RequestBody OrderUpsertRequest request) {
    return ApiResponse.of(200, "Order updated", orderCrudService.updateOrder(id, request));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> deleteOrder(@PathVariable String id) {
    orderCrudService.deleteOrder(id);
    return ApiResponse.of(200, "Order soft deleted", Map.of("id", id, "deleted", true));
  }
}
