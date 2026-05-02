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
| v1.3 — Catalog Realism & Commerce Intelligence | Seed catalog đầy đủ, cart→DB, admin analytics, review polish, AI chatbot, coupon | Phase 16-22 | ACTIVE |

---

## Pre-Phase Setup (v1.3)

Thực hiện trước khi bắt đầu Phase 16. Không cần plan riêng — ghi chú cho implementer.

**Flyway V-number reservations:**

| Service | Version | Purpose | Phase |
|---------|---------|---------|-------|
| product-svc | V7 | Seed ~100 sản phẩm (Spring profile `dev` only) | Phase 16 |
| order-svc | V3 | Coupons + coupon_redemptions tables | Phase 20 |
| order-svc | V4 | Carts + cart_items tables | Phase 18 |
| chat_svc | — | Schema init qua Next.js API route (raw pg driver, không Flyway) | Phase 22 |

**New stack packages:**

| Package | Version | Purpose | Phase |
|---------|---------|---------|-------|
| recharts | 3.8.1 | Admin analytics charts (SVG-based, React JSX API) | Phase 19 |
| @anthropic-ai/sdk | 0.92.0 | Claude API chatbot (Next.js API route proxy) | Phase 22 |

**Spring profile `dev` isolation:**
- Seed migration `V7` phải ở `classpath:db/seed/dev/` (KHÔNG phải `classpath:db/migration/`)
- `application-dev.yml` thêm `spring.flyway.locations` include seed path
- Production profile KHÔNG chạy seed

---

## Phases

- [ ] **Phase 16: Seed Catalog Hiện Thực** — ~100 sản phẩm / 5 tech categories + Unsplash WebP + brand thực tế
- [ ] **Phase 17: Sửa Order Detail Items** — Fix hardcoded placeholder, hiển thị full line items cả user + admin
- [ ] **Phase 18: Kiểm Toán Storage + Cart→DB** — Audit localStorage/sessionStorage + migrate cart sang DB per-user
- [ ] **Phase 19: Hoàn Thiện Admin: Charts + Low-Stock** — 4 analytics charts + low-stock alert dashboard
- [ ] **Phase 20: Hệ Thống Coupon** — % off + fixed amount, admin CRUD, checkout input, atomic redemption
- [ ] **Phase 21: Hoàn Thiện Reviews** — Author edit/delete + sort controls + admin moderation
- [ ] **Phase 22: AI Chatbot Claude API MVP** — Customer FAQ + product Q&A + recommendation, streaming, history persist

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
  4. Developer restart với Spring profile `dev` thì seed chạy; restart với profile `prod` thì seed KHÔNG chạy — Flyway V7 idempotent (`ON CONFLICT DO NOTHING`)
**Plans:** TBD
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
**Plans:** 4 plans

Plans:
- [x] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [x] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [x] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
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
**Plans:** 4 plans

Plans:
- [ ] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [ ] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [ ] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
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
- [ ] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [ ] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [ ] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
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
**Plans:** 4 plans

Plans:
- [ ] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [ ] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [ ] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
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
**Plans:** 4 plans

Plans:
- [ ] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [ ] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [ ] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
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
**Plans:** 4 plans

Plans:
- [ ] 17-01-PLAN.md — Tạo lib helpers (orderLabels + useEnrichedItems hook)
- [ ] 17-02-PLAN.md — Rewrite admin order detail page (xóa placeholder + render items + shipping/payment)
- [ ] 17-03-PLAN.md — Extend user order detail page (thumbnail + brand subtitle) + CSS
- [ ] 17-04-PLAN.md — Extend Playwright E2E specs (regression-guard ADM-ORD-3 + ORD-DTL-2)
**UI hint**: yes

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 16. Seed Catalog Hiện Thực | 0/? | Not started | - |
| 17. Sửa Order Detail Items | 2/4 | In progress | - |
| 18. Kiểm Toán Storage + Cart→DB | 0/? | Not started | - |
| 19. Hoàn Thiện Admin: Charts + Low-Stock | 0/? | Not started | - |
| 20. Hệ Thống Coupon | 0/? | Not started | - |
| 21. Hoàn Thiện Reviews | 0/? | Not started | - |
| 22. AI Chatbot Claude API MVP | 0/? | Not started | - |

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

**Mapped: 27/27 REQs** (SEED 4 + ORDER 1 + ADMIN-06 1 + STORE 3 + ADMIN-01-05 5 + COUP 5 + REV 3 + AI 5)

---

*Roadmap created: 2026-05-02 — Milestone v1.3 Catalog Realism & Commerce Intelligence*
*Phase numbering tiếp tục từ Phase 16 (v1.2 kết thúc Phase 15)*
