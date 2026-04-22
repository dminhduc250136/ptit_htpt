# product-service ENV Guide

## Biến môi trường cần thiết
- SPRING_DATASOURCE_URL
- SPRING_DATASOURCE_USERNAME
- SPRING_DATASOURCE_PASSWORD
- JWT_SECRET
- CORS_ALLOWED_ORIGINS

## Ví dụ cấu hình (application.yml)
spring:
  application:
    name: product-service
server:
  port: 8083
---
spring:
  profiles: dev
  datasource:
    url: jdbc:mysql://localhost:3306/product_db
    username: root
    password: password
jwt:
  secret: your_jwt_secret
  expiration: 3600
cors:
  allowed-origins: http://localhost:3000
