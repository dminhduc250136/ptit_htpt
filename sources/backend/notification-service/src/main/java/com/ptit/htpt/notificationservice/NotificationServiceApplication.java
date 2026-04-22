package com.ptit.htpt.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class NotificationServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }

  @RestController
  static class PingController {
    @GetMapping("/ping")
    String ping() {
      return "notification-service:ok";
    }
  }
}
