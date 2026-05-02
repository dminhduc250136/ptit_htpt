# Milestones

## v1.0 — MVP Stabilization ✅ SHIPPED

**Goal:** Make API surface consistent and complete (CRUD + Swagger/OpenAPI) and align frontend-backend contracts so E2E validation is reliable.

**Scope highlights:**

- CRUD completeness across services
- Swagger/OpenAPI for services + gateway
- Standardized DTOs, status codes, and error formats
- Contract alignment across gateway ↔ services ↔ frontend

**Status:** Shipped 2026-04-25 (started 2026-04-22, 4 phases / 14 plans / 57 commits, Playwright 12/12 PASS).
**Archive:** [milestones/v1.0-ROADMAP.md](./milestones/v1.0-ROADMAP.md) + [milestones/v1.0-REQUIREMENTS.md](./milestones/v1.0-REQUIREMENTS.md)
**Audit:** [v1.0-MILESTONE-AUDIT.md](./v1.0-MILESTONE-AUDIT.md) (status: ready_to_complete)

## v1.1 — Real End-User Experience ✅ SHIPPED

**Goal:** Biến demo flow từ "stub-verified" thành "real visible end-to-end" — mọi thứ user click trên UI hoạt động với real data thay vì mock/seeded. Audit v1.0 phát hiện in-memory layer → v1.1 mở đầu bằng cluster C0 Database Foundation.

**Scope highlights:**

- C0 Database Foundation — Postgres + JPA + Flyway + seed từ FE mocks (block C1/C2/C3)
- C1 Real auth flow — backend `/api/users/auth/*` + JWT HS256 + FE form gỡ mock + session persist
- C2 Admin + Search real CRUD — `/search` keyword + admin/products|orders|users qua gateway
- C3 Cart → Order persistence — ProductEntity.stock + OrderItemEntity per-row + shippingAddress/paymentMethod + FE full breakdown

**Status:** Shipped 2026-04-26 (started 2026-04-25, 4 phases / 22 plans / 289 commits, 15/19 REQs SATISFIED + 4 PARTIAL).
**Archive:** [milestones/v1.1-ROADMAP.md](./milestones/v1.1-ROADMAP.md) + [milestones/v1.1-REQUIREMENTS.md](./milestones/v1.1-REQUIREMENTS.md)
**Audit:** [milestones/v1.1-MILESTONE-AUDIT.md](./milestones/v1.1-MILESTONE-AUDIT.md) (status: gaps_found — proceeded với 4 partial gaps làm tech debt)

### Known Gaps (deferred v1.2)

- **AUTH-06 partial** — middleware.ts matcher chỉ `/admin/:path*` (spec yêu cầu thêm `/account|/profile|/checkout`). Compensating control: http.ts 401 redirect.
- **UI-02 partial** — admin landing dashboard (`app/admin/page.tsx`) còn empty mock arrays → KPI stats luôn = 0. Sub-routes CRUD OK.
- **Nyquist VALIDATION.md** — Phase 6 + Phase 8 thiếu (visible-first priority cho phép defer)
- **Backend hardening** — D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17 (carry-over từ v1.0 audit)
- **Behavioral E2E tests** — Playwright re-baseline cho v1.1 features mới
- **10 behavioral checks human_needed** — Phase 6 + Phase 7 (cần browser + docker stack chạy — manual UAT)

### Closed cuối milestone (commit `346092b`)

- DB-05 — order-service Flyway V2 collision (rename V2__seed_dev_data.sql → V100__)
- PERSIST-01 cart-side gap — CartItem.stock snapshot + clamp + cart page nút '+' disabled
- Bug login redirect loop — http.ts skip 401 redirect cho auth endpoints

## v1.2 — UI/UX Completion ✅ SHIPPED (passed)

**Goal:** Hoàn thiện trải nghiệm UI/UX end-user — close residual gaps từ v1.1 + ship core visible features (account / reviews / basic search / public polish), nâng demo flow lên "trải nghiệm UX gần production-ready".

**Scope highlights:**

- Phase 9 — Residual closure (AUTH-06 middleware mở rộng 4 routes, AUTH-07 password change, UI-02 admin dashboard real KPI, TEST-01 Playwright re-baseline 14 tests)
- Phase 10 — User-svc schema + profile editing (V3 avatar reserved, ACCT-03 fullName/phone qua /profile/settings)
- Phase 11 — Address book + order history filtering (V4 addresses + cap 10/user, ACCT-02/05/06 + AddressPicker checkout)
- Phase 12 — ~~Wishlist~~ SKIPPED (scope trim 2026-04-27, V5 migration reserved cho v1.3)
- Phase 13 — Reviews & ratings (V4 reviews + V5 avg_rating cached, REV-01/02/03 verified-buyer cross-service)
- Phase 14 — Basic search filters (JPQL cast IS NULL pattern + FilterSidebar component, SEARCH-01 brand multi-select + SEARCH-02 price range)
- Phase 15 — Public polish + milestone audit (PUB-01/02/03/04 hero next/image + Featured scroll-snap + 3-tier stock badge + breadcrumb + TEST-02 smoke E2E 4 tests)

**Status:** Shipped 2026-05-02 (started 2026-04-26, 6/6 active phases complete + 1 SKIPPED, 24/24 plans, 17/17 active REQs satisfied).
**Started:** 2026-04-26
**Closed:** 2026-05-02
**Archive:** [milestones/v1.2-ROADMAP.md](./milestones/v1.2-ROADMAP.md) + [milestones/v1.2-REQUIREMENTS.md](./milestones/v1.2-REQUIREMENTS.md)
**Audit:** [milestones/v1.2-MILESTONE-AUDIT.md](./milestones/v1.2-MILESTONE-AUDIT.md) (status: passed — re-run sau khi Phase 14 merge)
**Tag:** `v1.2` (annotated, local — push pending user)

### Known deferred items at close (12 — see STATE.md "Deferred Items")

- 2 debug sessions root_cause_found (orders-api-500 fix shipped 9dbf114; products-list-500)
- 5 phases với pending UAT scenarios (Phase 06, 07 v1.1 carry-over; 09, 10, 11 v1.2)
- 5 verification gaps human_needed (cùng phases — manual UAT defer per visible-first priority)

### Deferred to v1.3 (intentional scope trim)

- **ACCT-01 wishlist** — Phase 12 SKIPPED scope trim (V5 migration reserved).
- **REV-04 author edit/delete + SEARCH-03 rating + SEARCH-04 URL state + in-stock** — scope trim defer v1.3.
- **PUB-03-lightbox + TEST-02-full E2E suite (8+ tests)** — scope trim defer v1.3.
- **ACCT-04 avatar upload** — Deferred D-08 từ Phase 10 (multipart Thumbnailator scope cut), backlog no phase.
- **Backend hardening invisible** — D1..D17 carry-over (vẫn defer theo visible-first priority).
