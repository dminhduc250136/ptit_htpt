# Roadmap: tmdt-use-gsd E-Commerce Platform

## Overview

Stabilize the existing microservices + gateway + Next.js frontend by making the API surface consistent, complete, and well-documented (Swagger/OpenAPI), then evolve through hardening + integration milestones to reach a production-leaning MVP.

## Shipped Milestones

- ✅ **v1.0 — MVP Stabilization** (shipped 2026-04-25, 4 phases, 14 plans, 57 commits) — see [milestones/v1.0-ROADMAP.md](./milestones/v1.0-ROADMAP.md)

## Current Milestone

**v1.1 — Real End-User Experience** (started 2026-04-25, priority: visible-first)

Mục tiêu: biến demo flow từ "stub-verified" thành "real visible end-to-end" — mọi thứ user click trên UI hoạt động với real data thay vì mock/seeded. Audit phát hiện v1.0 chạy in-memory (không có DB layer) → v1.1 mở đầu bằng cluster C0 Database Foundation block C1/C2/C3.

## Phase Numbering

- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (5.1, 5.2): Urgent insertions (marked with INSERTED)
- v1.1 phases tiếp tục từ v1.0 (Phase 5..8), KHÔNG reset

## Phases (v1.1)

- [ ] **Phase 5: Database Foundation** — Đưa Postgres + JPA + Flyway vào stack, refactor in-memory repos, seed dev data từ FE mocks (block các phase sau)
- [ ] **Phase 6: Real Auth Flow** — Backend ship `/api/users/auth/{register,login,logout}` thật + FE form gỡ mock, session persist sau reload
- [ ] **Phase 7: Search + Admin Real Data** — `/search` rewire + admin/products/orders/users migrate khỏi mock sang CRUD thật qua gateway
- [ ] **Phase 8: Cart → Order Persistence Visible** — ProductEntity.stock persist + OrderEntity per-item rows + shippingAddress/paymentMethod, FE order detail render full breakdown thật

## Phase Details

### Phase 5: Database Foundation
**Goal**: Stack có Postgres thật + JPA + Flyway; toàn bộ repos refactor khỏi in-memory; seed dev data từ FE mocks để zero UX surprise; gateway round-trip qua FE trả seeded data thật từ DB.
**Depends on**: Nothing (đầu milestone v1.1; v1.0 đã shipped)
**Requirements**: DB-01, DB-02, DB-03, DB-04, DB-05, DB-06
**Success Criteria** (what must be TRUE):
  1. `docker compose up` khởi động Postgres container green với healthcheck PASS, volume persist data giữa restarts
  2. 5 services (user/product/order/payment/inventory) start green với JPA + Flyway baseline migration applied (`V1__init_schema.sql`)
  3. Flyway dev seed (`V2__seed_dev_data.sql`) populate đúng products/orders từ FE mocks + 1 admin user (`admin/admin123` BCrypt) + 5 categories
  4. FE `GET /api/products` qua gateway trả seeded products thật từ Postgres (verify bằng cách query trực tiếp DB → match payload)
  5. Sau verify: `sources/frontend/src/mock-data/` đã được xóa; FE flow visible (browse danh mục, product detail, add-to-cart) vẫn PASS với seeded data thật từ Postgres. Checkout submit + confirmation full breakdown defer Phase 8 (PERSIST-01..03) — Phase 5 chỉ cam kết các trang đó không vỡ build/render.
**Plans:** 9 plans
Plans:
- [ ] 05-01-PLAN.md — Pre-flight: capture OpenAPI baselines (5 services) + verify BCrypt admin123 hash
- [ ] 05-02-PLAN.md — Infra: Postgres container + db/init/01-schemas.sql + docker-compose wiring (5 services depends_on healthcheck)
- [ ] 05-03-PLAN.md — product-service refactor (canonical) — JPA + Flyway V1 + V2 (5 cats + 10 products) + DTO/Mapper boundary
- [ ] 05-04-PLAN.md — user-service refactor — rename UserProfile→UserEntity + V1 + V2 (admin BCrypt + demo_user)
- [ ] 05-05-PLAN.md — order-service refactor — V1 (preserve `note` field) + V2 (2 demo orders cho demo_user)
- [ ] 05-06-PLAN.md — payment-service refactor — V1 align entity actual fields (sessionId/reference/message) + KHÔNG V2
- [ ] 05-07-PLAN.md — inventory-service refactor — rename InventoryItem→InventoryEntity + V1 + V2 (10 rows align prod-001..010)
- [ ] 05-08-PLAN.md — Integration verify: docker compose stack + Flyway history + gateway round-trip + OpenAPI diff = 0
- [ ] 05-09-PLAN.md — FE cleanup: xóa mock-data + rewire flow chính + Playwright audit + manual sign-off

### Phase 6: Real Auth Flow
**Goal**: User đăng ký + đăng nhập + đăng xuất thật qua backend; JWT issued; FE form gỡ mock; session persist sau page reload; protected routes redirect đúng khi không có session.
**Depends on**: Phase 5 (cần `UserEntity` persist trong Postgres để register/login query thật)
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05, AUTH-06
**Success Criteria** (what must be TRUE):
  1. User register qua FE form → backend persist `UserEntity` với BCrypt hash → `201 Created`; trùng username/email trả `409 CONFLICT` qua `ApiErrorResponse` và FE hiện field errors
  2. User login qua FE form → backend verify cred → trả `{accessToken, user}` (JWT HS256, claim `sub/username/roles/exp`); sai cred → `401 INVALID_CREDENTIALS` và FE hiện thông báo lỗi
  3. Sau login: token + user lưu localStorage + middleware-readable cookie; user reload page vẫn còn session, không bị kick về `/login`
  4. Logout endpoint invalidate token (blacklist hoặc client-side discard); sau logout user truy cập `/account/*` bị redirect về `/login` đúng
  5. Protected routes (`/account/*`, `/checkout/*`, `/admin/*`) middleware redirect khi không có session; admin role check (`roles` array contains `ADMIN`) gate `/admin/*` đúng
**Plans**: TBD
**UI hint**: yes

### Phase 7: Search + Admin Real Data
**Goal**: FE `/search` page và toàn bộ `admin/*` pages migrate khỏi mock sang CRUD thật qua gateway; admin có thể quản lý products/orders/users với data thật từ Postgres.
**Depends on**: Phase 5 (cần DB thật để CRUD), Phase 6 (cần admin role + JWT để gate `/admin/*` pages)
**Requirements**: UI-01, UI-02, UI-03, UI-04
**Success Criteria** (what must be TRUE):
  1. User nhập keyword vào `/search` → FE call `listProducts({keyword, page, size})` qua gateway → render kết quả thật từ DB; empty state hiện "Không tìm thấy sản phẩm cho '{keyword}'", loading state hiện skeleton
  2. Admin login → vào `admin/products` → list từ backend; create/edit/delete product qua form/dialog → success toast → list refresh; gỡ hoàn toàn mock data
  3. Admin vào `admin/orders` → list orders thật; click row mở detail page show full order với line items + status; admin update status (`PENDING → SHIPPED → DELIVERED`) persist trong DB
  4. Admin vào `admin/users` → list users thật; admin xem detail + soft-delete user qua existing CRUD endpoints, list refresh đúng sau action
**Plans**: TBD
**UI hint**: yes

### Phase 8: Cart → Order Persistence Visible
**Goal**: ProductEntity.stock persist trong DB (gỡ "cart-seed via localStorage"); OrderEntity persist per-item OrderItem rows + shippingAddress + paymentMethod; FE order confirmation + order detail render full breakdown thật từ backend payload.
**Depends on**: Phase 5 (cần entity layer thật), Phase 6 (cần user authenticated để place order với userId real từ JWT)
**Requirements**: PERSIST-01, PERSIST-02, PERSIST-03
**Success Criteria** (what must be TRUE):
  1. `ProductEntity.stock` persist trong DB; `GET /api/products/{id}` + `GET /api/products/slug/{slug}` trả `stock` trong payload; A4 add-to-cart respect stock thật, hết "cart-seed via localStorage"
  2. Khi `stock=0` và user thêm vào cart → backend trả `409 STOCK_SHORTAGE` qua `ApiErrorResponse`; FE dispatcher hiện thông báo "Hết hàng" đúng cho user
  3. `POST /api/orders` persist `OrderEntity` với per-item `OrderItemEntity` rows (productId, productName snapshot, quantity, unitPrice snapshot, lineTotal) + `shippingAddress` (JSON/embedded) + `paymentMethod`
  4. `GET /api/orders/{id}` + `GET /api/orders/me` trả full payload với items array + shippingAddress + paymentMethod
  5. FE `/checkout/success` (confirmation) và `/account/orders/{id}` (detail) render full breakdown thật từ backend (line items + địa chỉ + phương thức thanh toán + totals); hết mock data trên 2 trang này
**Plans**: TBD
**UI hint**: yes

## Progress

| Milestone | Phases Complete | Status | Shipped |
|-----------|-----------------|--------|---------|
| v1.0 MVP Stabilization | 4/4 | Shipped | 2026-04-25 |
| v1.1 Real End-User Experience | 0/4 | Active | — |

### v1.1 Phase Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 5. Database Foundation | 0/9 | Planned | — |
| 6. Real Auth Flow | 0/TBD | Not started | — |
| 7. Search + Admin Real Data | 0/TBD | Not started | — |
| 8. Cart → Order Persistence Visible | 0/TBD | Not started | — |
