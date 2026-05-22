# Requirements — Milestone v1.3 (Catalog Realism & Commerce Intelligence)

**Milestone:** v1.3
**Started:** 2026-05-02
**Status:** Roadmap complete — 40/40 REQs mapped (PAY + MAIL thêm 2026-05-22)
**Phase numbering:** tiếp tục từ Phase 16 (KHÔNG reset)

---

## Scope Summary

10 trục bổ sung cho tmdt-use-gsd e-commerce demo (Spring Boot microservices + Next.js):

1. **SEED** — Catalog 100 SP / 5 categories realistic + ảnh Unsplash WebP
2. **STORE** — Audit toàn FE storage + cart→DB migration
3. **ADMIN** — 4 charts analytics + low-stock alert + admin order detail items
4. **REV** — Review polish (REV-04 author edit/delete + sort + admin moderation)
5. **AI** — Claude API chatbot MVP (customer + admin suggest reply)
6. **ORDER** — Order detail items fix BE/FE cả user + admin
7. **COUP** — Coupon system (% off + fixed + admin CRUD)
8. **MQ** — RabbitMQ async messaging cho luồng OrderPlaced (producer + 2 consumer + retry + DLQ + idempotency)
9. **PAY** — Tích hợp thanh toán VNPay sandbox (checkout redirect + IPN callback verify chữ ký + cập nhật trạng thái đơn)
10. **MAIL** — Gửi email thật qua SMTP (xác thực tài khoản + xác nhận đơn + cập nhật trạng thái đơn)

**Locks (từ research + user answers):**
- Review delete = **soft-delete** (column `deleted_at` hoặc `hidden`); admin vẫn xem
- Coupon per-user usage = **1 lần/coupon/user** (unique constraint)
- Chatbot = **login required** (KHÔNG guest); chat_sessions.user_id NOT NULL
- Admin charts date range = **default 30 ngày + dropdown 7d/30d/90d/all**
- Stack mới: `recharts@3.8.1` + `@anthropic-ai/sdk@0.92.0` (Next.js API route proxy, KHÔNG Spring Boot chat-svc)
- Coupon table = `order-svc` Flyway V3
- Chat persistence = Next.js API route + Postgres `chat_svc` schema (raw pg driver)
- Cart→DB = order-svc V4 (carts + cart_items)

---

## v1.3 Requirements (Active)

### SEED — Realistic Catalog

- [x] **SEED-01** — Reset product categories sang 5 đúng domain: điện thoại / laptop / chuột / bàn phím / tai nghe (xóa categories cũ sai domain: fashion/household/books/cosmetics) ✅ 2026-05-02 (Plan 16-02)
- [x] **SEED-02** — Seed ~100 sản phẩm realistic distributed ~20/category với brand thực tế (Apple, Samsung, Xiaomi, Logitech, Razer, Sony, ASUS, Dell, HP, Lenovo, Steelseries, ...). Mỗi SP có `name`, `brand`, `price`, `original_price` (gạch giá), `description`, `stock` ✅ 2026-05-02 (Plan 16-02 — 100 SP / 25 brands)
- [x] **SEED-03** — Mỗi SP có ảnh từ Unsplash CDN format `?fm=webp&q=80&w=800` (precedent v1.2 P15). KHÔNG hot-link breakage — verify URLs ổn định khi seed. ✅ 2026-05-02 (Plan 16-02 — 100 unique IDs từ IMAGES.csv; runtime URL stability verify trong Plan 16-03)
- [x] **SEED-04** — Flyway profile isolation: dev seed `V101+` tách khỏi baseline `V1-V7`. Spring profile `dev` mới chạy seed migration (production profile skip). ✅ 2026-05-02 (Plan 16-02 — V101 tại db/seed-dev/)

### STORE — Storage Audit + Cart→DB

- [x] **STORE-01** — Audit toàn FE codebase: grep `localStorage` + `sessionStorage`, classify mọi usage thành 3 nhóm: (a) user-data persist sang DB cần thiết, (b) UI preference giữ localStorage OK, (c) auth-token reviewable security. Output báo cáo trong phase SUMMARY.md. **COMPLETED Phase 18 Plan 06 — 18-SUMMARY.md §Storage Audit Report.**
- [x] **STORE-02** — Migrate cart từ `localStorage['cart']` sang DB. Order-svc V4 tạo `carts` + `cart_items` tables (per `user_id`). FE `services/cart.ts` chuyển sang API calls. Idempotent merge endpoint (`ON CONFLICT DO UPDATE`) khi guest login. **COMPLETED Phase 18 Plans 01-05.**
- [x] **STORE-03** — Migrate các user-data leak khác phát hiện trong STORE-01 (recently viewed / search history / wishlist nếu có / etc.) sang DB hoặc giải thích lý do giữ lại. **COMPLETED Phase 18 Plan 06 — no additional leaks found beyond cart. 18-SUMMARY.md §STORE-03 Disposition.**

### ADMIN — Charts + Low-Stock + Order Detail Items

- [x] **ADMIN-01** — Revenue chart theo thời gian (line/area chart). Aggregate `orders` DELIVERED status, dropdown 7d/30d/90d/all. Default 30d.
- [x] **ADMIN-02** — Top products bán chạy (bar chart). Top-10 by quantity sold trong window đã chọn. Cùng date dropdown.
- [x] **ADMIN-03** — Order status distribution (pie/donut chart). Counts theo pending/confirmed/shipped/delivered/cancelled. Cùng date dropdown.
- [x] **ADMIN-04** — User signups theo thời gian (line chart). Daily new user count.
- [x] **ADMIN-05** — Low-stock alert: list/banner các SP có `stock < 10` (threshold configurable trong code). Hiển thị trên admin dashboard hoặc trang riêng.
- [x] **ADMIN-06
** — Admin order detail items fix: `/admin/orders/[id]` hiển thị full line items (image / name / brand / price / qty / subtotal) — hiện đang là hardcoded placeholder string "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện". `AdminOrder` interface cần thêm `items[]`, BE DTO check.

### REV — Review Polish

- [x] **REV-04
** — Author edit/delete review của mình. Edit chỉ chủ review hoặc 24h sau publish (configurable). Delete = soft-delete (`deleted_at` column), avg_rating recalc loại bỏ deleted. Admin vẫn xem.
- [x] **REV-05
** — Sort review list by `helpful` (defer — KHÔNG có votes nên dùng `created_at DESC` làm fallback) / `newest` / `rating DESC` / `rating ASC`. Dropdown FE + BE query param.
- [x] **REV-06
** — Admin moderation: `/admin/reviews` screen list + filter (visible/hidden) + actions hide/unhide/delete. Hide = `hidden BOOLEAN` column → user không thấy nhưng admin vẫn list được.

### AI — Claude API Chatbot MVP

- [ ] **AI-01** — Customer chatbot UI: floating widget button góc dưới phải mọi trang. Click mở modal chat. Streaming response (token-by-token), Markdown render cơ bản. Login required (guest thấy nút "đăng nhập để chat").
- [ ] **AI-02** — System prompt thiết kế: vai trò "trợ lý mua sắm tmdt-use-gsd", domain electronics, ngôn ngữ Vietnamese, có thể trả lời FAQ + Q&A sản phẩm + recommend từ catalog. **Prompt caching** với `cache_control: { type: 'ephemeral' }` từ ngày 1.
- [ ] **AI-03** — Context injection: top-N sản phẩm liên quan (semantic match đơn giản qua keyword/category) inject vào user message với XML tag `<product_context>...</product_context>` để chống prompt injection từ user-generated content.
- [ ] **AI-04** — Chat history persist DB: schema `chat_sessions` (id, user_id, title, created_at, updated_at) + `chat_messages` (id, session_id, role, content, created_at). Sliding window 10 turns gần nhất gửi vào API. User xem được lịch sử sessions cũ.
- [x] **AI-05** — Admin "suggest reply" template: trong `/admin/orders/[id]`, button "AI suggest reply" generate gợi ý phản hồi customer dựa trên order context. Admin manual review + send. KHÔNG auto-confirm order. ✅ 2026-05-02 (Phase 22 P04 server endpoint + P06 SuggestReplyModal UI)

### ORDER — Order Detail Items Fix

- [x] **ORDER-01
** — User order detail `/account/orders/[id]` hiển thị full line items. Verify BE `findByIdWithItems()` đã return đúng (research: BE OK), fix FE render nếu thiếu DTO mapping.
- [x] **ORDER-02** — Admin order detail items đã cover trong ADMIN-06
. Cross-reference (KHÔNG duplicate REQ).

*Note: ORDER-02 là pointer, count = 1 unique REQ trong category này.*

### COUP — Coupon System

- [ ] **COUP-01** — Coupon table schema: `coupons` (id, code, type [PERCENT|FIXED], value, min_order_amount, max_total_uses, used_count, expires_at, active, created_at). `coupon_redemptions` (coupon_id, user_id, order_id, redeemed_at, UNIQUE(coupon_id, user_id) cho 1-lần/user). Both ở `order-svc` Flyway V3.
- [ ] **COUP-02** — Admin CRUD `/admin/coupons` screen: list + create/edit/disable/delete coupon. Form validate type + value + expiry + min_order. Soft-disable = `active=false`.
- [ ] **COUP-03** — FE checkout input mã giảm giá: text input + nút "Áp dụng". Validate qua `POST /api/orders/coupons/validate` (cart total + user_id + code) → return discount preview. Re-validate tại order create để chống TOCTOU.
- [ ] **COUP-04** — Atomic redemption: `UPDATE coupons SET used_count = used_count + 1 WHERE id = ? AND active = true AND (max_total_uses IS NULL OR used_count < max_total_uses) AND expires_at > NOW()` + check `rows_affected = 1`. Insert vào `coupon_redemptions` cùng transaction với order create. Race-safe.
- [ ] **COUP-05** — Order display: `/account/orders/[id]` + `/admin/orders/[id]` hiển thị coupon code + discount amount nếu order có coupon (lookup qua `coupon_redemptions.order_id`).

### MQ — Message Queue (RabbitMQ Integration)

- [ ] **MQ-01** — RabbitMQ container chạy trong docker-compose.yml với Management UI tại http://localhost:15672 (user guest/pass guest cho dev). Image `rabbitmq:3-management`. Healthcheck `rabbitmq-diagnostics ping`. Per D-01, D-02.
- [x] **MQ-02** — order-service publish event `OrderPlaced` (routing key `order.placed`) vào topic exchange `order.events` (durable=true) SAU KHI DB commit, với Publisher Confirms bật (`publisher-confirm-type=correlated` + `publisher-returns=true`). JSON envelope `{eventId, eventType, occurredAt, traceId, payload}`. Per D-01, D-03, D-04, D-15. ✅ 2026-05-20 (Phase 23 Plan 03)
- [ ] **MQ-03** — inventory-service consume từ queue `inventory.order-events` (bind `order.#`), trừ `InventoryEntity.quantity` atomic + ghi `inventory_svc.stock_ledger` entry, idempotent qua bảng `inventory_svc.processed_events` (PK event_id, INSERT ... ON CONFLICT DO NOTHING). Per D-06, D-10.
- [x] **MQ-04** — notification-service consume từ queue `notification.order-events` (bind `order.#`), ghi `notification_svc.dispatch_log` (status=SENT, channel=email, không gửi SMTP thật), idempotent qua `notification_svc.processed_events`. Per D-14.
- [ ] **MQ-05** — Consumer throw exception → retry 3 lần exponential backoff (1s → 2s → 4s, config `spring.rabbitmq.listener.simple.retry.*`); `PermanentMessageException` → reject ngay (0 retry) vào DLQ `order-events.dlq` qua DLX `order.dlx` (direct). Verify được message trong DLQ qua Management UI. traceId propagate qua header `X-Trace-Id` xuyên 3 service. Per D-07, D-08, D-09, D-16, D-17.

### PAY — VNPay Sandbox Payment Integration

- [ ] **PAY-01** — Checkout payment method: tại `/checkout` thêm lựa chọn phương thức "Thanh toán qua VNPay" (bên cạnh COD nếu có). Khi chọn VNPay + đặt hàng → order tạo với `payment_method=VNPAY` + `payment_status=PENDING`, BE build URL thanh toán VNPay sandbox (params `vnp_Amount`, `vnp_TxnRef`, `vnp_OrderInfo`, `vnp_ReturnUrl`, ... + `vnp_SecureHash` HMAC SHA512) và FE redirect khách sang cổng VNPay.
- [ ] **PAY-02** — Return URL: VNPay redirect khách về `vnp_ReturnUrl` của ứng dụng sau thanh toán. FE render trang kết quả rõ ràng (thành công / thất bại / huỷ) dựa trên `vnp_ResponseCode`. KHÔNG cập nhật trạng thái đơn dựa trên return URL (return URL chỉ để hiển thị — nguồn sự thật là IPN).
- [ ] **PAY-03** — IPN callback (server-to-server): BE expose endpoint nhận IPN từ VNPay, verify `vnp_SecureHash` (HMAC SHA512 với secret), so khớp `vnp_Amount` với số tiền đơn, so khớp `vnp_TxnRef` với order. Hợp lệ + `vnp_ResponseCode=00` → cập nhật `payment_status=PAID` + lưu `vnp_TransactionNo`; thất bại → `payment_status=FAILED`. Idempotent khi VNPay gửi lại cùng giao dịch. Trả response đúng format VNPay yêu cầu.
- [ ] **PAY-04** — Order display: `/account/orders/[id]` + `/admin/orders/[id]` hiển thị phương thức thanh toán + trạng thái thanh toán (PENDING/PAID/FAILED) + mã giao dịch VNPay nếu có. Chữ ký sai hoặc số tiền lệch → giao dịch bị từ chối và ghi log audit.

### MAIL — Real Email Delivery (SMTP)

- [x] **MAIL-01
** — Cấu hình SMTP qua biến môi trường: host, port, username, password/app-password, from-address đọc hoàn toàn từ env (KHÔNG hardcode credential trong source). Thiếu env → service vẫn khởi động được và log cảnh báo (graceful degradation, không crash). SMTP Gmail (App Password) — account do user cấp qua env.
- [x] **MAIL-02
** — Email xác thực tài khoản: đăng ký tài khoản mới → gửi email xác minh tới hộp thư thật, link/token xác minh → tài khoản chuyển trạng thái verified. Yêu cầu reset mật khẩu → gửi email chứa link/token reset. Gắn vào user-service/auth flow.
- [x] **MAIL-03
** — Email xác nhận đơn hàng: khi đặt hàng thành công, notification-service consume event `OrderPlaced` (từ RabbitMQ Phase 23) → gửi email xác nhận (mã đơn, danh sách sản phẩm, tổng tiền) tới email khách. Render bằng template tiếng Việt.
- [x] **MAIL-04
** — Email cập nhật trạng thái đơn: khi trạng thái đơn thay đổi (shipped / delivered / cancelled) → gửi email cập nhật tương ứng tới khách. Gửi bất đồng bộ (không chặn request chính).

---

## Future Requirements (Defer v1.4+)

- **AI-06** — Agentic chatbot (tool use: browse catalog / add to cart / checkout cho user)
- **AI-07** — Admin auto-confirm order rule-based (đủ stock + payment OK)
- **REV-07** — Helpful votes (upvote/downvote review) — cần V_ migration mới
- **STORE-04** — Auth-token migration localStorage → httpOnly cookie (security hardening — visible-first defer)
- **COUP-06** — Coupon stacking (1 product-level + 1 cart-level)
- **COUP-07** — Free shipping coupon (cần shipping fee logic)
- **ADMIN-07** — Custom date picker (from-to) thay dropdown preset
- **ACCT-01** — Wishlist (carry-over từ v1.2 Phase 12 SKIPPED)
- **SEARCH-03/04** — Rating filter + URL state + in-stock + clear-all (carry-over)
- **PUB-03** — Lightbox + axe-core a11y gate (carry-over)
- **TEST-02-FULL** — Full E2E suite 8+ tests (carry-over, smoke 4 đã có)
- **ACCT-04** — Avatar upload (multipart Thumbnailator, carry-over D-08)
- **ORDER-03+** — Multi-step checkout (Shipping → Payment → Review)
- **ORDER-04** — Recently viewed / Related products (rule-based recommendations)

---

## Out of Scope (v1.3 Explicit Exclusions)

- ~~**Real payment gateway integration** — mock đủ cho dự án thử nghiệm GSD~~ — **REVISED 2026-05-22:** đưa vào scope qua Phase 26 (VNPay **sandbox** — môi trường test, không phải merchant production thật; đáp ứng yêu cầu tích hợp cổng thanh toán mà vẫn an toàn cho dự án demo)
- **Production-grade infrastructure** — load balancing, K8s, failover (project-wide lock)
- **Mobile app** — web-only (project-wide lock)
- **Real-time WebSockets** — polling/SSE đủ (project-wide lock)
- **Backend hardening D1..D17 invisible** — visible-first priority giữ nguyên (carry-over từ v1.0/v1.1/v1.2). NGOẠI LỆ: nếu STORE-01 audit phát hiện security leak nghiêm trọng (vd. JWT in localStorage XSS-able), surface để user quyết định ad-hoc.
- **Helpful votes trên reviews** — defer v1.4 per user answer
- **Coupon stacking** — 1 mã/đơn lock per user answer
- **Free shipping coupon** — defer v1.4 (cần shipping logic)
- **Agentic chatbot tool-use** — MVP scope strictly customer FAQ + Q&A + recommendation per user answer
- **Auto-confirm order AI** — admin chỉ "suggest reply", manual confirm per user answer
- **Guest chatbot** — login required per user answer
- **Admin user detail / admin product detail expansion** — KHÔNG có evidence missing screen quan trọng (admin orders chỉ thiếu items array, đã cover ADMIN-06)
- **i18n** — Vietnamese only

---

## Traceability

| REQ-ID | Phase | Plan(s) | Status |
|--------|-------|---------|--------|
| SEED-01 | Phase 16 | 16-02 | Satisfied (2026-05-02) |
| SEED-02 | Phase 16 | 16-01, 16-02 | Satisfied (2026-05-02) |
| SEED-03 | Phase 16 | 16-01, 16-02, 16-03 | Satisfied (2026-05-02 — runtime verify defer 16-VERIFICATION.md §4) |
| SEED-04 | Phase 16 | 16-02, 16-03 | Satisfied (2026-05-02 — prod negative test defer 16-VERIFICATION.md §2) |
| ORDER-01 | Phase 17 | — | Active |
| ADMIN-06 | Phase 17 | — | Active |
| STORE-01 | Phase 18 | 06 | COMPLETED |
| STORE-02 | Phase 18 | 01-05 | COMPLETED |
| STORE-03 | Phase 18 | 06 | COMPLETED |
| ADMIN-01 | Phase 19 | 01, 04 | COMPLETED |
| ADMIN-02 | Phase 19 | 01, 04 | COMPLETED |
| ADMIN-03 | Phase 19 | 01, 04 | COMPLETED |
| ADMIN-04 | Phase 19 | 02, 04 | COMPLETED |
| ADMIN-05 | Phase 19 | 03, 04 | COMPLETED |
| COUP-01 | Phase 20 | — | Active |
| COUP-02 | Phase 20 | — | Active |
| COUP-03 | Phase 20 | — | Active |
| COUP-04 | Phase 20 | — | Active |
| COUP-05 | Phase 20 | — | Active |
| REV-04 | Phase 21 | — | Active |
| REV-05 | Phase 21 | — | Active |
| REV-06 | Phase 21 | — | Active |
| AI-01 | Phase 22 | — | Active |
| AI-02 | Phase 22 | — | Active |
| AI-03 | Phase 22 | — | Active |
| AI-04 | Phase 22 | — | Active |
| AI-05 | Phase 22 | 22-04 + 22-06 | Satisfied 2026-05-02 |
| MQ-01 | Phase 23 | 23-01 | Active |
| MQ-02 | Phase 23 | 23-03 | Completed 2026-05-20 |
| MQ-03 | Phase 23 | — | Active |
| MQ-04 | Phase 23 | 23-05 | Completed 2026-05-20 |
| MQ-05 | Phase 23 | — | Active |
| PAY-01 | Phase 26 | — | Active |
| PAY-02 | Phase 26 | — | Active |
| PAY-03 | Phase 26 | — | Active |
| PAY-04 | Phase 26 | — | Active |
| MAIL-01 | Phase 27 | — | Active |
| MAIL-02 | Phase 27 | — | Active |
| MAIL-03 | Phase 27 | — | Active |
| MAIL-04 | Phase 27 | — | Active |

**Total active REQs: 40** (SEED 4 + ORDER 1 + ADMIN-06 1 + STORE 3 + ADMIN-01-05 5 + COUP 5 + REV 3 + AI 5 + MQ 5 + PAY 4 + MAIL 4)
**Mapped: 40/40** (100% coverage)

*Note: DB-01..04 (Phase 24) + SEC-01..04 (Phase 25) chưa backfill vào file này — sẽ thêm khi audit milestone. PAY + MAIL thêm 2026-05-22.*
