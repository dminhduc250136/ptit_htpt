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

## v1.2 — UI/UX Completion ✅ SHIPPED (gaps_found)

**Goal:** Hoàn thiện trải nghiệm UI/UX end-user — close residual gaps từ v1.1 + ship core visible features (account / reviews / basic search / public polish), nâng demo flow lên "trải nghiệm UX gần production-ready".

**Scope highlights:**
- Phase 9 — Residual closure (AUTH-06 middleware mở rộng, AUTH-07 password change, UI-02 admin dashboard, TEST-01 Playwright re-baseline)
- Phase 10 — User-svc schema + profile editing (V3 avatar reserved, ACCT-03 fullName/phone)
- Phase 11 — Address book + order history filtering (V4 addresses, ACCT-02/05/06)
- Phase 12 — ~~Wishlist~~ SKIPPED (defer v1.3, V5 migration reserved)
- Phase 13 — Reviews & ratings (V4 reviews + V5 avg_rating cached, REV-01/02/03 verified-buyer)
- Phase 14 — ~~Basic search filters~~ NOT_STARTED (plans ready, defer execute v1.3)
- Phase 15 — Public polish + milestone audit (PUB-01/02/03/04 + TEST-02 smoke E2E)

**Status:** Shipped 2026-05-02 (started 2026-04-26, 5/6 active phases complete + 1 SKIPPED + 1 NOT_STARTED, 23/24 plans, 15/17 active REQs satisfied + 2 pending v1.3).
**Started:** 2026-04-26
**Closed:** 2026-05-02
**Audit:** [milestones/v1.2-MILESTONE-AUDIT.md](./milestones/v1.2-MILESTONE-AUDIT.md) (status: gaps_found — proceeded với SEARCH-01/02 pending v1.3)
**Tag:** `v1.2` (annotated, local — push pending user)

### Known Gaps (deferred v1.3)

- **SEARCH-01 + SEARCH-02 pending** — Phase 14 không execute trong v1.2 timeline. Plans (3) planned full chi tiết, ready cho v1.3 execute.
- **ACCT-01 wishlist** — Phase 12 SKIPPED scope trim (V5 migration reserved).
- **REV-04 author edit/delete + SEARCH-03 rating + SEARCH-04 URL state** — scope trim defer v1.3.
- **PUB-03-lightbox + TEST-02-full** — scope trim defer v1.3.
- **ACCT-04 avatar upload** — Deferred D-08 từ Phase 10 (multipart Thumbnailator scope cut), backlog no phase.
- **Manual UAT debt** — 5 items Phase 10 + 4 items Phase 11 + manual docker smoke run Phase 15 + Lighthouse LCP measurement — auto-approved auto mode.
- **Backend hardening invisible** — D1..D17 carry-over (vẫn defer theo visible-first priority).
