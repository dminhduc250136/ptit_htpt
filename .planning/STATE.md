---
gsd_state_version: 1.0
milestone: v1.2
milestone_name: milestone
status: executing
last_updated: "2026-04-27T20:00:00.000Z"
last_activity: 2026-04-27 -- Phase 13 Wave 1 complete — 13-01 JWT claim name + 13-02 order eligibility endpoint
progress:
  total_phases: 6
  completed_phases: 3
  total_plans: 24
  completed_plans: 16
  percent: 88
---

## Current Position

Phase: Phase 13 — Reviews & Ratings
Plan: Wave 1 complete (13-01 ✓, 13-02 ✓) — next: Wave 2 (13-03)
Status: Executing — Wave 2
Last activity: 2026-04-27 -- Wave 1 complete: JWT name claim + order eligibility endpoint
Resume file: .planning/phases/13-reviews-ratings/

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-04-27 — Current Milestone: v1.2)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** v1.2 UI/UX Completion — Phase 11: Address Book + Order History Filtering.

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

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 P9-P10 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật (JWT HS256); admin CRUD qua gateway; FE typed services; rhf+zod pattern (P10 lần đầu); Playwright E2E suite (re-baseline P9).
- v1.1 priority rule applied: nếu feature invisible to end-user → defer; nếu visible → ship.
- Phase 10 decisions: hasAvatar=false (D-06, avatar defer D-08); rhfHandleSubmit rename (tránh collision Phase 9); useAuth().login() thay manual localStorage (refinement D-07).
- UAT debt: 5 items Phase 10 cần docker stack (10-HUMAN-UAT.md, status: partial); 4 items Phase 11 cần browser + backend stack (11-HUMAN-UAT.md, status: human_needed).

## Next Steps

1. Execute Phase 13 — `/gsd-execute-phase 13` (Wave 1: 13-01 + 13-02 parallel → Wave 2: 13-03 → Wave 3: 13-04 checkpoint).
2. Sau Phase 13: Phase 14 Advanced Search → Phase 15 Polish + Audit.
3. UAT debt cần giải quyết: 10-HUMAN-UAT.md (5 items) + 11-HUMAN-UAT.md (4 items) khi có docker stack chạy.
