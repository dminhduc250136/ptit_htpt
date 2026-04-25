package com.ptit.htpt.orderservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderControllerCreateOrderCommandTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  private static final String VALID_COMMAND_JSON = """
      {
        "items": [{
          "productId": "prod-uat-1",
          "quantity": 2,
          "unitPrice": 99000
        }],
        "shippingAddress": {
          "street": "1 Cau Giay",
          "ward": "Dich Vong",
          "district": "Cau Giay",
          "city": "Ha Noi"
        },
        "paymentMethod": "COD",
        "note": "UAT test"
      }
      """;

  private HttpHeaders jsonHeaders(String userId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (userId != null) headers.add("X-User-Id", userId);
    return headers;
  }

  @Test
  void createOrder_returns201_withPendingStatus_whenCommandAndHeaderValid() {
    HttpEntity<String> req = new HttpEntity<>(VALID_COMMAND_JSON, jsonHeaders("user-uat-1"));
    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:" + port + "/orders", HttpMethod.POST, req, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body).contains("\"status\":201");
    assertThat(body).contains("\"data\":");
    assertThat(body).contains("\"userId\":\"user-uat-1\"");
    assertThat(body).contains("\"status\":\"PENDING\"");
    // totalAmount = 2 * 99000 = 198000 (relaxed substring assertion to avoid Jackson BigDecimal
    // serialization variants like 198000.0 / "198000")
    assertThat(body).contains("198000");
  }

  @Test
  void createOrder_returns400_whenXUserIdMissing() {
    HttpEntity<String> req = new HttpEntity<>(VALID_COMMAND_JSON, jsonHeaders(null));
    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:" + port + "/orders", HttpMethod.POST, req, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String body = response.getBody();
    assertThat(body).contains("\"code\":\"BAD_REQUEST\"");
    assertThat(body).contains("Missing X-User-Id");
  }

  @Test
  void createOrder_returns400_validationError_whenItemsEmpty() {
    String emptyItemsJson = """
        {
          "items": [],
          "shippingAddress": {
            "street": "1 Cau Giay",
            "ward": "Dich Vong",
            "district": "Cau Giay",
            "city": "Ha Noi"
          },
          "paymentMethod": "COD"
        }
        """;
    HttpEntity<String> req = new HttpEntity<>(emptyItemsJson, jsonHeaders("user-uat-2"));
    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:" + port + "/orders", HttpMethod.POST, req, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String body = response.getBody();
    assertThat(body).contains("\"code\":\"VALIDATION_ERROR\"");
    assertThat(body).contains("\"items\"");   // fieldErrors entry references items
  }
}
