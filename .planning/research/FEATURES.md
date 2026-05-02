# Feature Landscape — v1.3 Catalog Realism & Commerce Intelligence

**Domain:** B2C E-commerce (Spring Boot microservices + Next.js)
**Researched:** 2026-05-02
**Scope:** 7 feature axes locked cho v1.3

---

## Axis 1 — SEED Catalog Realistic (~100 SP / 5 categories)

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| 100 sản phẩm trải đều 5 categories | Demo catalog thưa (10 SP hiện tại) trông empty, không realistic | M | 20 SP/category là mục tiêu |
| Unsplash WebP CDN URL thật | Ảnh placeholder/text làm demo mất tin cậy; 67% buyers quyết định dựa trên image | S | Unsplash free-tier không cần API key với static URL pattern |
| Brand realistic per category | "Apple", "Samsung", "Logitech" — brand giả trông amateurish | S | Dùng brand thật nhưng product là fictionalized/demo |
| Price trong dải thực tế VNĐ | Điện thoại 5-30tr, laptop 10-50tr, chuột 200k-3tr, bàn phím 500k-5tr, tai nghe 500k-8tr | S | Mismatch giá phá vỡ immersion |
| Slug unique, SEO-friendly | Cần cho product detail URL | S | brand + model + category slug |
| stock realistic per SKU | 3-tier stock badge đã có — cần data để thể hiện | S | Mix: high/low/out-of-stock |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| short_description riêng biệt per SP | Card carousel không bị truncate title 200 ký tự | S | Column đã có trong V2 migration |
| original_price (giá gốc) một số SP | Hiển thị "giảm giá" — cột đã có sẵn trong V2 | S | Chỉ set cho 20-30 SP để không nhàm |
| Phân bổ brand không đều (hero brand per category) | Apple iPhone dẫn đầu điện thoại, Dell/Asus laptop — natural feel | S | 3-4 brand chính + 1-2 nhỏ per category |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Fake Unsplash URL tự chế | Broken image worse than placeholder | Dùng pattern `https://images.unsplash.com/photo-{id}?w=800&auto=format&fit=crop&q=80` đã verify |
| Flyway V7 seed quá lớn (> 500 rows) | Slow startup, khó maintain | Giữ đúng ~100 SP, dùng INSERT đơn giản không PL/pgSQL |
| category mới ngoài 5 đã định | Risk phá brand/price filter đang có | Chỉ dùng đúng 5 category: điện thoại, laptop, chuột, bàn phím, tai nghe — đổi tên cũ (electronics/fashion/household/books/cosmetics) |
| Inventory seed riêng rẽ khỏi product seed | Orphan inventory_items — đã bị bug v1.1 | Pair product seed với inventory seed cùng migration hoặc chú thích cross-service rõ |

### Dependencies

- Flyway version: tiếp tục từ V6 (product-service) → V7 seed mới
- inventory-service cần sync: mỗi prod-xxx cần inventory_item tương ứng
- FilterSidebar brand multi-select đã có → data brand phải nhất quán

### Edge Cases

- **Slug collision**: brand + model tên giống nhau → slug phải có suffix `-2` hoặc dùng UUID suffix
- **Image 404**: Unsplash photo ID thay đổi rất hiếm nhưng nên verify vài URL trước khi commit
- **Flyway checksum**: Đừng edit V100__seed_dev_data.sql cũ → tạo V101 mới hoặc V7 product migration mới
- **Category ID mismatch**: categories hiện tại là `cat-electronics` etc. — seed mới phải match hoặc thay thế hoàn toàn

---

## Axis 2 — STORAGE Audit + Cart → DB Migration

### Hiện trạng đã xác nhận

`sources/frontend/src/services/cart.ts` dùng `localStorage['cart']` hoàn toàn client-side. Đây là **confirmed data leak** — cart không persist khi user đổi device, và cart của user A có thể bị đọc bởi browser tab khác cùng origin.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Grep toàn FE: classify mọi localStorage/sessionStorage usage | Không thể migrate mà không biết đang store gì | S | 6 files đã xác định: cart.ts, auth.ts, http.ts, token.ts, AuthProvider.tsx, profile/settings/page.tsx |
| Cart persist DB per user_id | Authenticated user mất cart khi đổi device = bad UX | M | Cần `/api/orders/cart` hoặc `/api/cart` endpoint mới ở order-service |
| Merge guest cart → DB khi login | User add vài item trước login → sau login cart phải giữ | M | Pattern chuẩn: localStorage merge-on-login rồi clear localStorage |
| Cart load từ DB khi page refresh | Đây là mục tiêu cuối cùng của migration | M | Giữ localStorage làm optimistic cache, DB làm source of truth |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Last-write-wins conflict resolution (2 tabs cùng user) | Tránh silent cart corruption | M | Dùng `updatedAt` timestamp để detect stale |
| Guest cart → localStorage vẫn OK | Không cần login để browse và add | S | Không thay đổi UX cho guest |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Real-time sync WebSocket cart | Out of scope v1.3, overkill | Polling or reload-on-focus đủ |
| Migrate token/JWT ra khỏi localStorage | Security concern nhưng không visible — defer per visible-first policy | Ghi nhận trong audit report nhưng không thực hiện v1.3 |
| IndexedDB | Overkill cho cart size này | localStorage + DB đủ |
| Xóa guest cart localStorage hoàn toàn | Guest UX bị phá | Giữ localStorage cho guest, DB cho logged-in |

### Dependencies

- Auth service: cần `user_id` từ JWT để key cart DB
- Order service: nơi hợp lý nhất host cart endpoint (đã có order_items schema)
- Hoặc: tạo cart table riêng trong order-service DB

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| 2 browser tabs cùng user, add item đồng thời | Race → cart count sai | Server-side atomic upsert (INSERT ... ON CONFLICT DO UPDATE) |
| User logout rồi login lại | localStorage cart còn, DB cart khác | Merge strategy: DB wins cho logged-in items, ask user nếu conflict |
| Stock thay đổi từ khi add vào cart đến checkout | Cart stale | Re-validate stock tại checkout (đã có pattern từ v1.1) |
| Cart expire | DB cart tích tụ vô hạn | TTL 30 ngày hoặc clear on checkout — note cho implementation |
| localStorage ['cart'] bị corrupt JSON | Hiện tại: try-catch return [] | Giữ pattern này |

### Phân loại storage hiện tại (từ code analysis)

| File | Key | Type | Action |
|------|-----|------|--------|
| cart.ts | `cart` | CartItem[] | Migrate → DB (confirmed scope) |
| token.ts / auth.ts | `access_token` | JWT string | Security concern, defer v1.4 (không visible) |
| AuthProvider.tsx | (reads token) | - | No change needed |
| http.ts | (reads token) | - | No change needed |
| profile/settings | TBD | - | Audit — likely form state không persist |

---

## Axis 3 — ADMIN Completion (4 Charts + Low-Stock Alert + Admin Order Detail Items)

### Hiện trạng

Admin dashboard đã có 4 KPI cards (v1.2). Admin order detail page thiếu order_items breakdown.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Revenue/time chart (line/bar) | Admin cần thấy trend doanh thu, không chỉ total | M | Cần endpoint aggregation theo ngày/tuần/tháng |
| Top products chart (bar horizontal) | Biết SP bán chạy nhất để điều chỉnh inventory | M | JOIN order_items + products, group by product_id |
| Order status pie chart | Visual breakdown PENDING/CONFIRMED/SHIPPING/DELIVERED/CANCELLED | S | Dữ liệu đã có từ order stats endpoint |
| Low-stock alert section | Admin cần biết SP sắp hết hàng trước khi xảy ra | S | threshold = stock <= 5, cần query inventory-service |
| Admin order detail: hiển thị order_items | Đây là bug cần fix — items table đã có nhưng FE không render | M | AdminOrder interface hiện không có `items[]` |
| Signups chart (line) | Track user growth theo thời gian | M | Cần aggregation endpoint user-service |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Date range picker cho charts | Filter revenue theo period | M | react-datepicker hoặc custom input[type=date] |
| Export CSV button | Admin muốn data ra Excel | L | Defer — quá scope cho v1.3 |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Real-time dashboard WebSocket | Overkill, out of scope | Manual refresh hoặc 60s polling nếu cần |
| Recharts full animation | Quá nặng, thêm bundle size | Recharts với `isAnimationActive={false}` hoặc Chart.js minimal |
| D3.js custom charts | Tốn thời gian build, khó maintain | Recharts hoặc Chart.js đủ |
| Export to PDF | Ngoài scope | Ghi nhận backlog |

### Dependencies

- Order service: cần aggregate endpoints `/api/orders/stats/revenue?period=daily`
- Product service: cần inventory query low-stock (cross-service call qua gateway)
- Chart library: Recharts (đã common trong Next.js ecosystem)

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| Không có đơn hàng nào | Chart render rỗng crash | Empty state: "Chưa có dữ liệu" |
| Revenue = 0 ngày specific | Line chart dip về 0 — OK | Không phải edge case cần handle riêng |
| Timezone mismatch BE/FE | Revenue ngày "sai" | Chuẩn hóa UTC trong query, FE hiển thị local |
| Admin order detail: items[] null/empty | FE crash nếu map null | Defensive: `items ?? []` |
| Low-stock threshold: 0 vs null stock | Inventory không có entry → considered out-of-stock? | Clarify: chỉ alert khi stock IS NOT NULL AND stock <= threshold |

---

## Axis 4 — REVIEW Polish (REV-04 Author Edit/Delete + Sort + Admin Moderation)

### Hiện trạng

Reviews đã có: create (verified-buyer), display với avg_rating, XSS-safe render. Thiếu: edit/delete, sort options, admin hide/approve.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Author edit review | User viết sai → không có cách sửa = frustrating | M | PATCH /api/products/{pid}/reviews/{rid}, chỉ author hoặc admin |
| Author delete review | User muốn xóa → không được = trapped | S | DELETE soft-delete (add `deleted` column) hoặc hard delete |
| Sort by newest (default) | Expected behavior mọi review system | S | `ORDER BY created_at DESC` — có thể đã là default |
| Sort by highest rating | Người mua muốn xem đánh giá 5 sao trước | S | `ORDER BY rating DESC, created_at DESC` |
| Sort by lowest rating | Người mua skeptical muốn xem review xấu | S | `ORDER BY rating ASC, created_at DESC` |
| Admin hide review | Toxic/spam review cần ẩn mà không xóa (audit trail) | M | `hidden` boolean column + admin-only endpoint |
| Admin approve/unhide | Đảo ngược hide | S | Cùng endpoint, toggle field |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Sort by "Hữu ích nhất" | Amazon/Shopee pattern nổi tiếng | L | Cần helpful_votes table — DEFER v1.4 |
| Review image upload | Richer review content | L | Multipart upload — DEFER v1.4 |
| Verified buyer badge prominent | Tăng trust | S | Đã có verified_buyer flag, cần UI badge |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Helpful vote / "Đánh dấu hữu ích" | Scope v1.4 — cần separate votes table | Ghi nhận backlog, không xây v1.3 |
| Vendor reply to review | B2B feature không cần ở B2C demo này | Defer indefinitely |
| Review pagination client-side | Data lớn sẽ chậm | Server-side pagination đã có pattern từ product list |
| Edit rating (chỉ edit content) | Controversial — Shopee cho phép, Amazon không | Scope quyết định: cho phép edit cả rating, không phức tạp hơn |

### Dependencies

- reviews table đã có (`id`, `product_id`, `user_id`, `reviewer_name`, `rating`, `content`, `created_at`, `updated_at`)
- Cần thêm: `hidden` BOOLEAN DEFAULT FALSE, `updated_at` đã có để track edit
- Auth: chỉ author (user_id match) mới được edit/delete; admin có thể hide bất kỳ
- Flyway: V7 hoặc V8 alter reviews table thêm `hidden` column

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| User edit review sau khi đơn hàng hết verified | Vẫn allow nếu đã từng verified — đây là điều hiển nhiên | Không re-check eligibility tại edit, chỉ tại create |
| Admin hide review nhưng user chưa biết | User thấy review biến mất không hiểu | Optional: email notification — defer |
| Sort URL state reset khi back | UX friction | Giữ sort trong URL query param (`?sort=newest`) |
| Empty content edit | Có thể muốn chỉ giữ rating | Validate: content min 1 ký tự hoặc nullable — quyết định tại implementation |

---

## Axis 5 — AI Chatbot Claude API MVP

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Customer FAQ (vận chuyển, đổi trả, thanh toán) | Câu hỏi lặp lại nhất — chatbot giải quyết 70-85% | M | System prompt với FAQ context |
| Product Q&A ("máy này pin bao nhiêu mAh?") | User hỏi chi tiết SP mà FE không hiển thị | M | RAG-lite: inject product context từ API vào prompt |
| Product recommendation ("tôi cần laptop văn phòng 15tr") | Differentiator mạnh — upsell/cross-sell tự động | M | Inject catalog summary (top 20 SP) vào context |
| Streaming UI | Perceived latency giảm rõ rệt — user thấy text xuất hiện dần | M | SSE hoặc ReadableStream từ Claude API |
| Chat history persist DB | History biến mất sau refresh = useless chatbot | M | Cần chat_sessions + chat_messages table |
| KHÔNG agentic tool-use | Scope lock — chatbot chỉ read-only, không đặt hàng, không sửa dữ liệu | N/A | System prompt explicit: "chỉ tư vấn, không thực hiện action" |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Admin "suggest reply template" | Admin xem câu hỏi phổ biến, AI suggest template → admin customize | M | Separate admin endpoint, không phải real-time |
| Multi-turn context | Chatbot nhớ context trong session (sản phẩm vừa xem) | M | Pass recent messages vào Claude API `messages[]` array |
| Chat bubble persistent | Floating button, không block browsing | S | Fixed position component |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Agentic tool-use (đặt hàng qua chat) | Scope lock — risk unintended actions, phức tạp auth trong LLM context | System prompt constraint + no tool definitions |
| Voice input/output | Out of scope | Text-only v1.3 |
| Multi-language switching | Overkill | Tiếng Việt mặc định, English nếu user hỏi English |
| Train custom model | Không cần, Claude API đủ | Fine-tuning không cần với good prompt |
| Store full conversation server-side vô hạn | Storage grows unbounded | TTL 30 ngày hoặc cap 50 messages per session |
| Rate limit per IP | Infrastructure concern, invisible | Rate limit per user_id: max 50 messages/ngày đủ cho demo |

### Dependencies

- Claude API key (đã có `claude-api` skill confirmed)
- Cần new service hoặc extend existing: `chat-service` mới hoặc endpoint trong `product-service`
- product-service: context injection cần fetch top products/categories
- DB: `chat_sessions(id, user_id, created_at)` + `chat_messages(id, session_id, role, content, created_at)`
- FE: streaming fetch + SSE consumer component

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| Claude API rate limit (per minute tokens) | 429 error visible to user | Retry với backoff + user message "Hệ thống bận, thử lại sau" |
| Context window vượt quá | Dài chat → API error | Truncate oldest messages, giữ system prompt + last N turns |
| User hỏi off-topic (tin tức, code, etc.) | Chatbot nên từ chối gracefully | System prompt: "Chỉ trả lời câu hỏi về sản phẩm và dịch vụ" |
| Concurrent requests cùng session | Stream interleave | Session-level lock hoặc queue per session |
| Unauthenticated user sử dụng chatbot | Session không có user_id | Allow guest chat nhưng không persist history |
| Product data stale trong context | AI recommend SP đã hết hàng | Inject stock status vào context, refresh mỗi session start |

### Complexity Rating: L

Đây là axis phức tạp nhất vì:
1. Cần new service + DB migration
2. Streaming implementation FE+BE
3. Context management (product data injection)
4. Claude API integration + error handling

---

## Axis 6 — ORDER-DETAIL Items Fix

### Hiện trạng

`order_items` table đã có (V2__add_order_items.sql). Schema đầy đủ: `order_id`, `product_id`, `product_name`, `quantity`, `unit_price`, `line_total`. Vấn đề: FE `/profile/orders/[id]` và `/admin/orders/[id]` không render items — AdminOrder interface thiếu `items[]`.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `/profile/orders/[id]` hiển thị full line items | User xem lịch sử mua nhưng không thấy mua gì = confusing | M | GET /api/orders/{id} phải include items[] |
| `/admin/orders/[id]` hiển thị full line items | Admin xử lý đơn không thấy items = không làm được việc | M | Cùng fix BE, thêm items[] vào AdminOrderDto |
| Subtotal per line item | qty × unit_price = line_total | S | Data đã có trong DB |
| Order total reconciliation | Sum line_items phải khớp order.total | S | Verify tại implementation |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Product thumbnail per item trong order detail | Visual recognition, không chỉ text | M | Cần cross-service call product-service để fetch thumbnail |
| Link từ order item → product page | Đặt hàng lại 1 click | S | /products/{slug} — cần store slug tại order time hoặc fetch |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Hiển thị product image full resolution | Không cần trong order detail | thumbnail_url 80px đủ |
| Editable order items | Order đã đặt không được sửa items | Chỉ admin change STATUS, không change items |

### Dependencies

- order-service: endpoint GET /api/orders/{id} phải join order_items và return items[]
- Order DTO hiện tại cần extend: `List<OrderItemDto> items`
- FE: Order type cần update với `items?: OrderItemDto[]`

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| Order cũ (pre-V2 migration) không có items | items[] trả về [] | Defensive: empty state "Không có thông tin chi tiết" |
| product_name đã bị thay đổi sau khi đặt | Snapshot trong order_items phải không đổi | Đúng rồi — `product_name` là snapshot, không dynamic lookup |
| line_total rounding mismatch | 0.01đ diff do float | Use BigDecimal/DECIMAL(12,2) consistent — đã có |

### Complexity Rating: M (debug + BE fix + FE update)

---

## Axis 7 — COUPON System

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| % off coupon | Giảm giá theo phần trăm — loại phổ biến nhất | M | `discount_type = PERCENT`, `discount_value = 10` → 10% off |
| Fixed amount coupon | Giảm cố định VNĐ | M | `discount_type = FIXED`, `discount_value = 50000` |
| Expiry date | Coupon hết hạn tự động | S | `expires_at TIMESTAMP` |
| Min order value | Không áp dụng cho đơn nhỏ hơn ngưỡng | S | `min_order_value DECIMAL` |
| Max usage per coupon | Tổng số lần dùng toàn hệ thống | M | `max_uses INT`, `used_count INT` |
| Max usage per user | 1 user không dùng 1 mã nhiều lần | M | `coupon_usages(coupon_id, user_id, order_id)` table |
| 1 mã per đơn (KHÔNG stack) | Scope lock — không cho combine coupons | S | Validate tại checkout: nếu đã có coupon → reject second |
| Admin CRUD /admin/coupons | Tạo/sửa/xóa mã | M | List + create + edit + deactivate |
| FE checkout input field | User nhập mã → xem discount preview | M | Input + validate API call + update total preview |
| Validation message rõ ràng | "Mã hết hạn", "Đơn chưa đạt tối thiểu", "Đã dùng rồi" | S | Error messages per reason |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Auto-apply best coupon | Hệ thống chọn mã tốt nhất | L | Defer v1.4 |
| Coupon activity log | Admin xem ai dùng mã khi nào | S | Có từ coupon_usages table nếu build đúng |
| Percentage cap (ví dụ: max 100k dù 20%) | Bảo vệ margin | M | `max_discount_amount DECIMAL` optional field |

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Stack multiple coupons | Scope lock — "KHÔNG stack" | Enforce 1 mã/đơn cả FE và BE |
| Coupon per product/category | Tăng complexity không cần thiết | Apply cho toàn đơn |
| Referral code system | Riêng biệt hoàn toàn | Defer |
| Coupon auto-generate bulk | Out of scope | Admin tạo manual đủ |
| Time-based flash sale | Cần scheduler — overkill | Đủ với expiry date |

### Dependencies

- Order service: cần biết coupon discount khi create order (lưu vào orders table: `coupon_code`, `discount_amount`)
- New service hoặc endpoint: `coupon-service` (mới) hoặc endpoint trong `order-service`
- Quyết định nơi host: order-service hợp lý nhất vì coupon liên quan đến order total
- FE checkout flow: cần thêm coupon input step + re-calculate total
- DB: orders table cần thêm `coupon_code VARCHAR(50)`, `discount_amount DECIMAL(12,2)`

### Edge Cases

| Edge Case | Risk | Mitigation |
|-----------|------|------------|
| Coupon hết hạn trong khi user đang checkout | User điền mã OK, submit thì fail | Validate lại tại POST /api/orders, không chỉ tại input |
| Race condition: 2 users dùng mã cùng lúc (max_uses = 1) | Cả 2 pass validation, cả 2 place order | Atomic increment: `UPDATE coupons SET used_count = used_count + 1 WHERE used_count < max_uses` + check rows_affected = 1 |
| User đặt order rồi cancel → coupon count không giảm lại | used_count inflated | Nên decrement khi cancel — note cho implementation |
| Coupon code case-insensitive | "SUMMER10" vs "summer10" | Normalize to UPPER at save + validate |
| Discount lớn hơn order total | Negative total | Cap: `discount_amount = min(calculated_discount, order_total)` |
| Max usage per user kiểm tra khi user chưa đăng nhập | Guest không có user_id | Coupon chỉ apply cho authenticated user |

### Complexity Rating: L (new schema + BE validation + FE checkout integration)

---

## Feature Dependencies Map

```
SEED catalog (Axis 1)
  └→ ADMIN completion (Axis 3): top products chart cần data có sẵn
  └→ AI Chatbot (Axis 5): product context injection cần catalog có data thật

STORAGE audit (Axis 2)
  └→ Cart → DB: depends on Auth (user_id từ JWT) + Order service (cart table)

ORDER-DETAIL fix (Axis 6)
  └→ ADMIN completion (Axis 3): admin order detail items là subset của Axis 6 fix

REVIEW polish (Axis 4)
  └→ Standalone, chỉ depends product-service reviews table (V4 đã có)

AI Chatbot (Axis 5)
  └→ SEED catalog (Axis 1): recommendation quality depends on catalog richness
  └→ Cần product-service context (fetch SP list)

COUPON system (Axis 7)
  └→ Order service: cần orders table có coupon fields
  └→ Checkout FE: cần order total calculation flow
  └→ Auth: coupon per user cần authenticated session
```

## MVP Priority Order (visible-first policy)

1. **Axis 6 — ORDER-DETAIL items fix** (S-M, debug): Unblocks admin UX + user UX ngay. Bug fix không tốn nhiều thời gian, impact lớn.
2. **Axis 1 — SEED catalog** (S-M): Foundation cho mọi demo — catalog rỗng = không demo được chatbot, charts, filters
3. **Axis 3 — ADMIN completion charts** (M): Visible và impressive, admin use case rõ ràng
4. **Axis 4 — REVIEW polish** (M): Carries over từ v1.2 backlog, scope rõ
5. **Axis 7 — COUPON system** (L): Commerce feature cốt lõi, visible tại checkout
6. **Axis 2 — STORAGE audit + cart→DB** (M): UX improvement, less visible than coupon but important for data integrity
7. **Axis 5 — AI Chatbot** (L): Most complex, should be last — depends on good catalog data

## Sources

- [Ecommerce Catalog Management Guide 2026](https://odoopim.com/blog/ecommerce-catalog-management/)
- [How To Design A Coupon Management System](https://targetbay.com/blog/design-a-coupon-management-system/)
- [What a good coupon system really looks like](https://www.voucherify.io/blog/5-traits-of-a-good-coupon-management-system)
- [Building a Shopping Cart: Session-Based vs Database-Backed](https://medium.com/@sohail_saifi/building-a-shopping-cart-session-based-vs-database-backed-745260091f30)
- [AI Chatbots in E-Commerce 2026](https://www.xictron.com/en/blog/ai-chatbots-e-commerce-2026/)
- [Ecommerce Analytics Dashboard 2026](https://www.referralcandy.com/blog/the-complete-guide-to-ecommerce-analytics-dashboard-setup-in-2026/)
- [Product Reviews Management 2026](https://www.xictron.com/en/blog/product-reviews-online-shop-management-2026/)
- [Claude API Documentation](https://platform.claude.com/docs/en/home)
