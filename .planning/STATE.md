---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Real End-User Experience
current_plan: null
status: phase5-planned
last_updated: "2026-04-26T00:00:00.000Z"
last_activity: 2026-04-26 -- Phase 5 đã PLANNED. 9 PLAN.md files tạo trong 5 waves (preflight → Postgres infra → 5 services parallel refactor → integration smoke → FE cleanup). Plan checker PASSED iteration 2 sau khi tighten Plan 09 + add cross-service ID constants + smoke verifies. Sẵn sàng /gsd-execute-phase 5.
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 9
  completed_plans: 0
  percent: 0
---

## Current Position

Phase: 5 — Database Foundation (planned, ready to execute)
Plan: 9 plans / 5 waves — Wave 1 preflight → Wave 2 Postgres infra → Wave 3 5 services parallel → Wave 4 integration smoke → Wave 5 FE cleanup
Status: PLAN.md files đã verified PASS, sẵn sàng `/gsd-execute-phase 5`
Last activity: 2026-04-26 — Phase 5 planning complete (research + patterns + 9 plans + revision iteration). ROADMAP SC#5 tightened theo visible-first (defer checkout/confirmation hardening sang Phase 8).

## Resume Cheat-Sheet

- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + `.planning/milestones/v1.0-REQUIREMENTS.md`
- Cross-phase audit v1.0: `.planning/v1.0-MILESTONE-AUDIT.md` (17 deferred items, 11 → v1.2)
- Git tag: `v1.0` đã có
- Milestone v1.1 priority: visible-first — defer backend hardening/security/observability invisible
- Roadmap v1.1: `.planning/ROADMAP.md` (Phase 5-8) — 19 REQs full coverage
- Next step: `/gsd-plan-phase 5` (Database Foundation — block toàn bộ v1.1)

## Phase Map (v1.1)

| Phase | Goal | REQs | Depends on |
|---|---|---|---|
| 5 — Database Foundation | Postgres + JPA + Flyway + seed từ FE mocks; gateway round-trip qua FE trả seeded data thật | DB-01..06 | — |
| 6 — Real Auth Flow | Backend `/auth/*` thật + JWT + FE form gỡ mock + session persist | AUTH-01..06 | Phase 5 |
| 7 — Search + Admin Real Data | `/search` rewire + admin/products/orders/users CRUD thật | UI-01..04 | Phase 5, 6 |
| 8 — Cart → Order Persistence Visible | ProductEntity.stock persist + OrderEntity per-item rows + FE detail full breakdown | PERSIST-01..03 | Phase 5, 6 |

## Decisions

- v1.1 scope reshape theo "visible-first" (user feedback): chỉ pick 6/17 deferred items có visible impact (D3 partial, D4, D5, D11 partial, D15, D16). 11/17 còn lại (D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17) defer sang v1.2.
- Phase numbering: continue từ v1.0 (start Phase 5), KHÔNG dùng `--reset-phase-numbers`.
- Skip research step: stack đã locked, patterns auth/CRUD đã có sẵn từ v1.0 foundation.
- **2026-04-25 audit finding**: v1.0 không có DB layer thật. → **Thêm cluster C0 Database Foundation (DB-01..06) vào v1.1** đứng trước C1/C2/C3, dùng Postgres + Flyway + seed từ FE mocks.
- **2026-04-25 roadmap split**: Gộp C1 (Auth backend + FE) vào MỘT phase (Phase 6) thay vì split — 6 REQs là kích thước hợp lý 1 phase, backend/FE cùng feature nên ship cùng. C2/C3 mỗi cluster 1 phase riêng (Phase 7, Phase 8). Tổng 4 phases (vs 5-6 nếu split) giúp giữ granularity standard.

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose). Codebase là vehicle, không phải production product.
- Foundation v1.0 reuse được: ApiErrorResponse envelope unified, Springdoc + OpenAPI codegen pipeline, CRUD completeness 6 services, validation hardened, FE typed HTTP tier + ApiError dispatcher, middleware route protection, Playwright E2E suite hoạt động.
- v1.1 priority rule: nếu feature invisible to end-user → defer; nếu visible → ship.
- Phase 5 critical path: tất cả phases sau đều depend on Phase 5. Nếu Phase 5 lệch schedule, toàn bộ v1.1 lệch.
- Phase 7/8 có thể parallel sau khi Phase 6 xong (UI-01..04 độc lập với PERSIST-01..03 về codepath, chỉ chung DB).
