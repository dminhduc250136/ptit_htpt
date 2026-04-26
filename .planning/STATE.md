---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Real End-User Experience
status: phase7-ready-to-execute
last_updated: "2026-04-26T00:00:00.000Z"
last_activity: "2026-04-26 — Phase 7 planning complete. 6 plans in 2 waves. Wave 1: 07-01 (gateway+search), 07-02 (product Flyway+UpsertRequest), 07-03 (user Flyway+PATCH). Wave 2: 07-04 (FE services+ToastProvider), 07-05 (admin products/orders pages), 07-06 (admin users page). All 4 requirements covered (UI-01..04). Resume: /gsd-execute-phase 7."
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 9
  completed_plans: 12
  percent: 50
---

## Current Position

Phase: 6 — Real Auth Flow ✅ COMPLETE (3/3 plans, Wave 1+2 done)
Plan: 3/3 — ✅ Wave 1 (06-01 backend + 06-02 FE services) → ✅ Wave 2 (06-03 FE pages + middleware + /403)
Status: Phase 6 COMPLETE. 17/17 automated checks PASS. 6 human UAT items pending docker smoke (06-HUMAN-UAT.md). Next: Phase 7 Search + Admin Real Data.
Last activity: 2026-04-26 10:00Z — Phase 6 Real Auth Flow DONE. JJWT+BCrypt backend + FE pages wired.

## Resume Instructions (Phase 5 Execute)

**Last orchestrator commit on develop**: `098cc1b` (post merge of Plan 06 + 4 partial worktrees Plans 03/04/05/07).

**Wave 3 status (2026-04-26 04:30Z — RESUMED after 4:20am limit reset):**

- ✅ Plan 06 payment-service — COMPLETE + MERGED (4 commits + SUMMARY)
- 🟡 Plan 03 product-service — partial deps merged, RESUMED via agent `a2c438f03a64ac616` (background)
- 🟡 Plan 04 user-service — partial deps merged, RESUMED via agent `a4f92d7394f404216` (background)
- 🟡 Plan 05 order-service — deps + entity merged, RESUMED via agent `aa4191a483af4dd1c` (background)
- 🟡 Plan 07 inventory-service — partial deps merged, RESUMED via agent `a9fe5aacf93a96951` (background)

**Resume steps NEW SESSION nếu cần:**

1. `git worktree list` → check 4 resume worktrees `agent-{a2c438f03a64ac616, a4f92d7394f404216, aa4191a483af4dd1c, a9fe5aacf93a96951}`.
2. Per worktree: `git -C "$WT" log --oneline 098cc1b..HEAD` để xem commits added; `ls "$WT/.planning/phases/05-database-foundation/05-0X-SUMMARY.md"`.
3. Pre-merge: `cp .planning/STATE.md /tmp/state.bak && cp .planning/ROADMAP.md /tmp/roadmap.bak`.
4. Merge: `git merge worktree-agent-<id> --no-ff --no-edit -m "chore: merge resumed Wave 3 worktree (Plan 05-0X)"`.
5. Restore: `cp /tmp/state.bak .planning/STATE.md && cp /tmp/roadmap.bak .planning/ROADMAP.md`.
6. Try worktree cleanup: `git worktree remove <path> --force; git branch -D <branch>` (may fail if locked — non-blocking).
7. Sau khi merge cả 4: post-merge test gate (`mvn test` mỗi service hoặc `docker compose up` smoke).
8. Continue Wave 4 (Plan 08 integration smoke), Wave 5 (Plan 09 FE), then phase verifier.

**Stale worktrees (locked, non-blocking):** `agent-a8422e9616c369b6c` (W1), `agent-a60a38734b1d51504` (W2), and old Wave 3 batch (`ad7b3d0814aedeeb2`, `aeef4b198f3b5587b`, `a56f0b03c6deb3627`, `a6478588edc9ff3ec`, `a9f0daf00af104c9b`) — đã merge xong, có thể remove sau session khi unlocked.

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

- **2026-04-26 Plan 08:** Gateway two-route-per-service pattern (base + wildcard) — tránh trailing-slash ambiguity trong RewritePath. Fix đồng thời capture group name conflict với $PATH env var (dùng `seg` thay `path`).
- **2026-04-26 Plan 08:** OpenAPI drift documented (không block): order=0 diff; user/product/payment/inventory có expected drift per Plans 03/04/06/07 SUMMARYs. Wave 5 proceeds.
- v1.1 scope reshape theo "visible-first" (user feedback): chỉ pick 6/17 deferred items có visible impact (D3 partial, D4, D5, D11 partial, D15, D16). 11/17 còn lại (D1, D2, D6, D7, D8, D9, D10, D12, D13, D14, D17) defer sang v1.2.
- Phase numbering: continue từ v1.0 (start Phase 5), KHÔNG dùng `--reset-phase-numbers`.
- Skip research step: stack đã locked, patterns auth/CRUD đã có sẵn từ v1.0 foundation.
- **2026-04-25 audit finding**: v1.0 không có DB layer thật. → **Thêm cluster C0 Database Foundation (DB-01..06) vào v1.1** đứng trước C1/C2/C3, dùng Postgres + Flyway + seed từ FE mocks.
- **2026-04-25 roadmap split**: Gộp C1 (Auth backend + FE) vào MỘT phase (Phase 6) thay vì split — 6 REQs là kích thước hợp lý 1 phase, backend/FE cùng feature nên ship cùng. C2/C3 mỗi cluster 1 phase riêng (Phase 7, Phase 8). Tổng 4 phases (vs 5-6 nếu split) giúp giữ granularity standard.
- Phase 5 Plan 09: FE gateway paths corrected (double-path /api/products/products → /api/products); slug lookup via client-side filter (backend ignores slug param)

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose). Codebase là vehicle, không phải production product.
- Foundation v1.0 reuse được: ApiErrorResponse envelope unified, Springdoc + OpenAPI codegen pipeline, CRUD completeness 6 services, validation hardened, FE typed HTTP tier + ApiError dispatcher, middleware route protection, Playwright E2E suite hoạt động.
- v1.1 priority rule: nếu feature invisible to end-user → defer; nếu visible → ship.
- Phase 5 critical path: tất cả phases sau đều depend on Phase 5. Nếu Phase 5 lệch schedule, toàn bộ v1.1 lệch.
- Phase 7/8 có thể parallel sau khi Phase 6 xong (UI-01..04 độc lập với PERSIST-01..03 về codepath, chỉ chung DB).
