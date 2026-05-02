---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
last_updated: "2026-05-02T17:42:00.000Z"
last_activity: 2026-05-02 -- Phase 22 Plan 05 complete (customer chat UI: FloatingChatButton + ChatPanel + useChat SSE consumer)
progress:
  total_phases: 7
  completed_phases: 2
  total_plans: 14
  completed_plans: 12
  percent: 86
---

## Current Position

Phase: 22 (AI Chatbot Claude API MVP) — EXECUTING
Plan: 6 of 7 (Plan 01 ✅, Plan 02 ✅, Plan 03 ✅, Plan 04 ✅, Plan 05 ✅)
Status: Executing Phase 22 — Wave 3 in progress (22-05 customer UI ✅); next 22-06 admin suggest-reply UI + 22-07 E2E
Last activity: 2026-05-02 -- Phase 22 Plan 05 complete (customer chat UI: FloatingChatButton + ChatPanel + useChat SSE consumer)

```
Progress: [██████████] 100%
```

## Project Reference

See: `.planning/PROJECT.md` (updated 2026-05-02 — Current Milestone: v1.3 Catalog Realism & Commerce Intelligence)

**Core value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

**Current focus:** Phase 22 — AI Chatbot Claude API MVP

## Resume Cheat-Sheet

- Roadmap: `.planning/ROADMAP.md` (7 phases — Phase 16-22)
- Requirements: `.planning/REQUIREMENTS.md` (27 active REQs, tất cả mapped)
- Research: `.planning/research/SUMMARY.md` (HIGH confidence — codebase inspection trực tiếp)
- Milestone v1.0 archive: `.planning/milestones/v1.0-ROADMAP.md` + REQUIREMENTS.md
- Milestone v1.1 archive: `.planning/milestones/v1.1-ROADMAP.md` + REQUIREMENTS.md (audit gaps_found)
- Milestone v1.2 archive: `.planning/milestones/v1.2-ROADMAP.md` + REQUIREMENTS.md + audit (passed)
- Git tags: `v1.0`, `v1.1`, `v1.2`
- Project priority: visible-first (memory `feedback_priority.md`)
- Language: Vietnamese (memory `feedback_language.md`)

## Performance Metrics

| Milestone | Phases | Plans | REQs | Result |
|-----------|--------|-------|------|--------|
| v1.0 | 4 | 14 | 11/11 | PASSED |
| v1.1 | 4 | 22 | 15/19 SATISFIED + 4 PARTIAL | PASSED (gaps deferred) |
| v1.2 | 6 (+1 SKIP) | 24 | 17/17 | PASSED |
| v1.3 | 7 planned | TBD | 0/27 | In progress |
| Phase 17-s-a-order-detail-items P01 | 5min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P02 | 3min | 2 tasks | 1 file |
| Phase 17-s-a-order-detail-items P03 | 2min | 2 tasks | 2 files |
| Phase 17-s-a-order-detail-items P04 | 4min | 2 tasks | 2 files |
| Phase 22-ai-chatbot P01 | ~25min | 3 tasks | 12 files (9 lib/chat + 3 env) |
| Phase 22-ai-chatbot P02 | ~10min | 1 task | 1 file (route.ts 185 lines) |
| Phase 22-ai-chatbot P03 | ~8min | 2 tasks | 2 files (sessions list + messages history) |
| Phase 22-ai-chatbot P04 | ~3min | 1 task | 1 file (admin suggest-reply route 117 lines) |
| Phase 22-ai-chatbot P05 | ~6min | 2 tasks | 15 files (chat UI: FAB + ChatPanel + useChat hook + 4 child components + services/chat.ts + layout mount) |

## Decisions (active v1.3 locks)

**Carry-over từ v1.2:**

- Phase numbering tiếp tục KHÔNG reset (v1.3 → Phase 16+)
- Visible-first priority giữ nguyên
- Backend hardening (D1..D17) defer cho đến khi có triggering event (ngoại lệ: storage audit có thể surface security issue)
- ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright suite

**v1.3 locks (2026-05-02 — locked từ research + user answers):**

- **Seed catalog**: ~100 SP / 5 categories (điện thoại, laptop, chuột, bàn phím, tai nghe), brand realistic (Apple/Samsung/Logitech/Razer/Sony/...), ảnh Unsplash CDN `?fm=webp&q=80` (precedent v1.2 P15). Flyway V7 product-svc Spring profile `dev`.
- **Storage audit scope**: TOÀN FE codebase (grep localStorage + sessionStorage), classify (user-data / UI-pref / auth-token), migrate user-data → DB per user_id. Cart confirmed localStorage-only (dead code `InMemoryCartRepository` unused).
- **Admin charts**: 4 loại — revenue/time (area), top products (bar), order status pie (donut), signups (line) + low-stock alert `stock < 10`. Stack: `recharts@3.8.1`.
- **Review polish**: REV-04 (author edit/delete = soft-delete `deleted_at`) + sort by newest/rating DESC/ASC + admin moderation (hide/unhide = `hidden BOOLEAN` column). KHÔNG helpful votes (defer v1.4).
- **Coupon**: 2 loại (% off + fixed amount), min order + expiry + max usage, 1 lần/coupon/user (`UNIQUE CONSTRAINT coupon_redemptions(coupon_id, user_id)`), 1 mã/đơn (KHÔNG stack). Admin CRUD `/admin/coupons` + FE checkout input. Race-safe atomic UPDATE. Flyway V3 order-svc.
- **Chatbot AI**: Claude API MVP, model `claude-haiku-4-5`. Customer FAQ + product Q&A + recommendation. Streaming UI (native ReadableStream, không Vercel AI SDK). Chat history persist Postgres `chat_svc` schema (raw pg driver). Prompt caching bắt buộc từ ngày 1. Login required (NOT guest). Sliding window 10 turns. Admin "suggest reply" — manual confirm. KHÔNG agentic tool-use. Stack: `@anthropic-ai/sdk@0.92.0`. Phase này dùng `/gsd-ai-integration-phase`.
- **Order detail bug**: Root cause xác định — admin page có hardcoded string "Chi tiết sản phẩm..."; `AdminOrder` interface thiếu `items[]`. FE fix chính, BE verify DTO đã đúng.
- **Cart→DB placement**: order-svc Flyway V4 (`carts` + `cart_items`). FE `services/cart.ts` refactor API-first. Guest merge idempotent `ON CONFLICT DO UPDATE`.
- **Chat persistence placement**: Next.js API route + raw pg driver trực tiếp `chat_svc` schema. KHÔNG tạo Spring Boot microservice mới.

## Flyway V-number Reservations (confirmed từ research)

| Service | Version | Purpose | Phase |
|---------|---------|---------|-------|
| product-svc | V101 | Seed ~100 sản phẩm trong db/seed-dev/ (Spring profile `dev` only) — APPLIED 2026-05-02 | Phase 16 |
| order-svc | V3 | Coupons + coupon_redemptions tables | Phase 20 |
| order-svc | V4 | Carts + cart_items tables | Phase 18 |
| chat_svc | — | Schema init qua Next.js raw pg driver (không Flyway) | Phase 22 |

*Note: user-svc V5 reserved cho ACCT-01 wishlist (v1.4). product-svc V8 optional review sorts index nếu cần.*

## Deferred Items (carry-over từ v1.2 + v1.3 scope locks)

| Category | Item | Status |
|----------|------|--------|
| debug | products-list-500 | root_cause_found — carry-over |
| uat_gap | Phase 06/07/09/10/11 *-HUMAN-UAT.md | partial / pending manual UAT |
| verification_gap | Phase 06/07/09/10/11 *-VERIFICATION.md | human_needed |
| v1.4+ | ACCT-01 wishlist | SKIPPED v1.2 Phase 12, V5 migration reserved |
| v1.4+ | SEARCH-03/04 rating filter + URL state | scope trim v1.2 |
| v1.4+ | ACCT-04 avatar upload | Deferred D-08 từ Phase 10 |
| v1.4+ | TEST-02-FULL 8+ E2E tests | scope trim v1.2 |
| v1.4+ | COUP-06 coupon stacking | scope lock v1.3 |
| v1.4+ | AI-06 agentic chatbot tool-use | scope lock v1.3 |
| v1.4+ | REV-07 helpful votes | scope lock v1.3 |
| v1.4+ | STORE-04 auth-token httpOnly cookie | visible-first defer |

## Blockers

Không có blocker.

## Accumulated Context

- Project: tmdt-use-gsd — dự án thử nghiệm GSD workflow (Spring Boot microservices + Next.js + API gateway + Docker Compose).
- Foundation v1.0 + v1.1 + v1.2 reuse được: ApiErrorResponse + traceId envelope; Swagger/OpenAPI codegen; Postgres + JPA + Flyway 5 services; auth thật JWT HS256; admin CRUD qua gateway; FE typed services; rhf+zod pattern; Playwright E2E suite (14 baseline + 4 smoke); reviews verified-buyer cross-service; FilterSidebar pattern; M3 design tokens.
- Visible-first priority applied: feature invisible → defer; visible → ship.
- v1.2 closed 2026-05-02 với verdict PASSED (17/17 active REQs). Tag `v1.2` annotated local.
- Memory: Vietnamese chat/docs/commits; visible-first priority; dự án thử nghiệm GSD KHÔNG phải PTIT/HTPT assignment.
- Pitfall quan trọng v1.3: coupon double-redemption (atomic UPDATE + UNIQUE constraint); chatbot context window blowup (sliding 10 turns + prompt cache); Flyway seed prod isolation (V7 vào seed/dev path); cart merge race condition (idempotent upsert + FE useRef flag); prompt injection từ reviews (XML tag isolation).

## Next Steps

1. ✅ Phase 16 COMPLETE (3/3 plans, SEED-01..04 satisfied artifacts-side; manual UAT defer)
2. `/gsd-verify-work` — chạy 16-VERIFICATION.md sections (smoke SQL + prod negative + add-to-cart + Playwright + UI walkthrough)
3. `/gsd-plan-phase 17` → Execute Phase 17: Sửa Order Detail Items
4. `/gsd-plan-phase 18` → Execute Phase 18: Kiểm Toán Storage + Cart→DB
5. `/gsd-plan-phase 19` → Execute Phase 19: Admin Charts + Low-Stock
6. `/gsd-plan-phase 20` → Execute Phase 20: Hệ Thống Coupon
7. `/gsd-plan-phase 21` → Execute Phase 21: Hoàn Thiện Reviews
8. `/gsd-ai-integration-phase 22` → Execute Phase 22: AI Chatbot Claude API MVP (dùng AI integration workflow thay plan-phase chuẩn)

**Planned Phase:** 22 (AI Chatbot Claude API MVP) — 7 plans — 2026-05-02T16:04:22.444Z
