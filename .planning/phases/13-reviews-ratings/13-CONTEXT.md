# Phase 13: Reviews & Ratings - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship reviews end-to-end tại PDP với verified-buyer eligibility + XSS-safe rendering + avg_rating cached. Gồm:
1. Backend: V4 reviews table (product-svc) + V5 avg_rating/review_count cached cols trên products
2. Backend: Eligibility internal endpoint (order-svc) + review CRUD (product-svc)
3. Frontend: Reviews tab PDP — pre-check eligibility, form (star widget + textarea), list paginated

**Deferred (v1.3):** REV-04 author edit/delete

</domain>

<decisions>
## Implementation Decisions

### Cross-service Eligibility

- **D-01:** product-svc verify buyer eligibility bằng **RestTemplate HTTP call** đến order-svc internal endpoint (Docker network, không qua gateway). URL: `http://order-service:8080/internal/orders/eligibility?userId={}&productId={}`.
- **D-02:** order-svc thêm endpoint `/internal/orders/eligibility` (GET, không route qua gateway). Query: `SELECT COUNT(*) FROM orders o JOIN order_items i ON i.order_id = o.id WHERE o.user_id=? AND o.status='DELIVERED' AND i.product_id=?`. Trả `{ eligible: boolean }`.
- **D-03:** Eligibility check xảy ra 2 lần: (1) FE pre-check khi load tab Reviews; (2) BE re-check inline khi user POST review. BE không trust FE pre-check result.

### Review Form UX

- **D-04:** Star rating widget: **CSS interactive** — 5 stars clickable, hover highlight, selected state. Không dùng lib. Hidden number input 1-5 cho rhf register. Rating là bắt buộc.
- **D-05:** Review form đặt **trong reviews tab, phía trên list** (form hiển thị nếu eligible → form ở top; list bên dưới).
- **D-06:** Textarea content: **optional, max 500 ký tự**. Min không bắt buộc (chỉ rating required). Validate client-side counter + BE constraint.
- **D-07:** Post-submit flow: **reset form + toast 'Đã gửi đánh giá' + reload review list** (fetch lại trang 1). Form vẫn visible sau submit.

### Eligibility FE Flow

- **D-08:** FE gọi `GET /api/products/{id}/reviews/eligibility` khi user load tab Reviews (user logged-in). Nếu `eligible: true` → show form; nếu `eligible: false` → hide form, show hint "Chỉ user đã mua mới có thể đánh giá sản phẩm này".
- **D-09:** User **chưa đăng nhập**: không gọi eligibility endpoint, hiển thị hint "Đăng nhập để đánh giá sản phẩm." + link `/login?redirect=/products/{slug}`. Review list vẫn hiển thị bình thường.

### displayName Snapshot

- **D-10:** Snapshot `reviewerName` từ **JWT claim 'name'** (fullName). Không call user-svc. Planner/researcher cần inspect auth-svc JwtProvider để xác nhận 'name' claim tồn tại; nếu thiếu → thêm vào token generation (auth-svc).
- **D-11:** Review entity lưu: `reviewer_name VARCHAR(150)` snapshot (bất biến sau insert — REV-04 deferred nên không cần update path).

### avg_rating Recompute

- **D-12:** Sau mỗi review insert (và delete nếu cần): product-svc service layer gọi `SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id=?` rồi UPDATE products SET avg_rating=?, review_count=? trong **cùng transaction**. Không drift (recompute from scratch, không increment).
- **D-13:** V5 migration: `ALTER TABLE product_svc.products ADD COLUMN avg_rating DECIMAL(3,1) DEFAULT 0, ADD COLUMN review_count INT DEFAULT 0`.

### XSS Safety

- **D-14:** Backend OWASP sanitize content: dùng **Jsoup.clean(content, Safelist.none())** — strip toàn bộ HTML tags. Plain text only. FE render bằng `{content}` text node (không dangerouslySetInnerHTML).

### Claude's Discretion

- OWASP sanitize library version: Claude chọn Jsoup stable compatible với Spring Boot 3.x trong product-svc pom.xml
- Pagination UX cho review list: load trang 1 khi tab open, "Xem thêm" button hoặc numbered pages — Claude chọn approach đơn giản hơn
- Review list item layout: avatar placeholder, reviewer name, star display (read-only), date, content — Claude format cho clean

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents PHẢI đọc các file dưới trước khi plan/implement.**

### Project-level
- `.planning/PROJECT.md` — visible-first priority, demo distributed system
- `.planning/REQUIREMENTS.md` §REV-01, §REV-02, §REV-03 — full requirement spec
- `.planning/ROADMAP.md` §Phase 13 — Goal + 4 Success Criteria + Flyway V-number table (V4 reviews, V5 avg_rating)
- `.planning/STATE.md` — locked decisions carry-over

### Research artifacts
- `.planning/research/PITFALLS.md` — pitfalls chung (inter-service call patterns, XSS)
- `.planning/research/STACK.md` — tech stack reference

### Codebase intel
- `.planning/codebase/CONVENTIONS.md` — ApiResponse envelope, error code pattern
- `.planning/codebase/INTEGRATIONS.md` — gateway routes, service-to-service

### Backend code (đụng tới Phase 13)
- `sources/backend/product-service/src/main/resources/db/migration/` — V1-V3 tồn tại; cần tạo V4__create_reviews.sql + V5__add_avg_rating_review_count.sql
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java` — cần thêm avg_rating + review_count fields
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java` — pattern endpoint hiện tại; thêm ReviewController riêng
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java` — extractUserIdFromBearer + JWT claims pattern
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java` — thêm /internal/orders/eligibility endpoint
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemEntity.java` — có productId field (đã confirm)
- `sources/backend/api-gateway/src/main/resources/application.yml` — gateway routes (KHÔNG thêm route /internal/* ra ngoài)

### Auth-svc (cần inspect)
- `sources/backend/auth-service/` — JwtProvider: kiểm tra 'name' claim (fullName) có trong JWT không; thêm nếu thiếu

### Frontend code (đụng tới Phase 13)
- `sources/frontend/src/app/products/[slug]/page.tsx` — PDP hiện tại, reviews tab có placeholder; implement thực tế
- `sources/frontend/src/types/index.ts` — Review interface đã có (id, userId, userName, productId, rating, comment, createdAt); align với BE DTO
- `sources/frontend/src/services/products.ts` — thêm listReviews(), submitReview(), checkEligibility()

### Prior phase context
- Phase 11 D-03: server-side validation pattern
- Phase 10: rhf+zod form pattern — áp dụng cho review form
- Phase 9: JWT Bearer → claims.sub pattern — ReviewController dùng same approach

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `JwtRoleGuard.extractUserIdFromBearer()` — pattern lấy userId + claims từ JWT; dùng trong ReviewController
- `useToast()` hook — toast success 'Đã gửi đánh giá' / error
- `RetrySection` component — fallback khi fetch reviews fail
- `Button`, `Badge` UI components — dùng trong review list items
- rhf + zod pattern (Phase 10) — áp dụng cho review form (rating + content fields)
- `httpGet`, `httpPost` từ `services/http.ts` — fetch reviews list + submit review

### Established Patterns
- `ApiResponse<T>` envelope — tất cả BE response đều wrap
- Error codes dạng string `REVIEW_NOT_ELIGIBLE` (422) — consistent với `ADDRESS_LIMIT_EXCEEDED`
- `PaginatedResponse<T>` type — dùng cho review list response (page/size)
- Soft-delete + `@SQLRestriction` pattern (ProductEntity) — ReviewEntity cũng dùng (nếu cần delete sau REV-04)

### Integration Points
- Gateway route `/api/products/**` → product-svc đã có — review endpoints `/api/products/{id}/reviews` và `/api/products/{id}/reviews/eligibility` tự động route theo pattern này
- `/internal/orders/eligibility` endpoint ở order-svc: KHÔNG add vào gateway (Docker-internal only)
- PDP tab 'reviews': `activeTab === 'reviews'` block đã có placeholder — replace placeholder bằng real component

</code_context>

<specifics>
## Specific Ideas

- Eligibility endpoint path: `/api/products/{id}/reviews/eligibility` (GET, requires auth) → product-svc nhận, gọi order-svc internal
- Internal order-svc URL: `http://order-service:8080/internal/orders/eligibility?userId={}&productId={}` (không qua gateway)
- Star widget: 5 `<button>` elements với aria-label="X sao", filled/unfilled class, hover state CSS
- Review list item: `[Avatar placeholder] [reviewerName] [★★★★☆] [createdAt relative]` + content paragraph
- Hint for non-buyer: "ℹ️ Chỉ user đã mua sản phẩm này mới có thể đánh giá."
- Hint for not logged in: "Đăng nhập để đánh giá sản phẩm." với link `/login?redirect=/products/{slug}`
- avg_rating display: 1 decimal (4.2★) trên product card + PDP header (đã có slot từ Phase 15 spec nhưng cần data thật từ Phase 13)

</specifics>

<deferred>
## Deferred Ideas

- **REV-04 author edit/delete** — PATCH/DELETE `/api/reviews/{id}` ownership check — defer v1.3 (locked decision)
- **Review images** — Review type đã có `images?: string[]` nhưng upload mechanism chưa có — defer v1.3
- **Duplicate review prevention FE** — Hide form nếu user đã review (cần thêm state check từ eligibility endpoint hoặc review list) — Claude's Discretion nếu simple, defer nếu phức tạp

</deferred>

---

*Phase: 13-reviews-ratings*
*Context gathered: 2026-04-27*
