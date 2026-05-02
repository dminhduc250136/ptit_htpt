---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: milestone
status: planning
last_updated: "2026-05-02T13:00:05.695Z"
last_activity: 2026-05-02
progress:
  total_phases: 7
  completed_phases: 6
  total_plans: 26
  completed_plans: 26
  percent: 100
---

## Current Position

Phase: Phase 15 — Public Polish + Milestone Audit ✓ COMPLETE
Plan: 15-04 complete (Wave 3 milestone closure ✓) — Milestone v1.2 SHIPPED (gaps_found verdict)
Status: milestone_complete — v1.2 closed, v1.3 planning pending (run /gsd-new-milestone hoặc /gsd-roadmap)
Last activity: 2026-05-02
Resume file: .planning/milestones/v1.2-MILESTONE-AUDIT.md (review trước khi push tag)

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-27 — Current Milestone: v1.2)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** v1.2 UI/UX Completion — Phase 14: Basic Search Filters (Brand + Price).

## Resume Cheat-Sheet

- Roadmap: `.planning/ROADMAP.md` (7 phases, 9-15)
- Requirements: `.planning/REQUIREMENTS.md` (23 REQs traceability mapped)
- Research: `.planning/research/` (STACK, FEATURES, ARCHITECTURE, PITFALLS, SUMMARY)
- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 archive: `.planning/milestones/v1.1-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 audit: `.planning/milestones/v1.1-MILESTONE-AUDIT.md` (gaps_found — 4 partial)
- Git tags: `v1.0`, `v1.1`
- Project priority: visible-first (memory `feedback_priority.md`)

## Decisions (carry-over từ v1.1 + v1.2 locks)

**Carry-over từ v1.1:**

- Phase numbering tiếp tục KHÔNG reset (v1.2 → Phase 9..15)
- Visible-first priority giữ nguyên
- Backend hardening (D1..D17) defer cho đến khi có triggering event

**Phase 15 locks (2026-05-02 — Wave 0):**

- Hero WebP source: **Unsplash CDN `?fm=webp&q=80`** download (KHÔNG cần cwebp local) — 125KB primary + 65KB secondary
- Badge `.danger` reuse `var(--error-container)` M3 token; `.success/.warning` hard-code hex `#15803d` / `#b45309` (tokens chưa tồn tại)
- DELIVERED order test strategy: **Strategy A (skip-if-no-data)** per D-18 — precedent `order-detail.spec.ts:50-53`
- ReviewSection selectors locked Wave 0: rating `getByRole('button', name: /5 sao/)`, textarea `#review-content`, submit `getByRole('button', name: /gửi đánh giá/i)`

**Phase 15 Wave 2 locks (2026-05-02 — Plan 15-03):**

- ReviewSection KHÔNG phải tab — render inline với eligibility hint `getByText(/chỉ người đã mua sản phẩm này/i)` để detect not-eligible
- AddressPicker dropdown closed-by-default — SMOKE-2 phải click trigger `getByRole('button', { name: /địa chỉ đã lưu/i })` trước khi `[role="option"]` render
- Auto-approved Task 3 checkpoint (auto mode) — manual `docker compose up && npx playwright test e2e/smoke.spec.ts` deferred Plan 15-04 milestone audit gate

**Phase 15 Wave 3 locks + v1.2 closure (2026-05-02 — Plan 15-04):**

- Self-generated audit doc workaround `/gsd-audit-milestone` slash command (subagent constraint per D-21 anticipation) — cross-reference REQUIREMENTS + ROADMAP + Phase 9-15 SUMMARYs + grep code evidence
- Verdict PARTIAL_COMPLETE proceed tag (precedent v1.1 gaps_found tagged anyway)
- SEARCH-01/02 unplanned defer v1.3 — Phase 14 plans ready, recommend execute đầu phase mới
- Tag v1.2 annotated LOCAL pointing commit f267bad — D-22 enforce KHÔNG push automatic
- Auto-approved checkpoints Task 1 + Task 3 (auto mode) — tag creation đã proceed vì D-22 chỉ forbid push, không forbid tag

**v1.2 locks (2026-04-26):**

- Avatar: **multipart upload** + Thumbnailator 0.4.20 + BYTEA inline (NOT URL input)
- Route prefix: **`/profile/*`** consolidation cho tất cả account-management pages
- Reviews eligibility: **verified-buyer only** (cross-service check qua order-svc DELIVERED orders)
- AUTH-06 status: **chưa đóng** (codebase verified `middleware.ts:29` matcher = `/admin/:path*`) → plan đầy đủ Phase 9
- Featured products: top-8 by `createdAt DESC` (KHÔNG `featured BOOLEAN` column)
- Address limit: 10/user với `ADDRESS_LIMIT_EXCEEDED` (422)

## Flyway V-number Reservations (xem ROADMAP.md Pre-Phase Setup)

- user-svc: V3 avatar (P10), V4 addresses (P11), V5 wishlists (P12)
- product-svc: V4 reviews (P13), V5 avg_rating cached (P13), V6 search indexes (P14, optional)
- order-svc: V3 filter index (optional, P9 hoặc skip)

## Deferred Items

Items acknowledged và defer tại milestone close 2026-05-02 (visible-first priority — backend hardening + manual UAT defer):

| Category | Item | Status |
|----------|------|--------|
| debug | orders-api-500 | root_cause_found (fix shipped commit 9dbf114) |
| debug | products-list-500 | root_cause_found |
| uat_gap | Phase 06 06-HUMAN-UAT.md | partial — 6 pending scenarios |
| uat_gap | Phase 07 07-HUMAN-UAT.md | passed — 3 pending scenarios |
| uat_gap | Phase 09 09-HUMAN-UAT.md | partial — 5 pending scenarios |
| uat_gap | Phase 10 10-HUMAN-UAT.md | partial — 5 pending scenarios |
| uat_gap | Phase 11 11-HUMAN-UAT.md | partial — 4 pending scenarios |
| verification_gap | Phase 06 06-VERIFICATION.md | human_needed |
| verification_gap | Phase 07 07-VERIFICATION.md | human_needed |
| verification_gap | Phase 09 09-VERIFICATION.md | human_needed |
| verification_gap | Phase 10 10-VERIFICATION.md | human_needed |
| verification_gap | Phase 11 11-VERIFICATION.md | human_needed |

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 P9-P10 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật (JWT HS256); admin CRUD qua gateway; FE typed services; rhf+zod pattern (P10 lần đầu); Playwright E2E suite (re-baseline P9).
- v1.1 priority rule applied: nếu feature invisible to end-user → defer; nếu visible → ship.
- Phase 10 decisions: hasAvatar=false (D-06, avatar defer D-08); rhfHandleSubmit rename (tránh collision Phase 9); useAuth().login() thay manual localStorage (refinement D-07).
- UAT debt: 5 items Phase 10 cần docker stack (10-HUMAN-UAT.md, status: partial); 4 items Phase 11 cần browser + backend stack (11-HUMAN-UAT.md, status: human_needed).

## Next Steps

1. **User manual review** audit doc `.planning/milestones/v1.2-MILESTONE-AUDIT.md` + tag message → `git push origin v1.2` nếu approve (D-22 lock — KHÔNG auto push).
2. **v1.3 planning bootstrap** — chạy `/gsd-new-milestone` hoặc `/gsd-roadmap`. Top priority backlog: Phase 14 execute (SEARCH-01/02 plans ready), wishlist re-plan (ACCT-01), REV-04, SEARCH-03/04, PUB-03 lightbox, TEST-02 full suite, ACCT-04 avatar.
3. **UAT debt** carry-over: `10-HUMAN-UAT.md` (5 items) + `11-HUMAN-UAT.md` (4 items) + manual docker-stack smoke run Phase 15 + Lighthouse LCP measurement — execute khi có docker stack convenient.
4. **Optional retrospective:** `/gsd-retrospective v1.2` để document lessons-learned (audit self-generation pattern, Phase 14 timing miss, scope trim effectiveness).
