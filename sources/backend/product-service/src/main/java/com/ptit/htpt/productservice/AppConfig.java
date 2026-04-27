package com.ptit.htpt.productservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Phase 13: RestTemplate @Bean cho ReviewService gọi order-svc internal endpoint.
 * Timeout (Pitfall 5): connect 2s, read 3s — fail-fast nếu order-svc unreachable.
 */
@Configuration
public class AppConfig {
  @Bean
  public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(2000);
    factory.setReadTimeout(3000);
    return new RestTemplate(factory);
  }
}
