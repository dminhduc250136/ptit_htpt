package com.ptit.htpt.paymentservice.web;

import com.ptit.htpt.paymentservice.api.ApiResponse;
import com.ptit.htpt.paymentservice.service.PaymentCrudService;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.SessionUpsertRequest;
import com.ptit.htpt.paymentservice.service.PaymentCrudService.TransactionUpsertRequest;
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
@RequestMapping("/payments")
public class PaymentController {
  private final PaymentCrudService paymentCrudService;

  public PaymentController(PaymentCrudService paymentCrudService) {
    this.paymentCrudService = paymentCrudService;
  }

  @GetMapping("/sessions")
  public ApiResponse<Map<String, Object>> listSessions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(200, "Payment sessions listed", paymentCrudService.listSessions(page, size, sort, false));
  }

  @GetMapping("/sessions/{id}")
  public ApiResponse<Object> getSession(@PathVariable String id) {
    return ApiResponse.of(200, "Payment session loaded", paymentCrudService.getSession(id, false));
  }

  @PostMapping("/sessions")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createSession(@Valid @RequestBody SessionUpsertRequest request) {
    return ApiResponse.of(201, "Payment session created", paymentCrudService.createSession(request));
  }

  @PutMapping("/sessions/{id}")
  public ApiResponse<Object> updateSession(@PathVariable String id, @Valid @RequestBody SessionUpsertRequest request) {
    return ApiResponse.of(200, "Payment session updated", paymentCrudService.updateSession(id, request));
  }

  @DeleteMapping("/sessions/{id}")
  public ApiResponse<Map<String, Object>> deleteSession(@PathVariable String id) {
    paymentCrudService.deleteSession(id);
    return ApiResponse.of(200, "Payment session soft deleted", Map.of("id", id, "deleted", true));
  }

  @GetMapping("/transactions")
  public ApiResponse<Map<String, Object>> listTransactions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "updatedAt,desc") String sort
  ) {
    return ApiResponse.of(
        200,
        "Payment transactions listed",
        paymentCrudService.listTransactions(page, size, sort, false)
    );
  }

  @GetMapping("/transactions/{id}")
  public ApiResponse<Object> getTransaction(@PathVariable String id) {
    return ApiResponse.of(200, "Payment transaction loaded", paymentCrudService.getTransaction(id, false));
  }

  @PostMapping("/transactions")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createTransaction(@Valid @RequestBody TransactionUpsertRequest request) {
    return ApiResponse.of(201, "Payment transaction created", paymentCrudService.createTransaction(request));
  }

  @PutMapping("/transactions/{id}")
  public ApiResponse<Object> updateTransaction(
      @PathVariable String id,
      @Valid @RequestBody TransactionUpsertRequest request
  ) {
    return ApiResponse.of(200, "Payment transaction updated", paymentCrudService.updateTransaction(id, request));
  }

  @DeleteMapping("/transactions/{id}")
  public ApiResponse<Map<String, Object>> deleteTransaction(@PathVariable String id) {
    paymentCrudService.deleteTransaction(id);
    return ApiResponse.of(200, "Payment transaction soft deleted", Map.of("id", id, "deleted", true));
  }
}
