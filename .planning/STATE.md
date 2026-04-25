---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Real End-User Experience
current_plan: 05-wave3-in-progress
status: phase5-executing
last_updated: "2026-04-26T01:30:00.000Z"
last_activity: "2026-04-26 -- Phase 5 EXECUTING. Wave 1 (Plan 01 preflight) + Wave 2 (Plan 02 Postgres infra) DONE và đã merge vào develop. Wave 3 (Plans 03-07: 5 services JPA refactor parallel) đang chạy 5 background agents trong worktrees riêng. Critical fix giữa Wave 1↔3: BCrypt hash trong RESEARCH §Decision #6 SAI (encode 'password' không phải 'admin123'); Plan 01 fail-fast caught, Plan 04 PLAN.md đã update sang verified hash $2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu (commit 5dfa026). Wave 4 (Plan 08 integration smoke) + Wave 5 (Plan 09 FE cleanup) chưa bắt đầu."
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 9
  completed_plans: 2
  percent: 22
---

## Current Position

Phase: 5 — Database Foundation (EXECUTING — Wave 3 in progress)
Plan: 9 plans / 5 waves — ✅ Wave 1 (Plan 01) DONE → ✅ Wave 2 (Plan 02) DONE → 🟡 Wave 3 (Plans 03-07 parallel) IN PROGRESS → ⏳ Wave 4 (Plan 08) → ⏳ Wave 5 (Plan 09)
Status: 5 background gsd-executor agents đang chạy worktree-isolated cho Wave 3 (product, user, order, payment, inventory services JPA refactor). Khi resume cần verify worktrees còn tồn tại + merge từng worktree branch về develop.
Last activity: 2026-04-26 01:30Z — Wave 3 spawned 5 parallel agents trên base commit 1ddf478 (= post Wave 2 merge).

## Resume Instructions (Phase 5 Execute)

**Last orchestrator commit on develop**: `1ddf478` (Wave 2 Plan 02 merge: postgres + 5 schemas + docker-compose updates).

**Wave 3 background agent IDs** (status check via `git worktree list`):
- Plan 03 product-service: `ad7b3d0814aedeeb2`
- Plan 04 user-service: `aeef4b198f3b5587b`
- Plan 05 order-service: `a56f0b03c6deb3627`
- Plan 06 payment-service: `a6478588edc9ff3ec`
- Plan 07 inventory-service: `a9f0daf00af104c9b`

**Resume steps khi mở session mới:**
1. `git worktree list` → check 5 Wave 3 worktrees còn tồn tại không. Mỗi worktree có branch `worktree-agent-<id>`.
2. Với mỗi Wave 3 worktree branch:
   - Verify SUMMARY.md tồn tại: `git log --oneline <branch> | grep "05-0[3-7]"` và `ls .planning/phases/05-database-foundation/05-0{3,4,5,6,7}-SUMMARY.md` từ trong worktree (hoặc check `git show <branch>:.planning/phases/05-database-foundation/05-0X-SUMMARY.md`).
   - Pre-merge: snapshot `.planning/STATE.md` + `.planning/ROADMAP.md` (orchestrator owns those — main always wins).
   - Merge: `git merge <branch> --no-ff --no-edit -m "chore: merge executor worktree (Wave 3 — Plan 05-0X)"`.
   - Post-merge: restore STATE.md/ROADMAP.md từ snapshot nếu worktree branch chạm tới chúng.
3. Sau khi merge cả 5: chạy post-merge test gate (mvn test for each service hoặc `docker compose up` smoke). Nếu PASS → cập nhật STATE.md + ROADMAP plan progress cho 03-07.
4. Cleanup: `git worktree remove <path> --force; git branch -D <branch>` cho mỗi.
5. Tiếp tục Wave 4 (Plan 08 integration smoke — full stack up + OpenAPI diff vs baseline + cross-service orphan-row check).
6. Sau Wave 4: Wave 5 (Plan 09 FE mock-data cleanup + flow test).
7. Sau Wave 5: spawn `gsd-verifier` agent để verify phase goal (DB-01..06 success criteria).

**Critical context cho Wave 3 → Wave 4 chain:**
- Cross-service IDs: admin user = `00000000-0000-0000-0000-000000000001`, demo_user = `00000000-0000-0000-0000-000000000002`. Order seed (Plan 05) reference demo_user. Inventory seed (Plan 07) reference `prod-001`..`prod-010` (Plan 03 product seed pattern).
- BCrypt verified hash cho admin: `$2a$10$TMH2spmmPRD90vJz8w5yz.G0o4AR/Hio2RU1yBwjjT1ClTLqF5lFu` (Plan 04 V2 seed). Hash sai trong RESEARCH (`$N9qo8uLOick...`) đã bị Plan 01 caught.
- OpenAPI baselines pre-refactor lưu tại `.planning/phases/05-database-foundation/baseline/openapi-{user,product,order,payment,inventory}-service.json` — Wave 4 capture post versions và diff = 0.

**v1.1 priority rule (giữ nguyên)**: visible-first. Wave 5 chỉ require browse/detail/add-to-cart PASS — checkout/confirmation chỉ render-without-crash, full breakdown defer Phase 8.

Last activity (history): 2026-04-26 — Phase 5 planning complete (research + patterns + 9 plans + revision iteration). ROADMAP SC#5 tightened theo visible-first (defer checkout/confirmation hardening sang Phase 8).

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
