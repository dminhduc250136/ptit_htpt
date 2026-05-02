# Tóm Tắt Research — v1.3 Catalog Realism & Commerce Intelligence

**Project:** tmdt-use-gsd (e-commerce Spring Boot + Next.js)
**Domain:** B2C E-commerce microservices — milestone feature additions
**Researched:** 2026-05-02
**Confidence:** HIGH (codebase inspection trực tiếp + Anthropic official docs)

---

## Executive Summary

v1.3 mở rộng nền tảng e-commerce đã solid từ v1.2 theo 7 trục độc lập nhưng có quan hệ phụ thuộc rõ ràng. Codebase phân tích trực tiếp xác nhận hai bug cụ thể: (1) admin order detail dùng hardcoded placeholder string thay vì render items thật, (2) catalog chỉ có ~10 sản phẩm với categories sai domain (electronics/fashion/household), cần reset toàn bộ thành 5 categories tech: điện thoại/laptop/chuột/bàn phím/tai nghe. Cart hiện tại là 100% client-side localStorage — backend đã có `InMemoryCartRepository` và `CartController` nhưng frontend chưa bao giờ gọi, tức là dead code từ v1.0.

Hai additions kỹ thuật mới: `recharts@3.8.1` (admin charts, SVG-based tích hợp tự nhiên Next.js App Router) và `@anthropic-ai/sdk@0.92.0` (AI chatbot qua Next.js API route proxy, không tạo Spring Boot service mới). Toàn bộ logic phức tạp nhất — coupon validation với race condition prevention, chatbot context window management, prompt injection isolation từ reviews — đều có pattern rõ ràng và có thể implement đúng ngay từ đầu nếu follow research. Rủi ro lớn nhất không phải là kỹ thuật mà là ordering: làm chart trước khi có catalog data, hoặc làm coupon trước khi cart migrate sang DB, sẽ gây redundant work.

Consensus 3/4 researchers về phase order: SEED catalog trước tiên vì đây là foundation data cho charts, chatbot context, coupon demo. ORDER-DETAIL fix thứ hai vì là pure FE bug không có dependency. STORAGE/cart-DB thứ ba vì coupon validation cần server-side cart total. Chatbot để cuối vì phức tạp nhất và cần catalog đầy đủ để demo product Q&A có giá trị.

---

## Key Findings

### Stack Additions Cho v1.3

Chỉ cần 2 packages mới. Stack hiện tại (Next.js, Spring Boot, Postgres, Flyway, JWT, Playwright, openapi-typescript) giữ nguyên hoàn toàn.

**Core additions:**
- `recharts@3.8.1`: Admin charts (4 loại: area/bar/pie/line) — SVG-based, React native JSX API, tích hợp sẵn shadcn/ui, `ResponsiveContainer` wrapper, vi-VN locale-safe. Chọn thay Chart.js (canvas + locale issue) và Nivo (bundle nặng hơn, config phức tạp).
- `@anthropic-ai/sdk@0.92.0`: Chatbot qua Next.js API route handler (`app/api/chat/route.ts`) — gọi trực tiếp Anthropic SDK, stream qua native `ReadableStream`, không cần Vercel AI SDK (SDK 6.0 có breaking changes và streaming bug documented). API key chỉ trong `.env.local`, không expose ra FE.
- Không cần thư viện streaming UI: dùng native `fetch` + `ReadableStream` reader (EventSource không hỗ trợ POST body).
- Không cần Spring Boot chat service mới: Next.js route proxy đủ cho MVP, triển khai nhanh hơn ~5x so với tạo microservice riêng.

**Model chatbot:** `claude-haiku-4-5` — cost-optimal cho FAQ/recommendation, TTFT < 1s. Prompt caching bắt buộc từ ngày 1 (`cache_control: { type: 'ephemeral' }` trên system prompt).

### Features Theo 7 Trục

**Must-have (table stakes):**

| Trục | Critical Features |
|------|-------------------|
| SEED | ~100 SP / 5 tech categories, Unsplash WebP CDN URLs thật, brand realistic (Apple/Samsung/Dell/Logitech/Sony), slug unique, price realistic VNĐ |
| STORAGE | Grep classify tất cả localStorage usage; cart migrate → DB; merge guest cart khi login |
| ADMIN | 4 charts (revenue/time, top products, order status pie, signups trend) + low-stock alert; admin order detail items |
| REVIEW | Author edit/delete; sort newest/highest/lowest; admin hide/approve |
| CHATBOT | Customer FAQ + product Q&A + recommendation; streaming UI; chat history persist DB; NO agentic tool-use |
| ORDER-DETAIL | /profile/orders/[id] và /admin/orders/[id] hiển thị full line items với subtotal per line |
| COUPON | % off + fixed amount; expiry + min order + max usage/user; 1 mã/đơn; admin CRUD; FE checkout input với validation message rõ |

**Defer sang v1.4+:**
- Helpful votes trên reviews (cần separate votes table)
- Auto-apply best coupon
- Export CSV từ admin charts
- Real-time WebSocket sync (cart hoặc dashboard)
- Review image upload

**Anti-features cần tránh:**
- Stack multiple coupons (scope lock)
- Agentic tool-use trong chatbot (scope lock)
- IndexedDB cho cart (overkill)
- D3.js custom charts (boilerplate quá nhiều)
- Spring Boot chat service mới (overengineered cho MVP)

### Architecture: Nơi Đặt Từng Component

Research xác nhận trực tiếp từ codebase — không phải suy luận:

**Coupon → order-svc** (confirmed): Coupon validate tại thời điểm tạo order, cần biết cart total → order-svc là nơi duy nhất có đủ context. Flyway V3 order-svc, thêm `coupon_usages` table riêng cho per-user tracking. Gateway route mới `/api/orders/coupons/**` và `/api/orders/admin/coupons/**` phải đứng trước catch-all routes.

**Chat → Next.js API Routes** (confirmed): Streaming response dễ hơn trong Next.js so với Spring WebFlux (project dùng Spring MVC blocking). DB schema `chat_svc` trên cùng Postgres instance. API routes: `/api/chat/sessions` + `/api/chat/sessions/[id]/messages`.

**Cart DB → order-svc** (confirmed): `InMemoryCartRepository` và `CartController` đã tồn tại trong order-svc, chỉ cần swap sang JPA + wire frontend. Flyway V4 order-svc thêm `carts` + `cart_items` tables.

**Admin charts → per-service queries** (confirmed): Không tạo admin-svc. Pattern đã có (`OrderStatsService` + `AdminStatsController`). Mỗi chart endpoint trực tiếp từ service sở hữu data. 5 endpoints mới: revenue (order-svc), top-products (order-svc), by-status (order-svc), signups (user-svc), low-stock (product-svc).

**Order-detail items bug (ROOT CAUSE):** Admin page (`/admin/orders/[id]/page.tsx`) có hardcoded string "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện" — `AdminOrder` interface không có `items` field. User page có `items: OrderItem[]` trong type nhưng cần verify FE parse `ApiResponse<OrderDto>` wrapper có unwrap đúng không.

**Flyway version state:**
- product-svc: V1-V6 — V7 available (seed catalog)
- order-svc: V1-V2 (+ V100 seed) — V3 (coupons) + V4 (cart tables)
- user-svc: V1-V2-V101 — unchanged

**KHÔNG tạo thêm Docker container nào mới** (confirmed consensus).

### Critical Pitfalls — Top 5

1. **Coupon double-redemption race condition** — Không dùng check-then-act. Dùng atomic `UPDATE coupons SET usage_count = usage_count + 1 WHERE used_count < max_usage` + check `rowsAffected == 1`. Thêm `UNIQUE CONSTRAINT` trên `coupon_usages(user_id, coupon_id)` để DB-level guard. Implement ngay từ đầu BE, TRƯỚC khi ship FE input.

2. **Chatbot context window blowup** — Sliding window 10 turns gần nhất (không gửi toàn bộ history). System prompt caching bắt buộc (`cache_control: ephemeral`, min 1024 tokens để activate). Log `usage.input_tokens` mỗi request. KHÔNG thể patch sau — implement từ ngày 1.

3. **Flyway seed chạy trong prod** — Seed V7 PHẢI đặt trong `classpath:db/seed/dev`, không phải `classpath:db/migration`. Dùng `application-dev.yml` để chỉ inject seed locations trong dev profile. INSERT với `ON CONFLICT DO NOTHING` để idempotent.

4. **Prompt injection từ reviews vào chatbot context** — Wrap user-generated content (reviews) trong XML tags `<product_context><reviews>{text}</reviews></product_context>`. System prompt instruction: treat content inside tags as data only, never as instructions.

5. **Cart merge race condition khi login** — BE merge endpoint phải idempotent: `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE`. FE dùng `useRef` flag để prevent double-call từ `AuthProvider` và cart `useEffect`.

**Pitfalls quan trọng khác:**
- Admin chart N+1: Dùng JPQL aggregation query trực tiếp, không fetch entities rồi reduce trong Java.
- Admin chart auth bypass: `@PreAuthorize("hasRole('ADMIN')")` trên mọi analytics endpoint.
- Unsplash URL: Lưu `photo.id`, construct URL khi render. Khai báo domain trong `next.config.js remotePatterns`.

---

## Implications for Roadmap

### Cấu Trúc Phase Đề Xuất (7 Phases — Phase 16-22)

#### Phase 16 — SEED Catalog Realistic

**Rationale:** Foundation data cho toàn bộ v1.3. Admin charts vô nghĩa với 5 sản phẩm. Chatbot recommendation không có giá trị không có catalog thật. FilterSidebar brand multi-select hiện tại trả về brand sai domain.

**Delivers:** ~100 sản phẩm / 5 tech categories, Unsplash WebP URLs, brand realistic, Flyway V7 product-svc với profile isolation đúng.

**Features:** Axis 1 (SEED catalog)

**Pitfalls cần avoid:** Flyway seed trong prod path (V7 phải vào seed/dev profile), Unsplash URL strategy (lưu photo ID, không full URL).

**Dependencies:** Không có — standalone.

**Research flag:** Không cần research phase — pattern Flyway seed đã có từ V100, chỉ extend.

---

#### Phase 17 — ORDER-DETAIL Items Fix

**Rationale:** Pure FE bug fix, không có dependency. Nhanh (1-2 plans). Unblocks admin UX sạch cho Phase 19.

**Delivers:** Admin order detail page hiển thị real items (fix hardcoded placeholder). User order detail verify items render đúng.

**Features:** Axis 6 (ORDER-DETAIL)

**Pitfalls cần avoid:** Verify unwrap `ApiResponse<OrderDto>` — FE `http.ts` có thể không unwrap đúng `data.items`.

**Dependencies:** Không có.

**Research flag:** Không cần — root cause đã xác định (hardcoded string + missing interface field).

---

#### Phase 18 — STORAGE Audit + Cart → DB Migration

**Rationale:** Coupon validation (Phase 20) cần server-side cart total để không trust FE-provided numbers. Cart phải persist trên server trước khi implement coupon. Đây là prerequisite bắt buộc cho coupon system an toàn.

**Delivers:** Flyway V4 order-svc (`carts` + `cart_items`), JPA `CartRepository` thay `InMemoryCartRepository`, FE `services/cart.ts` refactor API-first + localStorage fallback cho guest, storage audit report classify tất cả keys.

**Features:** Axis 2 (STORAGE + cart-DB)

**Pitfalls cần avoid:** Cart merge race condition (idempotent upsert + FE flag), localStorage không clear sau logout.

**Dependencies:** Phase 16 (cần products đủ để add to cart và test).

**Research flag:** Không cần — pattern `InMemoryCartRepository` → JPA đã rõ từ codebase.

---

#### Phase 19 — ADMIN Completion: Charts + Low-Stock Alert

**Rationale:** Cần catalog data thật từ Phase 16 để charts có ý nghĩa. Admin order detail items fix từ Phase 17 đã unblock admin UX hoàn chỉnh. Pattern per-service query đã có precedent.

**Delivers:** 4 chart components (Recharts), 5 aggregate endpoints mới, `npm install recharts@3.8.1`.

**Features:** Axis 3 (ADMIN charts)

**Pitfalls cần avoid:** N+1 aggregation query (JPQL GROUP BY trực tiếp), auth bypass trên analytics endpoints (`@PreAuthorize`), empty state khi không có data.

**Dependencies:** Phase 16 (data thật), Phase 17 (admin order UX).

**Research flag:** Không cần — Recharts pattern standard, endpoints aggregate đơn giản.

---

#### Phase 20 — COUPON System

**Rationale:** Cart phải ở server (Phase 18) để coupon validation có context server-side. Coupon là L-complexity, để sau khi foundation stable.

**Delivers:** Flyway V3 order-svc (coupons + coupon_usages + alter orders), `CouponController`, `CouponRepository`, coupon validate trong `createOrderFromCommand()`, FE `CouponInput` tại checkout + `AdminCouponsPage`.

**Features:** Axis 7 (COUPON system)

**Pitfalls cần avoid:** Double-redemption race condition (atomic UPDATE + UNIQUE constraint), coupon-expire-during-checkout (re-validate tại POST /api/orders), discount > order total (cap min).

**Dependencies:** Phase 18 (cart DB).

**Research flag:** Có thể cần research phase — coupon validation trong Spring Boot transaction với `@Lock` và atomic UPDATE là pattern cần verify cẩn thận.

---

#### Phase 21 — REVIEW Polish (REV-04+)

**Rationale:** Tương đối independent với các trục khác. Carries over từ v1.2 backlog. Đặt sau coupon để không block commerce path.

**Delivers:** Author edit/delete (PATCH + DELETE), admin moderation hide/approve, FE sort controls, Flyway alter reviews table thêm `hidden` column.

**Features:** Axis 4 (REVIEW polish)

**Pitfalls cần avoid:** Sort URL state reset khi back (giữ `?sort=` query param), admin hide confirmation dialog.

**Dependencies:** Phase 16 (cần real products để test reviews thực tế).

**Research flag:** Không cần — pattern CRUD review đã có từ Phase 13, chỉ extend.

---

#### Phase 22 — AI Chatbot Claude API MVP

**Rationale:** Phức tạp nhất — cần catalog đầy đủ (Phase 16) để product Q&A có giá trị, cần UX stable từ các phases trước để demo smooth. Để cuối tránh blocking path.

**Delivers:** `npm install @anthropic-ai/sdk@0.92.0`, Next.js API routes `/api/chat/sessions` + `/api/chat/sessions/[id]/messages`, Postgres schema `chat_svc`, streaming `ChatWidget` floating button + drawer, system prompt với product catalog context injection, sliding window 10 turns + prompt caching.

**Features:** Axis 5 (AI Chatbot)

**Pitfalls cần avoid:** Context window blowup (sliding window + prompt cache bắt buộc), prompt injection từ reviews (XML tag isolation), API key không bao giờ expose ra FE, typing indicator ngay khi submit.

**Dependencies:** Phase 16 (catalog data cho product context).

**Research flag:** CÓ — phase này cần `/gsd-ai-integration-phase` approach. Claude API streaming + SSE + chat session management + context injection là complex integration.

---

### Phase Ordering Rationale

```
16 SEED ───────────┬──► 19 ADMIN charts
17 ORDER-DETAIL ───┘
18 STORAGE/Cart-DB ────► 20 COUPON
16 SEED ────────────────► 21 REVIEW
16 SEED ────────────────► 22 CHATBOT (cuối)
```

- Phase 16 trước: data foundation cho 4/6 phases còn lại
- Phase 17 trước Phase 19: unblock admin UX hoàn chỉnh cho charts page
- Phase 18 trước Phase 20: prerequisite bắt buộc (server-side cart total cho coupon validation)
- Phase 21 sau Phase 20: review independent nhưng cần catalog thật
- Phase 22 cuối: most complex, depends on stable foundation

### Research Flags

**Cần research phase:**
- **Phase 22 (AI Chatbot):** `/gsd-ai-integration-phase` — Claude API streaming + chat session DB + context injection là complex integration.
- **Phase 20 (COUPON):** Spring Boot `@Lock` + atomic UPDATE pattern cần verify với JPA version hiện tại.

**Pattern đã rõ (skip research phase):**
- **Phase 16 (SEED):** Flyway V7 seed profile isolation — extend pattern V100 đã có.
- **Phase 17 (ORDER-DETAIL):** Root cause đã xác định, fix scope nhỏ.
- **Phase 18 (STORAGE):** `InMemoryCartRepository` → JPA swap, pattern rõ từ codebase analysis.
- **Phase 19 (ADMIN charts):** Recharts JSX + JPQL GROUP BY — well-documented.
- **Phase 21 (REVIEW):** Extend Phase 13 patterns (PATCH/DELETE + Flyway alter).

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack additions | HIGH | npm registry verified 2026-05-02, cả 2 packages version confirmed |
| Features / scope | HIGH | 7 trục locked bởi /gsd-new-milestone, codebase inspection xác nhận hiện trạng |
| Architecture placements | HIGH | Codebase analysis trực tiếp; order-svc/Next.js placement confirmed |
| Pitfalls — coupon race | HIGH | Multiple sources + Postgres atomic UPDATE pattern verified |
| Pitfalls — chatbot | HIGH | Anthropic official docs fetched trực tiếp |
| Pitfalls — Flyway prod | HIGH | Codebase V100 pattern đã có precedent |
| Phase ordering | HIGH | Consensus 3/4 researchers + dependency analysis |
| Review polish scope | MEDIUM | `hidden` soft-delete vs hard-delete chưa quyết định cuối |

**Overall confidence:** HIGH

### Open Questions Cần Resolve Trong Requirements Definition

1. **Review delete: soft-delete hay hard-delete?** PITFALLS.md đề cập soft-delete (thêm `deleted` column) nhưng FEATURES.md nói DELETE. Ảnh hưởng Flyway migration và audit trail.

2. **Chat history cho guest user:** Schema `chat_sessions.user_id NOT NULL` (require auth), nhưng FEATURES.md nói "allow guest chat nhưng không persist history". Cần quyết định: guest có streaming không? Có cần session không? Ảnh hưởng API route auth logic.

3. **Coupon per-user limit: max bao nhiêu lần?** Schema có `coupon_usages` table nhưng chưa rõ field `max_uses_per_user`. Clarify: mỗi user được dùng 1 mã tối đa N lần, hay chỉ 1 lần? Ảnh hưởng `UNIQUE CONSTRAINT` design.

4. **Admin charts date range default:** FEATURES.md mention "date range picker" nhưng ARCHITECTURE.md chỉ document `?period=7d`. Confirm: default là 7 hay 30 ngày? Date picker là v1.3 hay defer?

5. **Chat `chat_svc` migration tool:** Next.js không có Flyway. Quyết định: raw SQL init script trong Docker Compose hay migration tool riêng (drizzle/kysely)?

---

## Sources

### Primary (HIGH confidence — codebase inspection + official docs)

- Codebase: `sources/backend/order-service/` — CartEntity, InMemoryCartRepository, OrderCrudService
- Codebase: `sources/frontend/src/app/admin/orders/[id]/page.tsx` — hardcoded placeholder xác nhận
- Codebase: `sources/frontend/src/services/cart.ts` — localStorage-only confirmed
- [Anthropic Prompt Caching Docs](https://platform.claude.com/docs/en/build-with-claude/prompt-caching)
- [Anthropic Mitigate Prompt Injections](https://platform.claude.com/docs/en/test-and-evaluate/strengthen-guardrails/mitigate-jailbreaks)
- [npm recharts@3.8.1](https://www.npmjs.com/package/recharts)
- [npm @anthropic-ai/sdk@0.92.0](https://www.npmjs.com/package/@anthropic-ai/sdk)
- Flyway migrations: order-svc V1-V2, product-svc V1-V6, user-svc V1-V2-V101 (direct inspection)

### Secondary (MEDIUM confidence — community patterns)

- [Recharts vs chart.js vs nivo 2026 — pkgpulse](https://www.pkgpulse.com/guides/recharts-vs-chartjs-vs-nivo-vs-visx-react-charting-2026)
- [Race Condition in Coupon Redemption — jsmon.sh](https://blogs.jsmon.sh/what-is-race-condition-in-coupon-redemption-ways-to-exploit-examples-and-impact/)
- [Solving Race Conditions with Spring JPA — Medium](https://medium.com/@hc07car/solve-race-condition-with-java-jpa-upsert-jpa-lock-b6fc40462340)
- [Building production Claude streaming with Next.js — DEV Community](https://dev.to/bydaewon/building-a-production-ready-claude-streaming-api-with-nextjs-edge-runtime-3e7)
- [Shopping Cart Session-Based vs Database-Backed — Medium](https://medium.com/@sohail_saifi/building-a-shopping-cart-session-based-vs-database-backed-745260091f30)

### Research files chi tiết

- `.planning/research/STACK.md` — stack additions analysis
- `.planning/research/FEATURES.md` — 7-axis feature landscape
- `.planning/research/ARCHITECTURE.md` — integration decisions + codebase inspection
- `.planning/research/PITFALLS.md` — pitfall prevention với phase mapping

---
*Research hoàn thành: 2026-05-02*
*Sẵn sàng cho roadmap: yes*
