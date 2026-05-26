package com.ptit.htpt.orderservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 19 / Plan 19-01 Task 1 — unit test cho {@link ProductBatchClient} fallback semantics.
 */
class ProductBatchClientTest {

  private RestTemplate restTemplate;
  private ProductBatchClient client;

  @BeforeEach
  void setUp() {
    restTemplate = Mockito.mock(RestTemplate.class);
    client = new ProductBatchClient(restTemplate);
  }

  @Test
  void fetchBatch_emptyInput_returnsEmptyMapNoHttpCall() {
    Map<String, ProductBatchClient.ProductSummary> result =
        client.fetchBatch(List.of(), "Bearer admin");

    assertThat(result).isEmpty();
    verify(restTemplate, never()).exchange(
        any(String.class), any(HttpMethod.class), any(HttpEntity.class),
        any(ParameterizedTypeReference.class));
  }

  @Test
  void fetchBatch_nullInput_returnsEmptyMap() {
    assertThat(client.fetchBatch(null, "Bearer admin")).isEmpty();
  }

  @Test
  void fetchBatch_restTemplateThrows_returnsEmptyMapWithoutPropagating() {
    when(restTemplate.exchange(
            eq("http://api-gateway:8080/api/products/admin/batch"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            any(ParameterizedTypeReference.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    Map<String, ProductBatchClient.ProductSummary> result =
        client.fetchBatch(List.of("p-1", "p-2"), "Bearer admin");

    assertThat(result).isEmpty();
  }
}
