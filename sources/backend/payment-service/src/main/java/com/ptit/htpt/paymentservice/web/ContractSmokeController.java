package com.ptit.htpt.paymentservice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__contract")
public class ContractSmokeController {

  @GetMapping("/ping")
  public Map<String, String> ping() {
    return Map.of("service", "payment-service");
  }

  public record ValidateRequest(@NotBlank String name) {}

  @PostMapping("/validate")
  public Map<String, Object> validate(@Valid @RequestBody ValidateRequest request) {
    return Map.of("valid", true);
  }
}

