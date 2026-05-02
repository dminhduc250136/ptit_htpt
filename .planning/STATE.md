---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: Catalog Realism & Commerce Intelligence
status: planning
last_updated: "2026-05-02T14:00:00.000Z"
last_activity: 2026-05-02
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
Status: Defining requirements (research → requirements → roadmap)
Last activity: 2026-05-02 — Milestone v1.3 started

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-02 — Current Milestone: v1.3 Catalog Realism & Commerce Intelligence)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** v1.3 — 7 trục scope: seed catalog realistic / storage audit + cart→DB / admin charts + completion / review polish / Claude AI chatbot MVP / order detail items fix / coupon system.

## Resume Cheat-Sheet

- Roadmap: `.planning/ROADMAP.md` (TBD — chưa tạo, sẽ có sau roadmap step)
- Requirements: `.planning/REQUIREMENTS.md` (TBD — chưa tạo)
- Research: `.planning/research/` (sẽ overwrite từ v1.3 — v1.2 research archived → `milestones/v1.2-research/`)
- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 archive: `.planning/milestones/v1.1-ROADMAP.md` + REQUIREMENTS.md (audit gaps_found)
- Milestone v1.2 archive: `.planning/milestones/v1.2-ROADMAP.md` + REQUIREMENTS.md + audit (passed)
- Git tags: `v1.0`, `v1.1`, `v1.2`
- Project priority: visible-first (memory `feedback_priority.md`)
- Language: Vietnamese (memory `feedback_language.md`)

## Decisions (carry-over từ v1.2 + v1.3 locks)

**Carry-over từ v1.2:**

- Phase numbering tiếp tục KHÔNG reset (v1.3 → Phase 16+)
- Visible-first priority giữ nguyên
- Backend hardening (D1..D17) defer cho đến khi có triggering event (ngoại lệ: storage audit có thể surface security issue)
- ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright suite

**v1.3 locks (2026-05-02 — milestone bootstrap):**

- **Seed catalog**: ~100 SP / 5 categories (điện thoại, laptop, chuột, bàn phím, tai nghe), brand realistic (Apple/Samsung/Logitech/Razer/Sony/...), ảnh Unsplash CDN `?fm=webp&q=80` (precedent v1.2 P15)
- **Storage audit scope**: TOÀN FE codebase (grep localStorage + sessionStorage), classify (user-data / UI-pref / auth-token), migrate user-data → DB per user_id
- **Admin charts**: 4 loại — revenue/time, top products, order status pie, signups + low-stock alert
- **Review polish**: REV-04 (author edit/delete) + sort by helpful/newest/rating + admin moderation (hide/approve). KHÔNG helpful votes (defer v1.4).
- **Chatbot AI**: Claude API MVP — customer FAQ + product Q&A + recommendation; admin suggest reply template (manual confirm). Streaming UI + chat history persist DB. KHÔNG agentic tool-use (defer v1.4 nếu cần).
- **Order detail items**: 1 phase debug + fix BE/FE cả `/account/orders/[id]` + `/admin/orders/[id]`
- **Coupon**: 2 loại (% off + fixed amount), min order + expiry + max usage/user, 1 mã/đơn (KHÔNG stack), admin CRUD `/admin/coupons` + FE checkout input
- **AI integration phase**: dùng `/gsd-ai-integration-phase` cho phase chatbot (framework selector + eval planner)
- **Research**: 4 parallel researchers (Stack/Features/Architecture/Pitfalls) chạy ngay sau commit milestone bootstrap

## Flyway V-number Reservations (preliminary — sẽ confirm trong roadmap)

- product-svc: V6 search indexes (optional carry-over P14), **V7 seed-100-products** (P16 seed catalog), **V8 review_sorts_index** (review polish phase, optional)
- order-svc: V3 filter index (optional carry-over), **V4 coupon_redemptions** (coupon phase)
- user-svc: V5 wishlists (reserved v1.4), **V6 chat_sessions + chat_messages** (chatbot phase, hoặc service riêng)
- **NEW shared/coupon-svc**: V1 coupons table (chưa quyết placement — research sẽ xem)
- **NEW chat persistence**: chat_sessions + chat_messages (placement: user-svc hay service mới — research)

## Deferred Items (carry-over từ v1.2)

| Category | Item | Status |
|----------|------|--------|
| debug | orders-api-500 | root_cause_found (fix shipped commit 9dbf114) — closed |
| debug | products-list-500 | root_cause_found |
| uat_gap | Phase 06/07/09/10/11 *-HUMAN-UAT.md | partial / passed — pending scenarios cần manual run docker stack |
| verification_gap | Phase 06/07/09/10/11 *-VERIFICATION.md | human_needed |

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright E2E suite (14 baseline + 4 smoke); reviews verified-buyer cross-service; FilterSidebar pattern; M3 design tokens.
- Visible-first priority applied: feature invisible → defer; visible → ship.
- v1.2 closed 2026-05-02 với verdict PASSED (17/17 active REQs). Tag `v1.2` annotated local pending user push.
- Memory: Vietnamese chat/docs/commits; visible-first priority; dự án thử nghiệm GSD KHÔNG phải PTIT/HTPT student assignment.

## Next Steps

1. **Archive v1.2 research** → `.planning/milestones/v1.2-research/` (giữ history)
2. **Commit milestone bootstrap** — `docs: start milestone v1.3 Catalog Realism & Commerce Intelligence`
3. **Spawn 4 researchers** parallel (Stack/Features/Architecture/Pitfalls) cho v1.3 domain mới
4. **Synthesize** SUMMARY.md
5. **Define REQUIREMENTS.md** với REQ-IDs (categories: SEED, STORAGE, ADMIN, REV, AI, ORDER, COUP)
6. **Spawn roadmapper** continuing từ Phase 16
7. **User approval** roadmap → commit → ready cho `/gsd-discuss-phase 16`
