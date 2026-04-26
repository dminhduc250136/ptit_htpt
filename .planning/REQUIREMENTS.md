---
milestone: v1.1
name: Real End-User Experience
status: active
started: 2026-04-25
priority: visible-first
---

# Milestone v1.1 — Real End-User Experience

## Goal

Biến demo flow từ "stub-verified" thành "real visible end-to-end" — mọi thứ user click trên UI đều phải hoạt động với real data thay vì mock/seeded. Ưu tiên những thay đổi end-user nhìn thấy được; defer backend hardening / security / observability invisible sang v1.2.

## Foundation từ v1.0 (đã có sẵn, KHÔNG re-build)

- Unified `ApiErrorResponse` envelope + `traceId` propagation across 6 services + gateway
- Springdoc Swagger UI + OpenAPI codegen pipeline (FE 6 typed modules)
- CRUD completeness + soft-delete baseline + admin/public route boundaries (in-memory layer)
- Validation hardening (gateway pass-through + common-code taxonomy)
- FE typed HTTP tier + ApiError dispatcher (5 failure branches) + middleware route protection
- Playwright E2E suite hoạt động (12/12 PASS trên flow stub-verified)

## Audit Finding: DB Layer Hiện Trạng

Discovered 2026-04-25 trong khi planning v1.1: **không service nào có `spring-boot-starter-data-jpa` dependency, `docker-compose.yml` không có Postgres container, không có `application.yml` datasource config**. v1.0 "CRUD completeness" thực chất chạy trên in-memory store (ConcurrentHashMap-style), không persist gì. Đây là gap phải đóng trước khi C1/C2/C3 có thể "real" được — không có DB = không có v1.1 goal.

## v1.1 Requirements

### C0. Database Foundation (depends-on cho C1/C2/C3)

- [ ] **DB-01**: Postgres service (single instance, multi-schema hoặc multi-db tùy phase plan) thêm vào `docker-compose.yml`; healthcheck đảm bảo DB ready trước khi services start; volume persist data giữa restarts.
- [ ] **DB-02**: Add `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` + `flywaydb:flyway-core` vào 5 services có entity (user, product, order, payment, inventory). Notification có thể giữ in-memory nếu chỉ dispatch.
- [ ] **DB-03**: Mỗi service add `application.yml` datasource config (URL, username, password từ env vars), JPA properties (`ddl-auto: validate` — schema do Flyway control, KHÔNG `create-drop`), Flyway baseline migration `V1__init_schema.sql` reflect existing JPA entities.
- [ ] **DB-04**: Refactor existing repositories từ in-memory → JPA: convert mỗi `XxxRepository` thành `interface XxxRepository extends JpaRepository<XxxEntity, Long>`; existing service methods reuse signatures; tests update accordingly.
- [ ] **DB-05**: Seed dev data via Flyway `V2__seed_dev_data.sql` (profile `dev` only), extract từ `sources/frontend/src/mock-data/products.ts` + `orders.ts` + thêm 1 admin user (`admin/admin123`, BCrypt hash) + 5 categories. FE thấy đúng data như khi còn dùng mock → zero UX surprise.
- [x] **DB-06
**: End-to-end connectivity verify — `docker compose up` → tất cả services start green → gateway round-trip `GET /api/products` qua FE trả seeded products thật từ DB (không phải in-memory). Sau verify: xóa `sources/frontend/src/mock-data/` (đã không cần nữa).

### C1. Auth Flow Thật

- [ ] **AUTH-01**: Backend ship `/api/users/auth/register` endpoint thật — nhận `{username, email, password}`, persist `UserEntity` với password hash (BCrypt), trả `201 Created` + user payload (không trả password). Trùng username/email → `409 CONFLICT` qua `ApiErrorResponse`.
- [ ] **AUTH-02**: Backend ship `/api/users/auth/login` endpoint thật — verify credentials, issue JWT (HS256, claim: `sub=userId`, `username`, `roles`, `exp`), trả `{accessToken, user}`. Sai cred → `401 INVALID_CREDENTIALS`.
- [ ] **AUTH-03**: Backend ship `/api/users/auth/logout` endpoint — invalidate token (blacklist hoặc client-side discard, chọn approach đơn giản nhất cho MVP).
- [ ] **AUTH-04**: FE login form thật sự call `/api/users/auth/login` qua gateway (gỡ mock trong `services/auth.ts`); store token + user vào `localStorage` + middleware-readable cookie; redirect về `/` sau khi login thành công.
- [ ] **AUTH-05**: FE register form thật sự call `/api/users/auth/register`; show field errors từ `ApiErrorResponse.fieldErrors`; auto-login sau register thành công.
- [ ] **AUTH-06**: Session persist sau page reload — middleware đọc cookie/token, FE init state từ `localStorage` khi mount, protected routes (`/account/*`, `/checkout/*`, `/admin/*`) redirect đúng nếu không có session.

### C2. Admin + Search Real Data

- [ ] **UI-01**: FE `/search` page rewire — input keyword call `listProducts({keyword, page, size})` qua gateway, render kết quả thật (gỡ placeholder/mock list); empty state hiển thị "Không tìm thấy sản phẩm cho '{keyword}'"; loading state hiển thị skeleton.
- [ ] **UI-02**: FE `admin/products` migrate khỏi mock — list call `listProducts(admin scope)`; create/edit form call `createProduct` / `updateProduct`; delete confirm dialog call `deleteProduct`; success toast + refresh list.
- [ ] **UI-03**: FE `admin/orders` migrate khỏi mock — list call `listOrders(admin scope)` real qua gateway; click vào row mở detail page show full order với line items + status; admin có thể update status (`PENDING → SHIPPED → DELIVERED`).
- [ ] **UI-04**: FE `admin/users` migrate khỏi mock — list call `listUsers(admin scope)` real qua gateway; admin có thể view + soft-delete user (existing CRUD endpoints).

### C3. Cart → Order Persistence Visible

- [ ] **PERSIST-01**: Backend `ProductEntity` thêm field `stock: int` persist trong DB; `GET /api/products/{id}` + `GET /api/products/slug/{slug}` trả `stock` trong payload; `addToCart` flow check stock thật (không seed qua localStorage nữa); A4 step "out of stock" trả `409 STOCK_SHORTAGE` qua `ApiErrorResponse` khi stock=0.
- [ ] **PERSIST-02**: Backend `OrderEntity` persist per-item `OrderItemEntity` rows (productId, productName snapshot, quantity, unitPrice snapshot, lineTotal); `OrderEntity` thêm `shippingAddress` (JSON column hoặc embedded) + `paymentMethod` (string); `POST /api/orders` save full breakdown; `GET /api/orders/{id}` + `GET /api/orders/me` trả full payload với items array.
- [ ] **PERSIST-03**: FE order confirmation page (`/checkout/success`) + order detail page (`/account/orders/{id}`) render full breakdown thật từ backend payload (line items + shipping address + payment method + totals) — gỡ mock data.

## Future Requirements (defer sang v1.2)

Audit `.planning/v1.0-MILESTONE-AUDIT.md` đã liệt kê 17 deferred items; v1.1 chỉ pick 6 items có visible impact. Các items dưới đây defer tiếp:

- **D1** — Order → inventory.reserve real call (stock reservation chain)
- **D2** — Payment-service vào checkout chain (real PAYMENT_FAILED body)
- **D6** — Real 5xx HTML route từ gateway/Nginx (B5 stub)
- **D7** — Code review WR-03/05/06/07 + IN-01..07 carry-over
- **D8** — TEST-01 (integration suite) + OBS-01 (centralized tracing)
- **D9** — Sibling-service handleFallback observability rollout (5 services còn lại)
- **D10** — CategoryEntity.slug persist (đang dùng slugify fallback)
- **D12** — CSP headers
- **D13** — Server-side product price re-fetch (security)
- **D14** — Replace X-User-Id header trust bằng JWT claim verification ở gateway (security)
- **D17** — FE legacy types/index.ts + services/api.ts cleanup

## Out of Scope (v1.1)

- Real payment gateway integration — mock acceptable cho dự án thử nghiệm
- Production-grade auth (refresh tokens, OAuth, password reset email) — chỉ ship register + login + logout đơn giản
- Mobile responsive polish toàn bộ — chỉ check không bị broken trên flow chính
- Admin role-based access control phức tạp — đơn giản: kiểm tra `roles` array có chứa `ADMIN` là đủ
- I18n cho error messages — tiếng Việt hardcoded OK

## Requirement Quality Self-Check

| Tiêu chí | Kết quả |
|---|---|
| Specific & testable | ✓ Mỗi REQ có endpoint/behavior cụ thể, có thể UAT |
| User-centric | ✓ AUTH/UI viết theo "User can / FE shows"; PERSIST viết theo "user thấy full breakdown"; DB-06 verify qua FE round-trip thật |
| Atomic | ✓ DB chia 6 sub-req (compose / deps / config / refactor / seed / verify); AUTH 6; UI 4; PERSIST 3 |
| Independent (trong cluster) | ✓ Trong-cluster độc lập. Cross-cluster: C0 block C1/C2/C3 (xử lý bằng wave-based execution trong roadmap) |

## Traceability

| REQ | Phase | Plan | Status |
|---|---|---|---|
| DB-01 | Phase 5 | TBD | active |
| DB-02 | Phase 5 | TBD | active |
| DB-03 | Phase 5 | TBD | active |
| DB-04 | Phase 5 | TBD | active |
| DB-05 | Phase 5 | TBD | active |
| DB-06 | Phase 5 | TBD | active |
| AUTH-01 | Phase 6 | TBD | active |
| AUTH-02 | Phase 6 | TBD | active |
| AUTH-03 | Phase 6 | TBD | active |
| AUTH-04 | Phase 6 | TBD | active |
| AUTH-05 | Phase 6 | TBD | active |
| AUTH-06 | Phase 6 | TBD | active |
| UI-01 | Phase 7 | TBD | active |
| UI-02 | Phase 7 | TBD | active |
| UI-03 | Phase 7 | TBD | active |
| UI-04 | Phase 7 | TBD | active |
| PERSIST-01 | Phase 8 | TBD | active |
| PERSIST-02 | Phase 8 | TBD | active |
| PERSIST-03 | Phase 8 | TBD | active |
