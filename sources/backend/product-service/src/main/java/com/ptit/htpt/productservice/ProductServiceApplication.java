package com.ptit.htpt.productservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class ProductServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(ProductServiceApplication.class, args);
  }

  @RestController
  static class PingController {
    @GetMapping("/ping")
    String ping() {
      return "product-service:ok";
    }
  }
}
