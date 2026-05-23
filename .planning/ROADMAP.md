# ROADMAP — tmdt-use-gsd

**Project:** tmdt-use-gsd (Spring Boot microservices + Next.js e-commerce)
**Core Value:** Demo end-to-end shopping experience hoạt động với real data ở mọi điểm user nhìn thấy, đồng thời rèn quy trình GSD từ planning → execute → verify → archive.

---

## Milestones

| Milestone | Goal | Phases | Status |
|-----------|------|--------|--------|
| v1.0 — MVP Stabilization | API surface nhất quán + Swagger/OpenAPI + contract alignment | Phase 1-4 | SHIPPED 2026-04-25 |
| v1.1 — Real End-User Experience | DB foundation + auth thật + admin CRUD + cart→order persistence | Phase 5-8 | SHIPPED 2026-04-26 |
| v1.2 — UI/UX Completion | Residual closure + profile + address book + reviews + search + public polish | Phase 9-15 | SHIPPED 2026-05-02 |
| v1.3 — Catalog Realism & Commerce Intelligence | Seed catalog đầy đủ, cart→DB, admin analytics, review polish, AI chatbot, coupon, message queue, microservice hardening (DB tách hạ tầng + bảo mật gateway), thanh toán VNPay + email thật | Phase 16-27 | ACTIVE |

---

## Pre-Phase Setup (v1.3)

Thực hiện trước khi bắt đầu Phase 16. Không cần plan riêng — ghi chú cho implementer.

**Flyway V-number reservations:**

| Service | Version | Purpose | Phase |
|---------|---------|---------|-------|
| product-svc | V101 | Seed ~100 sản phẩm trong db/seed-dev/ (Spring profile `dev` only) | Phase 16 |
| order-svc | V5 | Coupons + coupon_redemptions tables (V5 vì V4 đã shipped Phase 18) | Phase 20 |
| order-svc | V4 | Carts + cart_items tables | Phase 18 |
| chat_svc | — | Schema init qua Next.js API route (raw pg driver, không Flyway) | Phase 22 |

**New stack packages:**

| Package | Version | Purpose | Phase |
|---------|---------|---------|-------|
| recharts | 3.8.1 | Admin analytics charts (SVG-based, React JSX API) | Phase 19 |
| @anthropic-ai/sdk | 0.92.0 | Claude API chatbot (Next.js API route proxy) | Phase 22 |

**Spring profile `dev` isolation:**
- Seed migration `V101` phải ở `classpath:db/seed-dev/` (KHÔNG phải `classpath:db/migration/`) — tiếp nối V100 đã có
- `application.yml` profile=dev đã include `classpath:db/seed-dev` trong `spring.flyway.locations` — KHÔNG cần sửa config (verified RESEARCH Finding 1)
- Production profile KHÔNG chạy seed

---

## Phases

- [x] **Phase 16: Seed Catalog Hiện Thực** ✅ 2026-05-02 — ~100 sản phẩm / 5 tech categories + Unsplash WebP + brand thực tế (3/3 plans, manual UAT defer cho `/gsd-verify-work`)
- [x] **Phase 17: Sửa Order Detail Items** — Fix hardcoded placeholder, hiển thị full line items cả user + admin (4/4 plans complete 2026-05-02)
- [x] **Phase 18: Kiểm Toán Storage + Cart→DB** — Audit localStorage/sessionStorage + migrate cart sang DB per-user — **COMPLETED 2026-05-02**
- [x] **Phase 19: Hoàn Thiện Admin: Charts + Low-Stock** — 4 analytics charts + low-stock alert dashboard ✅ 2026-05-02
- [ ] **Phase 20: Hệ Thống Coupon** — % off + fixed amount, admin CRUD, checkout input, atomic redemption
- [x] **Phase 21: Hoàn Thiện Reviews** ✅ 2026-05-02 — Author edit/delete + sort controls + admin moderation (4/4 plans complete)
- [ ] **Phase 22: AI Chatbot Claude API MVP** — Customer FAQ + product Q&A + recommendation, streaming, history persist
- [ ] **Phase 23: Message Queue Integration (RabbitMQ)** — Đáp ứng yêu cầu BẮT BUỘC 3.3 của đề chủ đề 4: giao tiếp bất đồng bộ giữa các microservice qua RabbitMQ; luồng OrderPlaced → inventory + notification với retry + DLQ
- [x] **Phase 24: Database Per Service (tách CSDL hạ tầng)** ✅ 2026-05-21 — Củng cố yêu cầu 3.4 + tính chịu lỗi độc lập (mục 4): chuyển từ "shared postgres / separate schema" sang "mỗi service một postgres container + credential riêng". Demo được failure isolation (1 DB chết → các service khác vẫn chạy). 4/4 plans
- [x] **Phase 25: Gateway JWT Edge Authentication (vá lỗ hổng X-User-Id)** ✅ 2026-05-21 — Củng cố yêu cầu 4 (JWT): gateway verify JWT + strip X-User-Id từ client + inject trusted X-User-Id sau khi verify. Bỏ port mapping của các service nội bộ trong docker-compose. Đóng lỗ hổng `orders-cross-user-leak` ở tầng kiến trúc. 5/5 plans
- [x] **Phase 26: Tích Hợp Thanh Toán VNPay Sandbox** — Khách chọn VNPay tại checkout → redirect cổng VNPay sandbox → IPN callback verify chữ ký HMAC SHA512 + cập nhật trạng thái thanh toán đơn hàng (idempotent)
 (completed 2026-05-22)
- [ ] **Phase 27: Gửi Email Thật (SMTP)** — Email thật qua SMTP cho 3 luồng: xác thực tài khoản (verify đăng ký + reset mật khẩu), xác nhận đơn hàng, cập nhật trạng thái đơn — tái dụng notification-service consumer RabbitMQ (Phase 23)

---

## Phase Details

### Phase 16: Seed Catalog Hiện Thực

**Goal:** Người dùng truy cập trang sản phẩm thấy ~100 sản phẩm thực tế với ảnh WebP chất lượng cao, thuộc đúng 5 danh mục tech, hiển thị brand chính xác và giá realistic
**Depends on:** Không có (standalone foundation)
**Requirements:** SEED-01, SEED-02, SEED-03, SEED-04
**Success Criteria** (what must be TRUE):
  1. Người dùng truy cập `/products` thấy ~100 sản phẩm phân phối qua 5 categories: điện thoại, laptop, chuột, bàn phím, tai nghe (categories cũ fashion/household/books/cosmetics đã biến mất)
  2. Mỗi sản phẩm có ảnh WebP hiển thị đúng từ Unsplash CDN (không bị broken image), cùng tên brand thực tế như Apple, Samsung, Dell, Logitech, Sony, Razer, ASUS
  3. FilterSidebar brand multi-select hiển thị brand list đúng domain tech (không còn brand sai domain)
  4. Developer restart với Spring profile `dev` thì seed chạy; restart với profile `prod` thì seed KHÔNG chạy — Flyway V101 idempotent (`ON CONFLICT DO NOTHING`)
**Plans:** 3 plans
- [x] 16-01-PLAN.md — Curate IMAGES.csv (≥100 Unsplash photo IDs cho 5 tech categories) ✅ 2026-05-02 (107 IDs, commit 20be054)
- [x] 16-02-PLAN.md — V101__seed_catalog_realistic.sql + patch ROADMAP V7→V101 ✅ 2026-05-02 (100 SP / 25 brands, commits d46f028 + 3aca025)
- [x] 16-03-PLAN.md — E2E Playwright spec + manual VERIFICATION.md + human acceptance ✅ 2026-05-02 (seed-catalog.spec.ts 7 tests + 16-VERIFICATION.md 5 sections, commits f842cd2 + 5f8257a; checkpoint auto-approved trong auto mode, manual UAT defer cho /gsd-verify-work)
**UI hint**: yes

---

### Phase 17: Sửa Order Detail Items

**Goal:** Người dùng và admin xem chi tiết đơn hàng thấy đầy đủ danh sách sản phẩm đã mua thay vì placeholder text
**Depends on:** Không có (bug fix độc lập)
**Requirements:** ORDER-01, ADMIN-06
**Success Criteria** (what must be TRUE):
  1. Người dùng vào `/account/orders/[id]` thấy danh sách line items với ảnh sản phẩm, tên, brand, đơn giá, số lượng, thành tiền — KHÔNG có placeholder text
  2. Admin vào `/admin/orders/[id]` thấy đúng danh sách sản phẩm chi tiết (KHÔNG còn chuỗi "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện")
  3. `AdminOrder` TypeScript interface có trường `items: OrderItem[]` và FE parse `ApiResponse<OrderDto>` unwrap đúng
**Plans:** 4/4 plans complete

Plans:
- [x] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [x] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [x] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [x] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
**UI hint**: yes

---

### Phase 18: Kiểm Toán Storage + Cart→DB

**Goal:** Giỏ hàng của người dùng persist trên server (không mất khi clear browser), và toàn bộ data user-sensitive không còn rò rỉ qua localStorage
**Depends on:** Phase 16 (cần sản phẩm thực tế để test add-to-cart workflow)
**Requirements:** STORE-01, STORE-02, STORE-03
**Success Criteria** (what must be TRUE):
  1. Người dùng add sản phẩm vào giỏ, đóng tab, mở lại → giỏ hàng vẫn còn đủ (persist qua server, không phụ thuộc localStorage)
  2. Guest add vào giỏ → login → giỏ hàng merge đúng, không bị duplicate item
  3. Audit report (SUMMARY.md) liệt kê tất cả `localStorage`/`sessionStorage` keys được classify: (a) đã migrate sang DB, (b) UI preference giữ lại hợp lý, (c) auth-token reviewed
  4. Cart localStorage không chứa dữ liệu user sau khi logout
**Plans:** 6 plans
- [x] 18-01-PLAN.md — BE foundation: Flyway V4 carts+cart_items, JPA entities, repos, cleanup InMemoryCartRepository
- [x] 18-02-PLAN.md — BE service+controller: CartCrudService với native upsert + CartController 6 endpoints
- [x] 18-03-PLAN.md — FE service+hooks: services/cart.ts dual-backend wrapper + useCart React Query hooks
- [x] 18-04-PLAN.md — FE consumers: cart page + checkout page + Header badge refactor sang React Query
- [x] 18-05-PLAN.md — Auth integration: AuthProvider login merge cart + logout clear cart
- [x] 18-06-PLAN.md — Storage audit + UAT checkpoint: classify mọi localStorage key + 4 phase truths verify
**UI hint**: yes

---

### Phase 19: Hoàn Thiện Admin: Charts + Low-Stock

**Goal:** Admin nhìn vào dashboard thấy 4 biểu đồ analytics thực tế và nhận cảnh báo tồn kho thấp để ra quyết định kinh doanh
**Depends on:** Phase 16 (cần catalog data thật để charts có ý nghĩa), Phase 17 (admin order UX hoàn chỉnh)
**Requirements:** ADMIN-01, ADMIN-02, ADMIN-03, ADMIN-04, ADMIN-05
**Success Criteria** (what must be TRUE):
  1. Admin thấy biểu đồ doanh thu theo thời gian (line/area chart) với dropdown 7d/30d/90d/all — default 30d, giá trị aggregate từ đơn hàng DELIVERED thật
  2. Admin thấy top-10 sản phẩm bán chạy (bar chart) theo số lượng bán trong window đã chọn
  3. Admin thấy phân phối trạng thái đơn hàng (pie/donut chart) với counts thật: pending/confirmed/shipped/delivered/cancelled
  4. Admin thấy biểu đồ user signups mới theo ngày (line chart) với số liệu thật từ user-svc
  5. Admin thấy danh sách/banner sản phẩm có `stock < 10` trực tiếp trên dashboard — click được để vào trang edit sản phẩm đó
**Plans:** 4 plans

Plans:
- [x] 19-01-PLAN.md — order-svc AdminChartsController (revenue + top-products + status-distribution) + ProductBatchClient + integration tests ✅ 2026-05-02
- [x] 19-02-PLAN.md — user-svc AdminChartsController (signups) + repository @Query + integration tests ✅ 2026-05-02
- [x] 19-03-PLAN.md — product-svc AdminChartsController (low-stock) + AdminProductBatchController + LowStockService + integration tests ✅ 2026-05-02
- [x] 19-04-PLAN.md — FE: install recharts@3.8.1 + chart fetchers + 4 chart components + ChartCard + LowStockSection + extend admin/page.tsx + 2 Playwright smoke specs ✅ 2026-05-02
**UI hint**: yes

---

### Phase 20: Hệ Thống Coupon

**Goal:** Khách hàng có thể nhập mã giảm giá hợp lệ tại checkout và nhận giảm giá tương ứng; admin quản lý toàn bộ vòng đời coupon
**Depends on:** Phase 18 (cart phải persist server-side để coupon validation dùng server-side cart total)
**Requirements:** COUP-01, COUP-02, COUP-03, COUP-04, COUP-05
**Success Criteria** (what must be TRUE):
  1. Người dùng nhập mã coupon hợp lệ tại checkout → thấy preview discount amount trước khi confirm — mã expired/sai/đã dùng → thấy thông báo lỗi rõ ràng
  2. Admin tại `/admin/coupons` tạo, chỉnh sửa, disable/delete coupon với đầy đủ field: type (% hoặc fixed), value, min_order, expiry, max_total_uses
  3. Hai user cùng dùng coupon "last slot" đồng thời → chỉ 1 user thành công (race condition safe, KHÔNG double-redemption)
  4. Đơn hàng tại `/account/orders/[id]` và `/admin/orders/[id]` hiển thị coupon code + discount amount nếu order có áp dụng coupon
**Plans:** 6 plans

Plans:
- [ ] 20-01-PLAN.md — BE foundation: Flyway V5 migration + JPA entities + repositories (CouponEntity, CouponRedemptionEntity, OrderEntity extension)
- [ ] 20-02-PLAN.md — BE service + admin controller: CouponService CRUD + CouponPreviewService.validate + CouponRedemptionService.atomicRedeem + AdminCouponController + error code enum + DTOs
- [ ] 20-03-PLAN.md — BE integration: extend OrderCrudService.create với atomic coupon + extend OrderEntity discountAmount/couponCode + extend OrderDto + POST /orders/coupons/validate + race condition IT (D-25)
- [ ] 20-04-PLAN.md — Gateway routes (user coupons + admin coupons) + ROADMAP patch (V3 → V5)
- [ ] 20-05-PLAN.md — FE checkout coupon section: useApplyCoupon mutation + Input/Áp dụng button + chip + auto-revalidate + extend createOrder body với couponCode + error toast mapping
- [ ] 20-06-PLAN.md — FE admin /admin/coupons page (rhf+zod+modal CRUD) + sidebar nav link + extend /profile/orders/[id] và /admin/orders/[id] hiển thị couponCode + discountAmount
**UI hint**: yes

---

### Phase 21: Hoàn Thiện Reviews

**Goal:** Tác giả review có thể sửa/xoá review của mình; người dùng có thể sắp xếp reviews theo ý muốn; admin có thể kiểm duyệt reviews vi phạm
**Depends on:** Phase 16 (cần catalog thật để test reviews trên sản phẩm thực tế)
**Requirements:** REV-04, REV-05, REV-06
**Success Criteria** (what must be TRUE):
  1. Tác giả review thấy nút "Sửa" và "Xoá" trên review của mình — sửa thành công cập nhật nội dung; xoá thành công ẩn review khỏi danh sách công khai nhưng avg_rating recalculate đúng
  2. Người dùng chọn sort "Mới nhất" / "Đánh giá cao nhất" / "Đánh giá thấp nhất" → danh sách review thay đổi thứ tự ngay lập tức (query param `?sort=`)
  3. Admin tại `/admin/reviews` thấy danh sách tất cả reviews, filter được theo visible/hidden, có thể hide hoặc unhide review bất kỳ — review bị hide không hiển thị cho user thường
**Plans:** 4/4 plans complete

Plans:
- [x] 21-01-PLAN.md — V7 migration + ReviewEntity mutators + Repository visibility finders + AdminReviewSpecifications + edit-window config ✅ 2026-05-02
- [x] 21-02-PLAN.md — ReviewService 5 mutation methods + recompute helper + ReviewController PATCH/DELETE + AdminReviewController + tests ✅ 2026-05-02
- [x] 21-03-PLAN.md — FE author UX: services/reviews.ts extend + types SortKey/AdminReview + ReviewSection sort/edit/delete + ReviewList dropdown/actions + ReviewForm edit mode ✅ 2026-05-02
- [x] 21-04-PLAN.md — FE admin moderation /admin/reviews page + sidebar nav + Playwright E2E specs (author edit + admin moderation) ✅ 2026-05-02
**UI hint**: yes

---

### Phase 22: AI Chatbot Claude API MVP

**Goal:** Khách hàng đăng nhập có thể hỏi chatbot về sản phẩm và nhận gợi ý mua sắm bằng tiếng Việt; admin nhận gợi ý reply tự động cho đơn hàng
**Depends on:** Phase 16 (cần catalog đầy đủ để chatbot product Q&A có giá trị thực tế)
**Requirements:** AI-01, AI-02, AI-03, AI-04, AI-05
**Success Criteria** (what must be TRUE):
  1. Người dùng đã đăng nhập thấy floating chat button góc dưới phải mọi trang — click mở modal, nhắn tin và nhận streaming response token-by-token bằng tiếng Việt; guest thấy nút "Đăng nhập để chat"
  2. Chatbot trả lời về sản phẩm có liên quan từ catalog (tên, giá, brand đúng) khi được hỏi — system prompt đã inject context sản phẩm với XML tag isolation
  3. Người dùng mở lại chatbot sau khi đóng tab → thấy lịch sử chat sessions cũ, có thể tiếp tục conversation
  4. Admin tại `/admin/orders/[id]` click "AI suggest reply" → nhận gợi ý phản hồi customer dựa trên context order — admin review và gửi thủ công (KHÔNG auto-confirm)
  5. API key Anthropic KHÔNG bao giờ xuất hiện trong Network tab của browser (proxy qua Next.js API route)
**Plans:** 7 plans

Plans:
- [x] 22-01-PLAN.md — Foundations: deps + env + lib/chat helpers (pg, schema-init, auth, rate-limit, vn-text, anthropic, product-context, messages-repo) ✅ 2026-05-02
- [x] 22-02-PLAN.md — POST /api/chat/stream route (Anthropic streaming + persist + abort + caching) ✅ 2026-05-02
- [x] 22-03-PLAN.md — GET /api/chat/sessions + GET /api/chat/sessions/[id]/messages (owner-only) ✅ 2026-05-02
- [x] 22-04-PLAN.md — POST /api/admin/orders/[id]/suggest-reply (admin role gate, 1-shot) ✅ 2026-05-02
- [x] 22-05-PLAN.md — Customer chat UI: FloatingChatButton + ChatPanel + useChat hook + sessions sidebar + mount layout ✅ 2026-05-02
- [x] 22-06-PLAN.md — Admin order detail "AI gợi ý phản hồi" button + SuggestReplyModal ✅ 2026-05-02
- [x] 22-07-PLAN.md — Playwright E2E (chatbot-customer / chatbot-admin / chatbot-edge) + 22-VERIFICATION.md ✅ 2026-05-02
**UI hint**: yes

---

### Phase 23: Message Queue Integration (RabbitMQ)

**Goal:** Các microservice giao tiếp bất đồng bộ qua RabbitMQ trong ít nhất một luồng nghiệp vụ — đáp ứng yêu cầu BẮT BUỘC 3.3 của đề chủ đề 4. Khi khách đặt hàng, order-service publish event `OrderPlaced` lên RabbitMQ; inventory-service consume để trừ kho; notification-service consume để ghi dispatch log. Luồng có retry + Dead Letter Queue để xử lý lỗi message (đáp ứng 3.5).
**Depends on:** Phase 18 (cart→DB phải xong để order workflow ổn định), Phase 20 (Coupon — không bắt buộc nhưng nếu xong sẽ có order workflow đầy đủ hơn để publish event)
**Requirements:** MQ-01, MQ-02, MQ-03, MQ-04, MQ-05
**Success Criteria** (what must be TRUE):
  1. Hệ thống có 1 container RabbitMQ chạy trong docker-compose.yml với Management UI truy cập được tại http://localhost:15672
  2. Khi user đặt hàng thành công, order-service publish event `OrderPlaced` (routing key `order.placed`) vào topic exchange `order.events` SAU KHI DB commit — Publisher Confirms được bật để chắc message đã vào broker
  3. inventory-service consume event `OrderPlaced` từ queue `inventory.order-events`, trừ kho atomic, lưu ledger entry — idempotent qua bảng `processed_events` (xử lý cùng eventId 2 lần chỉ trừ kho 1 lần)
  4. notification-service consume event `OrderPlaced` từ queue `notification.order-events`, ghi vào dispatch_log một bản ghi thông báo — idempotent tương tự
  5. Khi consumer ném exception, message được retry 3 lần với exponential backoff; sau 3 lần thất bại, message rơi vào Dead Letter Queue `order-events.dlq` — verify được trong Management UI
  6. Producer log và consumer log có cùng `traceId` (tận dụng TraceIdFilter sẵn có) để theo dõi xuyên service — đáp ứng yêu cầu "log để theo dõi message" (mục 4 đề)
  7. Có ít nhất 1 integration test (Testcontainers + RabbitMQ container) chứng minh luồng end-to-end: tạo order → message publish → 2 consumer xử lý → side effect xảy ra trong DB
**Plans:** 6 plans

Plans:
- [x] 23-01-PLAN.md — Bootstrap infra: docker-compose RabbitMQ service + db/init notification_svc schema + REQUIREMENTS.md backfill MQ-01..MQ-05 ✅ 2026-05-20 (commits 7fff50f + ab4e5ca)
- [x] 23-02-PLAN.md — notification-service persistence bootstrap: pom JPA+Flyway+AMQP + application.yml datasource+rabbitmq + V1 init schema (dispatch_log + processed_events) ✅ 2026-05-20 (commits cb0e90a + 6ac96c1)
- [x] 23-03-PLAN.md — Producer: RabbitMQConfig topology 3 service + OrderEventPublisher afterCommit+CorrelationData + OrderCrudService chèn publish + XÓA deductStock REST legacy ✅ 2026-05-20 (commits c5876d9 + 3d8c238)
- [x] 23-04-PLAN.md — Inventory consumer: V2 migration + V102 seed inventory + JPA entities + OrderPlacedListener idempotent + decrementForOrder ✅ 2026-05-20 (commits 253102f + b2c729b)
- [x] 23-05-PLAN.md — Notification consumer: JPA entities (DispatchLog + ProcessedEvent) + NotificationDispatchService render template + OrderPlacedNotifyListener idempotent ✅ 2026-05-20 (commits 68e5ce8 + 26d45ec)
- [x] 23-06-PLAN.md — Integration tests (4 scenarios D-18: happy/idempotent/DLQ/retry) + smoke script verify-mq.sh + cập nhật architecture/02-sequence-diagrams.md ✅ 2026-05-20 (commits fc86cb0 + 9957b99)
**UI hint**: no (toàn bộ là backend + infrastructure)

---

### Phase 24: Database Per Service (tách CSDL hạ tầng)

**Goal:** Mỗi backend microservice có postgres container + credential riêng (database-per-service đúng nghĩa). Demo được failure isolation: tắt 1 postgres → các service khác vẫn hoạt động bình thường.
**Depends on:** Không cứng (độc lập với Phase 23). Khuyến nghị làm sau Phase 23 để tránh xung đột docker-compose merge.
**Requirements:** DB-01, DB-02, DB-03, DB-04
**Success Criteria** (what must be TRUE):
  1. docker-compose.yml có 5 container postgres riêng (postgres-user, postgres-product, postgres-order, postgres-payment, postgres-inventory) — mỗi cái có volume + healthcheck riêng. notification-service hiện không dùng DB → không tạo container.
  2. Mỗi service application.yml trỏ tới đúng host postgres của mình + credential riêng (không còn dùng chung `tmdt/tmdt`). Service không thể truy cập DB của service khác về mặt vật lý (kiểm chứng bằng test connection sai).
  3. Stop container `postgres-user` → user-service trả 503/lỗi DB, nhưng `/api/products`, `/api/orders`, `/api/inventory` vẫn hoạt động bình thường (đọc/ghi DB của chúng). Đây là failure isolation thực sự.
  4. Flyway migration của mỗi service vẫn chạy đúng khi container postgres của service đó khởi động lần đầu. Existing data (nếu có) phải được migrate sang DB mới.
**Plans:** chưa lập (chạy /gsd-discuss-phase 24 → /gsd-plan-phase 24)

Plans:
- [ ] (sẽ tạo bởi /gsd-plan-phase 24)
**UI hint**: no (infrastructure thuần)

---

### Phase 25: Gateway JWT Edge Authentication (vá lỗ hổng X-User-Id)

**Goal:** API Gateway trở thành điểm verify JWT duy nhất, inject trusted `X-User-Id` từ JWT claim cho các service nội bộ. Service nội bộ không còn lộ port ra mạng ngoài. Đóng lỗ hổng cho phép client tự đặt `X-User-Id` để đọc dữ liệu user khác.
**Depends on:** Không cứng. Có thể làm song song Phase 23/24.
**Requirements:** SEC-01, SEC-02, SEC-03, SEC-04
**Success Criteria** (what must be TRUE):
  1. Gateway có filter verify JWT trên mọi route protected (`/api/orders/**`, `/api/users/me/**`, `/api/admin/**`). JWT sai/hết hạn/thiếu → 401 ngay tại gateway, không tới service.
  2. Gateway strip `X-User-Id` đến từ client (mọi giá trị client gửi đều bị loại bỏ), sau verify JWT thành công thì inject `X-User-Id` mới từ claim `sub`. Service tin header này vì biết chỉ gateway tạo được.
  3. docker-compose.yml: bỏ `ports:` mapping của 6 service nội bộ (user/product/order/payment/inventory/notification). Chỉ gateway và frontend lộ ra host. Verify: `curl http://localhost:8081/...` (port cũ của user-service) không kết nối được, nhưng `curl http://localhost:8080/api/users/...` qua gateway thì OK.
  4. Test reproduce bug `orders-cross-user-leak` cũ: gọi `/api/orders` với JWT của user A nhưng cố giả `X-User-Id` của user B → vẫn trả orders của A (X-User-Id giả bị strip).
**Plans:** chưa lập (chạy /gsd-discuss-phase 25 → /gsd-plan-phase 25)

Plans:
- [ ] (sẽ tạo bởi /gsd-plan-phase 25)
**UI hint**: no (backend + infrastructure + security)

---

### Phase 26: Tích Hợp Thanh Toán VNPay Sandbox

**Goal:** Khách hàng tại checkout chọn phương thức "Thanh toán qua VNPay", được redirect sang cổng VNPay sandbox để thanh toán, quay lại return URL với kết quả; backend xác nhận giao dịch qua IPN callback (server-to-server) và cập nhật trạng thái thanh toán của đơn hàng một cách đáng tin cậy.
**Depends on:** Phase 20 (Coupon — order workflow phải có discountAmount để số tiền gửi VNPay là final amount đúng)
**Requirements:** PAY-01, PAY-02, PAY-03, PAY-04
**Success Criteria** (what must be TRUE):
  1. Khách hàng tại `/checkout` chọn phương thức "VNPay" → bấm đặt hàng → được redirect sang trang VNPay sandbox với đúng số tiền (đã trừ coupon nếu có) và mã đơn hàng
  2. Sau khi thanh toán trên VNPay sandbox, khách quay lại return URL của ứng dụng và thấy trang kết quả rõ ràng (thành công / thất bại / huỷ) — KHÔNG dựa vào return URL để cập nhật DB
  3. Backend nhận IPN callback từ VNPay (server-to-server), verify chữ ký HMAC SHA512 (`vnp_SecureHash`), so khớp số tiền, cập nhật `payment_status` của đơn (PAID / FAILED) — idempotent khi VNPay gửi lại cùng giao dịch
  4. Đơn hàng tại `/account/orders/[id]` và `/admin/orders/[id]` hiển thị đúng trạng thái thanh toán + phương thức + mã giao dịch VNPay; chữ ký sai hoặc số tiền lệch → giao dịch bị từ chối và ghi log
**Plans:** 4/4 plans complete

Plans:
- [x] 26-01-PLAN.md — payment-service VNPay core: HMAC SHA512 ký/verify + messaging payment.events + IPN/return controller + gateway whitelist
- [x] 26-02-PLAN.md — order-service nền tảng dữ liệu: Flyway V6 (payment_status + vnp_transaction_no + processed_events) + entity/dto/mapper + port idempotency infra
- [x] 26-03-PLAN.md — order-service tích hợp: PaymentSessionClient + nhánh VNPAY OrderCrudService + PaymentEventListener consume PaymentSucceeded/Failed
- [x] 26-04-PLAN.md — FE: checkout selector VNPay + redirect + trang kết quả /checkout/result polling + order display
**UI hint**: yes (checkout payment method selector + trang kết quả thanh toán)

---

### Phase 26.1: Migrate Payment Gateway VNPay to MoMo (INSERTED)

**Goal:** Thay thế cổng thanh toán từ VNPay sang MoMo Developer (sandbox dễ tiếp cận hơn cho dev cá nhân — HMAC SHA256, JSON REST, không cần KYC). Giữ NGUYÊN kiến trúc event-based + cột `payment_status` đã có từ Phase 26: thay đổi giới hạn trong package `paymentservice/vnpay/` (rewrite sang `momo/`), rename cột `vnp_transaction_no` → `payment_transaction_no`, đổi enum value `VNPAY` → `MOMO` ở order-service + frontend. Xóa hoàn toàn code VNPay (không giữ song song).
**Requirements:** PAY-01, PAY-02, PAY-03, PAY-04 (re-targeted to MoMo)
**Depends on:** Phase 26 (cần code/architecture đã có)
**Success Criteria** (what must be TRUE):
  1. Khách hàng tại `/checkout` chọn phương thức "Thanh toán qua MoMo" → bấm đặt hàng → được redirect sang trang MoMo sandbox với đúng số tiền (đã trừ coupon nếu có) và mã đơn hàng
  2. Sau khi thanh toán trên MoMo sandbox, khách quay lại return URL của ứng dụng và thấy trang kết quả rõ ràng (thành công / thất bại / huỷ) — KHÔNG dựa vào return URL để cập nhật DB
  3. Backend nhận IPN callback từ MoMo (server-to-server POST JSON), verify chữ ký HMAC SHA256, so khớp số tiền, cập nhật `payment_status` của đơn (PAID / FAILED) — idempotent khi MoMo gửi lại cùng giao dịch
  4. Đơn hàng tại `/account/orders/[id]` và `/admin/orders/[id]` hiển thị đúng trạng thái thanh toán + phương thức (MOMO) + mã giao dịch MoMo
  5. Code VNPay (`paymentservice/vnpay/` + test) bị xóa sạch; không còn reference `VNPAY` / `vnp_*` trong codebase
**Plans:** 4 plans

Plans:
- [ ] 26.1-01-PLAN.md — payment-service: xóa vnpay/ + 2 test; tạo package momo/ (MomoConfig + MomoSignature HMAC SHA256 + MomoService buildPaymentUrl POST MoMo create + processIpn idempotent 204 + MomoController) + ApiResponseAdvice bypass + PaymentEventEnvelope rename field + gateway whitelist 2 endpoint MoMo + docker-compose env MOMO_*
- [ ] 26.1-02-PLAN.md — order-service: Flyway V7 RENAME COLUMN vnp_transaction_no → payment_transaction_no; OrderEntity/OrderDto/OrderMapper field rename; OrderCrudService switch case MOMO; PaymentSessionClient.createMomoSession (provider=MOMO); PaymentEventListener setPaymentTransactionNo; XÓA OrderCrudServiceVNPayIT, tạo OrderCrudServiceMomoIT
- [ ] 26.1-03-PLAN.md — frontend: type Order rename paymentTransactionNo; orderLabels MOMO; services/payments.ts getMomoReturn; checkout selector option MoMo + redirect; /checkout/result đọc MoMo query (resultCode/orderId/transId) giữ poll 3s×5 + 5 trạng thái UI-SPEC; order display "Mã giao dịch MoMo"; XÓA e2e/12-vnpay-payment.spec.ts, tạo 12-momo-payment.spec.ts
- [ ] 26.1-04-PLAN.md — cleanup verification: grep audit toàn sources/ + docker-compose enforce SC5 (zero VNPay UNACCEPTABLE matches); smoke test compile + Playwright list; tạo 26.1-CLEANUP-VERIFY.md ghi log SC5 verdict
**UI hint**: nhẹ (đổi label selector + trang kết quả tái dụng)

### Phase 27: Gửi Email Thật (SMTP)

**Goal:** Ứng dụng gửi email thật tới hộp thư người dùng qua SMTP cho ba luồng: xác thực tài khoản (xác minh email khi đăng ký + reset mật khẩu), xác nhận đơn hàng, và cập nhật trạng thái đơn. Tái dụng notification-service đã có consumer RabbitMQ từ Phase 23.
**Depends on:** Phase 23 (notification-service + RabbitMQ consumer `OrderPlaced` đã sẵn sàng để gắn email xác nhận đơn)
**Requirements:** MAIL-01, MAIL-02, MAIL-03, MAIL-04
**Success Criteria** (what must be TRUE):
  1. Cấu hình SMTP (host, port, username, password/app-password, from-address) đọc hoàn toàn từ biến môi trường — KHÔNG hardcode credential trong source; thiếu env thì service vẫn khởi động được và log cảnh báo (graceful degradation)
  2. Người dùng đăng ký tài khoản mới → nhận email xác minh tới hộp thư thật; bấm link xác minh → tài khoản chuyển trạng thái verified. Yêu cầu reset mật khẩu → nhận email chứa link/token reset
  3. Khi đặt hàng thành công, notification-service consume event `OrderPlaced` → gửi email xác nhận đơn hàng (mã đơn, danh sách sản phẩm, tổng tiền) tới email khách
  4. Khi trạng thái đơn thay đổi (shipped / delivered / cancelled), khách nhận email cập nhật tương ứng; email render bằng template tiếng Việt và gửi bất đồng bộ (không chặn request chính)
**Plans:** chưa lập (chạy /gsd-discuss-phase 27 → /gsd-plan-phase 27)

Plans:
- [ ] (sẽ tạo bởi /gsd-plan-phase 27)
**UI hint**: yes nhẹ (trang xác minh email + trang reset mật khẩu); phần gửi mail là backend

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 16. Seed Catalog Hiện Thực | 3/3 | Complete | 2026-05-02 |
| 17. Sửa Order Detail Items | 4/4 | Complete | 2026-05-02 |
| 18. Kiểm Toán Storage + Cart→DB | 6/6 | COMPLETED | 2026-05-02 |
| 19. Hoàn Thiện Admin: Charts + Low-Stock | 0/? | Not started | - |
| 20. Hệ Thống Coupon | 0/? | Not started | - |
| 21. Hoàn Thiện Reviews | 4/4 | Complete    | 2026-05-02 |
| 22. AI Chatbot Claude API MVP | 7/7 | Ready for verify | 2026-05-02 |
| 23. Message Queue Integration (RabbitMQ) | 2/6 | In progress | - |
| 24. Database Per Service (tách CSDL hạ tầng) | 0/? | Not planned | - |
| 25. Gateway JWT Edge Authentication | 0/? | Not planned | - |
| 26. Tích Hợp Thanh Toán VNPay Sandbox | 4/4 | Complete   | 2026-05-22 |
| 27. Gửi Email Thật (SMTP) | 0/? | Not planned | - |

---

## Coverage Map (v1.3)

| REQ-ID | Phase |
|--------|-------|
| SEED-01 | Phase 16 |
| SEED-02 | Phase 16 |
| SEED-03 | Phase 16 |
| SEED-04 | Phase 16 |
| ORDER-01 | Phase 17 |
| ADMIN-06 | Phase 17 |
| STORE-01 | Phase 18 |
| STORE-02 | Phase 18 |
| STORE-03 | Phase 18 |
| ADMIN-01 | Phase 19 |
| ADMIN-02 | Phase 19 |
| ADMIN-03 | Phase 19 |
| ADMIN-04 | Phase 19 |
| ADMIN-05 | Phase 19 |
| COUP-01 | Phase 20 |
| COUP-02 | Phase 20 |
| COUP-03 | Phase 20 |
| COUP-04 | Phase 20 |
| COUP-05 | Phase 20 |
| REV-04 | Phase 21 |
| REV-05 | Phase 21 |
| REV-06 | Phase 21 |
| AI-01 | Phase 22 |
| AI-02 | Phase 22 |
| AI-03 | Phase 22 |
| AI-04 | Phase 22 |
| AI-05 | Phase 22 |
| MQ-01 | Phase 23 |
| MQ-02 | Phase 23 |
| MQ-03 | Phase 23 |
| MQ-04 | Phase 23 |
| MQ-05 | Phase 23 |
| DB-01 | Phase 24 |
| DB-02 | Phase 24 |
| DB-03 | Phase 24 |
| DB-04 | Phase 24 |
| SEC-01 | Phase 25 |
| SEC-02 | Phase 25 |
| SEC-03 | Phase 25 |
| SEC-04 | Phase 25 |
| PAY-01 | Phase 26 |
| PAY-02 | Phase 26 |
| PAY-03 | Phase 26 |
| PAY-04 | Phase 26 |
| MAIL-01 | Phase 27 |
| MAIL-02 | Phase 27 |
| MAIL-03 | Phase 27 |
| MAIL-04 | Phase 27 |

**Mapped: 48/48 REQs** (SEED 4 + ORDER 1 + ADMIN-06 1 + STORE 3 + ADMIN-01-05 5 + COUP 5 + REV 3 + AI 5 + MQ 5 + DB 4 + SEC 4 + PAY 4 + MAIL 4)

Plans:
- [ ] TBD (run /gsd-plan-phase 26 to break down)

---

*Roadmap created: 2026-05-02 — Milestone v1.3 Catalog Realism & Commerce Intelligence*
*Phase numbering tiếp tục từ Phase 16 (v1.2 kết thúc Phase 15)*
