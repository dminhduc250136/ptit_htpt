package com.ptit.htpt.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Application configuration — shared beans.
 */
@Configuration
public class AppConfig {

  /**
   * RestTemplate dùng bởi MomoService để POST sang MoMo create endpoint.
   */
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
