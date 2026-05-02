---
phase: 09-residual-closure-verification
plan: "02"
subsystem: backend
tags:
  - stats-endpoints
  - jwt-role-guard
  - admin-dashboard
  - ui-02
dependency_graph:
  requires:
    - gateway routes /api/{products,orders,users}/admin/** (đã có trong api-gateway/application.yml)
    - ProductRepository, UserRepository, OrderRepository (JpaRepository baseline)
    - app.jwt.secret config (mỗi service đã có)
  provides:
    - GET /api/products/admin/stats → {totalProducts: int}
    - GET /api/orders/admin/stats → {totalOrders: int, pendingOrders: int}
    - GET /api/users/admin/stats → {totalUsers: int}
    - JwtRoleGuard (3 service copies): 401 no-header, 401 invalid-token, 403 non-admin
  affects:
    - Plan 09-04 (FE admin dashboard): wire 3 endpoints này qua Promise.allSettled
tech_stack:
  added:
    - JJWT 0.12.7 (jjwt-api, jjwt-impl, jjwt-jackson) vào product-service pom.xml
    - JJWT 0.12.7 vào order-service pom.xml
  patterns:
    - Manual JWT role check (JwtRoleGuard @Component + ResponseStatusException)
    - Spring Data JPA derived query countByStatus(String)
    - Map.of() response wrapped trong ApiResponse.of()
key_files:
  created:
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductStatsService.java
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/AdminStatsController.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/JwtRoleGuard.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/UserStatsService.java
    - sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/AdminStatsController.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/JwtRoleGuard.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderStatsService.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminStatsController.java
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
    - sources/backend/product-service/pom.xml
    - sources/backend/order-service/pom.xml
decisions:
  - "D-05 REVISED applied: manual JwtRoleGuard thay @PreAuthorize (codebase không có Spring Security)"
  - "D-06 applied: pendingOrders = status = PENDING ONLY (countByStatus không phải countByOrderStatus)"
  - "OrderEntity field là 'status', không phải 'orderStatus' — derived query phải là countByStatus()"
metrics:
  duration: "~10 phút"
  completed_date: "2026-04-27"
  tasks_completed: 2
  tasks_total: 2
  files_created: 9
  files_modified: 3
---

# Phase 9 Plan 02: Admin Stats Endpoints (UI-02 backend) Summary

**One-liner:** 3 admin-only stats endpoints với manual JWT role guard (HS256 verify) trả `{totalProducts}`, `{totalOrders, pendingOrders}`, `{totalUsers}` thật từ JPA count queries.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | product-service + user-service stats endpoints + JwtRoleGuard | b42058f | 6 files created + product pom.xml |
| 2 | order-service stats endpoint + countByStatus repo method | d0e52c1 | 3 files created + order pom.xml + OrderRepository |

## Files Created / Modified

### Created (9 files)

| File | Provides |
|------|----------|
| product-service/.../web/JwtRoleGuard.java | 401/403 manual JWT check |
| product-service/.../service/ProductStatsService.java | totalProducts() |
| product-service/.../web/AdminStatsController.java | GET /admin/products/stats |
| user-service/.../web/JwtRoleGuard.java | 401/403 manual JWT check |
| user-service/.../service/UserStatsService.java | totalUsers() |
| user-service/.../web/AdminStatsController.java | GET /admin/users/stats |
| order-service/.../web/JwtRoleGuard.java | 401/403 manual JWT check |
| order-service/.../service/OrderStatsService.java | totalOrders() + pendingOrders() |
| order-service/.../web/AdminStatsController.java | GET /admin/orders/stats |

### Modified (3 files)

| File | Change |
|------|--------|
| order-service/.../repository/OrderRepository.java | Thêm `long countByStatus(String status)` |
| product-service/pom.xml | Thêm JJWT 0.12.7 deps |
| order-service/pom.xml | Thêm JJWT 0.12.7 deps |

## Gateway Route Verification

Đã verify trong `api-gateway/src/main/resources/application.yml` — các route sau đã có sẵn:

| Gateway path | Rewrite to | Service |
|-------------|------------|---------|
| `/api/products/admin/**` | `/admin/products/**` | product-service:8080 |
| `/api/orders/admin/**` | `/admin/orders/**` | order-service:8080 |
| `/api/users/admin/**` | `/admin/users/**` | user-service:8080 |

Vậy `GET /api/products/admin/stats` → gateway rewrite → `GET /admin/products/stats` → `AdminStatsController@product-service`. Khớp hoàn toàn.

## Base Paths Verified

- product-service: `@RequestMapping("/admin/products")` + `@GetMapping("/stats")` ✓
- order-service: `@RequestMapping("/admin/orders")` + `@GetMapping("/stats")` ✓
- user-service: `@RequestMapping("/admin/users")` + `@GetMapping("/stats")` ✓

**user-service conflict check:** `AdminUserController` cũng dùng `@RequestMapping("/admin/users")` — KHÔNG conflict vì Spring map handler theo full URL. `/stats` chưa được map ở AdminUserController (chỉ có `/`, `/{id}` GET, POST, PUT, DELETE, PATCH). Spring tìm thấy đúng handler mà không ambiguous.

## Build Verification

Cả 3 service được build thành công qua `docker build`:
- `product-service-test:09-02` — build PASS
- `user-service-test:09-02` — build PASS
- `order-service-test:09-02` — build PASS

(`mvn clean package -DskipTests` chạy bên trong Docker multi-stage build)

## Curl Smoke Test Commands (chạy sau khi stack up)

Sau khi dev chạy `docker compose up -d --build`:

```bash
# Login admin lấy token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"<admin-password>"}' \
  | jq -r '.data.token')

# Login user thường lấy token
USER_TOKEN=$(curl -s -X POST http://localhost:8080/api/users/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"<user-password>"}' \
  | jq -r '.data.token')

# Case 1: admin → 200 {totalProducts: N}
curl -i -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/products/admin/stats

# Case 2: admin → 200 {totalOrders: N, pendingOrders: M}
curl -i -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/orders/admin/stats

# Case 3: admin → 200 {totalUsers: K}
curl -i -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/users/admin/stats

# Case 4: user token → 403 "ADMIN role required"
curl -i -H "Authorization: Bearer $USER_TOKEN" http://localhost:8080/api/products/admin/stats

# Case 5: no header → 401 "Missing or invalid Authorization header"
curl -i http://localhost:8080/api/products/admin/stats
```

Kỳ vọng kết quả sẽ được verify tại Plan 09-04 (FE admin dashboard) hoặc Plan 09-05 (Playwright).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Thêm JJWT 0.12.7 vào product-service pom.xml**
- **Found during:** Task 1 — sau khi tạo JwtRoleGuard.java dùng `io.jsonwebtoken.*`
- **Issue:** product-service pom.xml không có JJWT dependency (chỉ user-service có). Sẽ gây compile error khi build Docker image.
- **Fix:** Thêm 3 JJWT artifacts (jjwt-api compile, jjwt-impl runtime, jjwt-jackson runtime) version 0.12.7 vào product-service/pom.xml — match với user-service.
- **Files modified:** `sources/backend/product-service/pom.xml`
- **Commit:** b42058f

**2. [Rule 3 - Blocking] Thêm JJWT 0.12.7 vào order-service pom.xml**
- **Found during:** Task 2 — tương tự product-service, order-service cũng thiếu JJWT
- **Fix:** Thêm 3 JJWT artifacts vào order-service/pom.xml
- **Files modified:** `sources/backend/order-service/pom.xml`
- **Commit:** d0e52c1

**3. [Rule 1 - Bug] countByStatus thay vì countByOrderStatus**
- **Found during:** Task 2 — đọc OrderEntity.java
- **Issue:** Plan mô tả `countByOrderStatus(String orderStatus)` nhưng `OrderEntity` field thực tế là `status` (không phải `orderStatus`). Nếu dùng `countByOrderStatus`, Spring Data sẽ throw `PropertyReferenceException` khi khởi động.
- **Fix:** Dùng `countByStatus(String status)` — đúng với field name trong entity.
- **Files modified:** `OrderRepository.java`, `OrderStatsService.java`
- **Commit:** d0e52c1

## Known Stubs

Không có stubs — tất cả endpoints đều gọi JPA `.count()` / `countByStatus()` thật, trả số liệu thật từ DB.

## Threat Flags

Không phát hiện surface mới ngoài threat model đã định nghĩa trong plan. T-09-02-01 đến T-09-02-05 đều được mitigate bởi `JwtRoleGuard.requireAdmin()`.

## Self-Check: PASSED

Files exist:
- `sources/backend/product-service/.../web/JwtRoleGuard.java` ✓
- `sources/backend/product-service/.../service/ProductStatsService.java` ✓
- `sources/backend/product-service/.../web/AdminStatsController.java` ✓
- `sources/backend/user-service/.../web/JwtRoleGuard.java` ✓
- `sources/backend/user-service/.../service/UserStatsService.java` ✓
- `sources/backend/user-service/.../web/AdminStatsController.java` ✓
- `sources/backend/order-service/.../web/JwtRoleGuard.java` ✓
- `sources/backend/order-service/.../service/OrderStatsService.java` ✓
- `sources/backend/order-service/.../web/AdminStatsController.java` ✓

Commits exist: b42058f ✓, d0e52c1 ✓
