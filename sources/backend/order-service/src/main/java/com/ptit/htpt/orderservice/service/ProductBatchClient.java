package com.ptit.htpt.orderservice.service;

import com.ptit.htpt.orderservice.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 19 / Plan 19-01 (D-03 + Pitfall #4): cross-svc batch client gọi
 * product-svc {@code POST /admin/products/batch} để enrich top-products.
 *
 * <p>Forward Bearer JWT từ caller (admin) — product-svc batch endpoint cũng gate qua
 * {@code JwtRoleGuard}. Empty input → empty map (no HTTP). Mọi exception →
 * {@code log.warn} + return empty map (best-effort fallback theo D-03 — KHÔNG fail
 * toàn endpoint top-products).
 */
@Component
public class ProductBatchClient {

  private static final Logger log = LoggerFactory.getLogger(ProductBatchClient.class);
  private static final String URL = "http://api-gateway:8080/api/products/admin/batch";

  private final RestTemplate restTemplate;

  public ProductBatchClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public Map<String, ProductSummary> fetchBatch(List<String> ids, String authHeader) {
    if (ids == null || ids.isEmpty()) {
      return Map.of();
    }
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      // D-03 + Pitfall #4: forward Bearer xuống product-svc batch endpoint
      if (authHeader != null) {
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
      }
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(Map.of("ids", ids), headers);
      ResponseEntity<ApiResponse<List<ProductSummary>>> resp = restTemplate.exchange(
          URL, HttpMethod.POST, entity,
          new ParameterizedTypeReference<>() {});
      ApiResponse<List<ProductSummary>> body = resp.getBody();
      if (body == null || body.data() == null) {
        return Map.of();
      }
      return body.data().stream()
          .collect(Collectors.toMap(ProductSummary::id, p -> p, (a, b) -> a));
    } catch (Exception ex) {
      log.warn("[D-03] product-svc batch enrichment failed: {}", ex.getMessage());
      return Map.of();
    }
  }

  public record ProductSummary(String id, String name, String brand, String thumbnailUrl) {}
}
