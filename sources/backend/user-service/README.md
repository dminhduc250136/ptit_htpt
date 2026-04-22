# User Service

## Mục tiêu
Khởi tạo User Service (Spring Boot) chạy độc lập.

## Cách chạy local

### 1. Yêu cầu
- Java 17 trở lên
- Maven
- MySQL (tạo sẵn database user_db)

### 2. Cấu hình
- Sửa file `src/main/resources/application.yml` nếu cần thay đổi thông tin DB, JWT, CORS...

### 3. Chạy ứng dụng
```bash
mvn spring-boot:run
```

Ứng dụng sẽ chạy ở port 8084 (mặc định).

## Cấu trúc package chuẩn
- controller: Xử lý request API
- service: Xử lý logic nghiệp vụ
- repository: Tương tác database
- model: Định nghĩa entity/model

## Ví dụ cấu hình application.yml
```yaml
spring:
  application:
    name: user-service
server:
  port: 8084
---
spring:
  profiles: dev
  datasource:
    url: jdbc:mysql://localhost:3306/user_db
    username: root
    password: password
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    database-platform: org.hibernate.dialect.MySQLDialect
jwt:
  secret: your_jwt_secret
  expiration: 3600
cors:
  allowed-origins: http://localhost:3000
```
