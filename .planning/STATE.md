---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Real End-User Experience
current_plan: null
status: defining-requirements
last_updated: "2026-04-25T23:00:00.000Z"
last_activity: 2026-04-25 -- Milestone v1.1 STARTED. Scope visible-first (3 cluster: Auth real + Admin/Search real data + Cart→Order persistence visible). 11/17 deferred v1.0 items defer tiếp sang v1.2 vì invisible to end-user. Đang ở bước define requirements.
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements cho milestone v1.1
Last activity: 2026-04-25 — Milestone v1.1 Real End-User Experience started

## Resume Cheat-Sheet

- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + `.planning/milestones/v1.0-REQUIREMENTS.md`
- Cross-phase audit: `.planning/v1.0-MILESTONE-AUDIT.md` (17 deferred items phân loại theo cluster)
- Git tag: `v1.0` đã có
- Milestone v1.1 priority: visible-first — defer backend hardening/security/observability invisible
- Next step sau khi /gsd-new-milestone hoàn tất: `/gsd-discuss-phase 5` hoặc `/gsd-plan-phase 5`

## Decisions

- v1.1 scope reshape theo "visible-first" (user feedback): chỉ pick 6/17 deferred items có visible impact (D3 partial, D4, D5, D11 partial, D15, D16). 11/17 còn lại (D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17) defer sang v1.2.
- Phase numbering: continue từ v1.0 (start Phase 5), KHÔNG dùng `--reset-phase-numbers`.
- Skip research step: stack đã locked, patterns auth/CRUD đã có sẵn từ v1.0 foundation.

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose). Codebase là vehicle, không phải production product.
- Foundation v1.0 reuse được: ApiErrorResponse envelope unified, Springdoc + OpenAPI codegen pipeline, CRUD completeness 6 services, validation hardened, FE typed HTTP tier + ApiError dispatcher, middleware route protection, Playwright E2E suite hoạt động.
- v1.1 priority rule: nếu feature invisible to end-user → defer; nếu visible → ship.
