# Roadmap

## Milestones

- ✅ **v1.0 MVP Stabilization** — Phases 1-4 (shipped 2026-04-25) — [archive](./milestones/v1.0-ROADMAP.md)
- ✅ **v1.1 Real End-User Experience** — Phases 5-8 (shipped 2026-04-26) — [archive](./milestones/v1.1-ROADMAP.md)
- ✅ **v1.2 UI/UX Completion** — Phases 9-15 (shipped 2026-05-02) — [archive](./milestones/v1.2-ROADMAP.md)
- 📋 **v1.3 (TBD)** — chạy `/gsd-new-milestone` để bootstrap

## Phases

<details>
<summary>✅ v1.0 MVP Stabilization (Phases 1-4) — SHIPPED 2026-04-25</summary>

- [x] Phase 1: API surface + DTO standardization
- [x] Phase 2: Swagger/OpenAPI gateway + services
- [x] Phase 3: Error envelope + status code alignment
- [x] Phase 4: Frontend-backend contract validation (Playwright 12/12 PASS)

See [milestones/v1.0-ROADMAP.md](./milestones/v1.0-ROADMAP.md) for full details.

</details>

<details>
<summary>✅ v1.1 Real End-User Experience (Phases 5-8) — SHIPPED 2026-04-26</summary>

- [x] Phase 5: Database Foundation (Postgres + JPA + Flyway, 9/9 plans)
- [x] Phase 6: Real Auth Flow (JWT HS256, 3/3 plans)
- [x] Phase 7: Search + Admin Real Data (6/6 plans)
- [x] Phase 8: Cart → Order Persistence (4/4 plans)

See [milestones/v1.1-ROADMAP.md](./milestones/v1.1-ROADMAP.md) for full details.

</details>

<details>
<summary>✅ v1.2 UI/UX Completion (Phases 9-15) — SHIPPED 2026-05-02</summary>

- [x] Phase 9: Residual Closure & Verification (5/5 plans) — completed 2026-04-27
- [x] Phase 10: User-Svc Schema + Profile Editing (3/3 plans) — completed 2026-04-27
- [x] Phase 11: Address Book + Order History Filtering (6/6 plans) — completed 2026-04-27
- [~] Phase 12: ~~Wishlist~~ SKIPPED (scope trim 2026-04-27, defer v1.3)
- [x] Phase 13: Reviews & Ratings (4/4 plans) — completed 2026-04-27
- [x] Phase 14: Basic Search Filters (3/3 plans) — completed 2026-05-02
- [x] Phase 15: Public Polish + Milestone Audit (5/5 plans) — completed 2026-05-02

See [milestones/v1.2-ROADMAP.md](./milestones/v1.2-ROADMAP.md) for full details.

</details>

### 📋 v1.3 (TBD)

Chưa có phases — chạy `/gsd-new-milestone` để bootstrap milestone v1.3.

**Top backlog candidates** (deferred từ v1.2):

- ACCT-01 wishlist (Phase 12 plans cần re-plan với V5 migration)
- REV-04 author edit/delete reviews
- SEARCH-03 rating filter + SEARCH-04 URL state + in-stock
- PUB-03 lightbox + axe-core a11y gate
- TEST-02 full E2E suite (8+ tests)
- ACCT-04 avatar upload (multipart + Thumbnailator)
- Backend hardening D1..D17 (revisit khi có triggering event)

## Progress

| Phase | Milestone | Plans | Status | Completed |
|-------|-----------|-------|--------|-----------|
| 1-4 | v1.0 | 14/14 | ✅ Complete | 2026-04-25 |
| 5-8 | v1.1 | 22/22 | ✅ Complete | 2026-04-26 |
| 9 — Residual Closure | v1.2 | 5/5 | ✅ Complete | 2026-04-27 |
| 10 — Profile Editing | v1.2 | 3/3 | ✅ Complete | 2026-04-27 |
| 11 — Address Book + Order Filter | v1.2 | 6/6 | ✅ Complete | 2026-04-27 |
| 12 — Wishlist | v1.2 | — | ~~SKIPPED~~ | — |
| 13 — Reviews & Ratings | v1.2 | 4/4 | ✅ Complete | 2026-04-27 |
| 14 — Basic Search Filters | v1.2 | 3/3 | ✅ Complete | 2026-05-02 |
| 15 — Public Polish + Audit | v1.2 | 5/5 | ✅ Complete | 2026-05-02 |
