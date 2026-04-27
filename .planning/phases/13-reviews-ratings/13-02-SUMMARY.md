---
phase: 13-reviews-ratings
plan: "02"
subsystem: api
tags: [spring-boot, jpa, jpql, testcontainers, internal-endpoint, buyer-eligibility]

requires:
  - phase: 11-order-history
    provides: OrderEntity, OrderItemEntity, OrderRepository patterns

provides:
  - "GET /internal/orders/eligibility endpoint trên order-service (Docker-network-only)"
  - "OrderRepository.existsDeliveredOrderWithProduct JPQL query"
  - "InternalOrderControllerTest — 2 integration test cases"

affects:
  - 13-03-product-svc-reviews
  - 13-04-frontend-reviews

tech-stack:
  added: []
  patterns:
    - "Internal controller pattern: @RequestMapping('/internal/...') — NOT routed via api-gateway"
    - "JPQL COUNT > 0 JOIN query cho boolean eligibility check"
    - "Testcontainers @SpringBootTest(RANDOM_PORT) + TestRestTemplate pattern"

key-files:
  created:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/InternalOrderController.java
    - sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/InternalOrderControllerTest.java
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java

key-decisions:
  - "Internal endpoint KHÔNG expose qua api-gateway — Docker network isolation là security boundary (T-13-02-01)"
  - "JPQL COUNT(o) > 0 thay vì EXISTS subquery — tương thích tốt hơn với Hibernate 6"
  - "Status 'DELIVERED' hardcode trong JPQL — không nhận param (D-02 lock)"
  - "ApiResponse.of(200, ..., Map.of('eligible', bool)) — giữ envelope nhất quán với các endpoint khác"

patterns-established:
  - "Internal service endpoint: controller riêng /internal/**, không thêm route gateway"
  - "Eligibility boolean query: JPQL JOIN + COUNT > 0 trên related entity"

requirements-completed: [REV-01]

duration: 5min
completed: "2026-04-27"
---

# Phase 13 Plan 02: Internal Eligibility Endpoint Summary

**Internal endpoint GET /internal/orders/eligibility trên order-service — JPQL JOIN query check DELIVERED order, Docker-network-only, không qua api-gateway**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-04-27T16:02:23Z
- **Completed:** 2026-04-27T16:07:46Z
- **Tasks:** 2
- **Files modified:** 3 (1 modified, 2 created)

## Accomplishments

- `OrderRepository.existsDeliveredOrderWithProduct(userId, productId)` — JPQL `COUNT(o) > 0 JOIN o.items` với status='DELIVERED' hardcode, `@Param` parameterized (T-13-02-03 JPQL injection mitigation)
- `InternalOrderController` — GET `/internal/orders/eligibility` trả `ApiResponse<Map<String,Boolean>>` với `{eligible: bool}`
- `InternalOrderControllerTest` — 2 integration test cases (eligible=true khi DELIVERED order tồn tại, eligible=false khi không có data), pattern Testcontainers + `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`
- api-gateway/application.yml KHÔNG có route `/internal/**` — verify `grep -c '/internal/' yml` = 0

## Task Commits

1. **Task 1: Thêm OrderRepository.existsDeliveredOrderWithProduct()** - `f72eba6` (feat)
2. **Task 2: Tạo InternalOrderController + integration test** - `4737b91` (feat)

## Files Created/Modified

- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java` — Thêm method `existsDeliveredOrderWithProduct` với JPQL JOIN query
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/InternalOrderController.java` — Controller mới `/internal/orders/eligibility` (NEW)
- `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/InternalOrderControllerTest.java` — Integration test 2 cases (NEW)

## Decisions Made

- `COUNT(o) > 0` trong JPQL thay vì `EXISTS` subquery — Hibernate 6 trên Spring Boot 3.3.2 support tốt hơn (RESEARCH assumption A4 confirmed)
- Status 'DELIVERED' hardcode trong JPQL query — không nhận param bên ngoài (D-02 lock, phòng eligibility bypass)
- Không thêm authentication trên endpoint `/internal/orders/**` — Docker network isolation là boundary đủ cho demo scale (T-13-02-01 accept disposition)
- `Map.of("eligible", eligible)` return type — simple, không cần DTO riêng, nhất quán với pattern dự án

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

**Testcontainers Docker connection (pre-existing infrastructure issue):**
- Testcontainers JVM không kết nối được Docker daemon từ bash/mvn CLI trên Windows (Docker trả `Status 400` qua `npipe:////./pipe/docker_engine`)
- Vấn đề pre-existing: `OrderControllerCreateOrderCommandTest` và `OrderRepositoryJpaTest` cũng fail với cùng lỗi
- Code compile đúng (`-DskipTests compile test-compile` exit 0), test pattern đúng (mirror `OrderControllerCreateOrderCommandTest`)
- Nguyên nhân: Docker Desktop dùng `dockerDesktopLinuxEngine` pipe nhưng Testcontainers `NpipeSocketClientProviderStrategy` thử `docker_engine` pipe trả partial 400 response
- Không phải lỗi do Plan 02 code — là infrastructure gap trên môi trường dev Windows

## Known Stubs

None — endpoint trả real data từ DB query, không có stub hay hardcoded values.

## Threat Flags

Không có surface mới ngoài plan's threat_model. T-13-02-01, T-13-02-02, T-13-02-03, T-13-02-04 đều được handle theo plan.

## Next Phase Readiness

- Plan 03 (product-svc reviews endpoint) có thể gọi `GET http://order-service:8080/internal/orders/eligibility` để check buyer eligibility
- Endpoint ready khi Docker compose stack chạy (smoke test: `curl http://order-service:8080/internal/orders/eligibility?userId=X&productId=Y`)
- Testcontainers issue cần được resolve trên môi trường dev nếu muốn `./mvnw test` pass locally

---
*Phase: 13-reviews-ratings*
*Completed: 2026-04-27*
