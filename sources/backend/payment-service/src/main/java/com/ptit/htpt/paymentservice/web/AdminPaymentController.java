package com.ptit.htpt.paymentservice.web;

import com.ptit.htpt.paymentservice.api.ApiResponse;
import com.ptit.htpt.paymentservice.service.PaymentCrudService;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.SessionStatusRequest;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.SessionUpsertRequest;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.TransactionStatusRequest;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.TransactionUpsertRequest;
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
@RequestMapping("/admin/payments")
public class AdminPaymentController {
  private final PaymentCrudService paymentCrudService;

  public AdminPaymentController(PaymentCrudService paymentCrudService) {
    this.paymentCrudService = paymentCrudService;
  }

  @GetMapping("/sessions")
  public ApiResponse<Map<String, Object>> listSessions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(200, "Admin payment sessions listed", paymentCrudService.listSessions(page, size, sort, includeDeleted));
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createSession(@Valid @RequestBody SessionUpsertRequest request) {
    return ApiResponse.of(201, "Admin payment session created", paymentCrudService.createSession(request));
  }

  @PutMapping("/sessions/{id}")
  public ApiResponse<Object> updateSession(@PathVariable String id, @Valid @RequestBody SessionUpsertRequest request) {
    return ApiResponse.of(200, "Admin payment session updated", paymentCrudService.updateSession(id, request));
  }

  @PatchMapping("/sessions/{id}/status")
  public ApiResponse<Object> updateSessionStatus(
      @PathVariable String id,
      @Valid @RequestBody SessionStatusRequest request
  ) {
    return ApiResponse.of(200, "Admin payment session status updated", paymentCrudService.updateSessionStatus(id, request));
  }

  @DeleteMapping("/sessions/{id}")
  public ApiResponse<Map<String, Object>> deleteSession(@PathVariable String id) {
    paymentCrudService.deleteSession(id);
    return ApiResponse.of(200, "Admin payment session soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/transactions")
  public ApiResponse<Map<String, Object>> listTransactions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort,
      @RequestParam(defaultValue = "true") boolean includeDeleted
  ) {
    return ApiResponse.of(
        200,
        "Admin payment transactions listed",
        paymentCrudService.listTransactions(page, size, sort, includeDeleted)
    );
  }

  @PostMapping("/transactions")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createTransaction(@Valid @RequestBody TransactionUpsertRequest request) {
    return ApiResponse.of(201, "Admin payment transaction created", paymentCrudService.createTransaction(request));
  }

  @PutMapping("/transactions/{id}")
  public ApiResponse<Object> updateTransaction(
      @PathVariable String id,
      @Valid @RequestBody TransactionUpsertRequest request
  ) {
    return ApiResponse.of(200, "Admin payment transaction updated", paymentCrudService.updateTransaction(id, request));
  }

  @PatchMapping("/transactions/{id}/status")
  public ApiResponse<Object> updateTransactionStatus(
      @PathVariable String id,
      @Valid @RequestBody TransactionStatusRequest request
  ) {
    return ApiResponse.of(200, "Admin payment transaction status updated", paymentCrudService.updateTransactionStatus(id, request));
  }

  @DeleteMapping("/transactions/{id}")
  public ApiResponse<Map<String, Object>> deleteTransaction(@PathVariable String id) {
    paymentCrudService.deleteTransaction(id);
    return ApiResponse.of(200, "Admin payment transaction soft deleted", Map.of("id", id, "deleted", true));
  }
}
