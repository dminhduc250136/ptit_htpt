# Phase 21: Hoàn Thiện Reviews - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Mode:** `--auto` (Claude chọn recommended defaults — review CONTEXT.md trước khi plan)

<domain>
## Phase Boundary

Hoàn thiện vòng đời review trên nền Phase 13 với 3 trục:
1. **REV-04 — Author edit/delete:** Tác giả review chỉnh sửa trong cửa sổ 24h kể từ publish và xoá mềm (`deleted_at`) bất kỳ lúc nào.
2. **REV-05 — Sort controls:** Người dùng sort danh sách review theo `newest` / `rating_desc` / `rating_asc` qua dropdown FE + query param BE (`?sort=`). Helpful sort defer (chưa có votes).
3. **REV-06 — Admin moderation:** Trang `/admin/reviews` list/filter visible-hidden + actions hide/unhide/hard-delete cho mọi review.

**KHÔNG nằm trong scope (defer):**
- Helpful votes UI/BE (REV-05 phần "helpful")
- Admin keyword search trên /admin/reviews
- Bulk actions (hide/delete nhiều review cùng lúc)
- Audit log cho admin moderation actions
- Edit history (giữ revisions)

</domain>

<decisions>
## Implementation Decisions

### A. Edit/Delete authorization rules (REV-04)

- **D-01:** Edit window = **trong vòng 24h kể từ `created_at`** của review. Sau 24h → button "Sửa" disabled với tooltip "Đã quá thời hạn chỉnh sửa (24h)". *Lý do:* spec ghi "Edit chỉ chủ review hoặc 24h sau publish (configurable)" — diễn giải là grace period 24h để sửa, đồng nhất convention Amazon/Shopee. Configurable mở đường cho v1.4 nếu muốn nới.
- **D-02:** Window value cấu hình tại `application.yml` key `app.reviews.edit-window-hours: 24` (Spring `@Value("${app.reviews.edit-window-hours:24}")`). FE cũng đọc giá trị từ BE response (xem D-04) để khoá button đúng — không hard-code 24h ở FE.
- **D-03:** Delete (author) = **soft-delete** ghi `deleted_at = now()`. KHÔNG có time window cho delete (xoá lúc nào cũng được). Trả 204 No Content.
- **D-04:** Edit endpoint: `PATCH /api/products/{productId}/reviews/{reviewId}` — body `{ rating?, content? }`. Auth Bearer required.
  - 403 nếu caller không phải `userId` của review.
  - 422 với code `REVIEW_EDIT_WINDOW_EXPIRED` nếu past 24h (BE re-check, FE state chỉ là advisory).
  - 422 `REVIEW_NOT_FOUND` nếu review đã soft-delete hoặc không tồn tại.
  - Sanitize content lại bằng Jsoup (D-14 Phase 13). Sửa rating → trigger recompute avg_rating (D-08).
- **D-05:** Delete endpoint (author): `DELETE /api/products/{productId}/reviews/{reviewId}`. Auth Bearer required, 403 nếu không phải owner. Set `deleted_at` + recompute avg_rating. Idempotent (xoá review đã deleted → 404).
- **D-06:** Sau soft-delete, **author CAN re-review** sản phẩm. UNIQUE constraint cũ `uq_review_product_user (product_id, user_id)` block redo → cần migrate sang **partial unique index** `WHERE deleted_at IS NULL`. Xem D-20.

### B. Visibility & avg_rating recompute semantics

- **D-07:** Public review list (`GET /api/products/{id}/reviews`) lọc: `WHERE deleted_at IS NULL AND hidden = FALSE`. Áp dụng cho cả query thường lẫn count.
- **D-08:** `avg_rating` + `review_count` recompute **loại bỏ cả deleted lẫn hidden**. *Lý do:* hidden review = vi phạm moderation, không nên ảnh hưởng điểm sản phẩm. Trigger recompute mỗi khi: create / edit (nếu rating thay đổi) / author-delete / admin-hide / admin-unhide / admin-delete. Repository method `computeStats(productId)` cập nhật điều kiện WHERE thêm `deleted_at IS NULL AND hidden = FALSE`.
- **D-09:** Author KHÔNG thấy review đã soft-delete của chính mình trong public list (sạch — họ tạo lại được). Hidden review cũng KHÔNG hiển thị cho author (transparent moderation theo spec REV-06: "user không thấy nhưng admin vẫn list được").
- **D-10:** Admin `/admin/reviews` list KHÔNG lọc `hidden`, KHÔNG lọc `deleted_at`:
  - Admin thấy mọi review **chưa hard-delete** (cả hidden lẫn soft-deleted bởi author).
  - Filter UI cho admin: `Tất cả | Đang hiện | Đã ẩn | Đã xoá (author)`. Default `Tất cả`.
  - *Lý do:* admin cần thấy review author đã xoá để phát hiện pattern abuse (soft-delete để né moderation).

### C. Sort UX & API contract (REV-05)

- **D-11:** Sort options FE (3 lựa chọn — helpful defer):
  - `Mới nhất` → `sort=newest` (default — giữ behavior Phase 13)
  - `Đánh giá cao nhất` → `sort=rating_desc`
  - `Đánh giá thấp nhất` → `sort=rating_asc`
- **D-12:** API: `GET /api/products/{id}/reviews?page=&size=&sort=newest|rating_desc|rating_asc`. BE switch case → `Sort.by(...)`:
  - `newest` → `created_at DESC`
  - `rating_desc` → `rating DESC, created_at DESC` (tie-break newest)
  - `rating_asc` → `rating ASC, created_at DESC`
  - Invalid sort value → fallback `newest` (KHÔNG throw 400 — graceful).
- **D-13:** FE control: native `<select>` dropdown đặt **ngay phải `<h3>` "Đánh giá từ khách hàng (N)"** trong `ReviewList.tsx`. Đổi sort → fetch page 0 + reset list. URL persistence: `?sort=` ghép vào PDP query string qua `router.replace()` (không scroll, không reload). Default sort không cần ghi vào URL.

### D. Admin moderation UI (REV-06)

- **D-14:** Page `/admin/reviews/page.tsx` — table layout match `/admin/products` pattern. Columns:
  - Sản phẩm (link `/products/{slug}` — cần resolve productId → slug, có thể batch fetch)
  - Reviewer (`reviewerName` + initial avatar)
  - Rating (★★★★☆ readOnly stars)
  - Trích đoạn content (truncate 60 ký tự + tooltip full)
  - Trạng thái (badge: `Hiện` xanh / `Ẩn` đỏ / `Đã xoá` xám)
  - Ngày tạo (relative)
  - Hành động (xem D-17)
- **D-15:** Filter: dropdown `Tất cả | Đang hiện | Đã ẩn | Đã xoá` (default `Tất cả`). KHÔNG có keyword search ở phase này.
- **D-16:** Pagination: server-side `?page=&size=20` (giữ nhất quán với /admin/products). FE dùng existing `Pagination` component (nếu chưa có thì reuse pattern `/admin/orders`).
- **D-17:** Actions inline mỗi row:
  - Nếu `hidden=false` → button "Ẩn" → `PATCH .../visibility { hidden: true }` → toast → refetch row.
  - Nếu `hidden=true` → button "Bỏ ẩn" → `PATCH .../visibility { hidden: false }` → toast → refetch row.
  - Button "Xoá" (đỏ) → `window.confirm("Xoá vĩnh viễn review này? Không thể hoàn tác.")` → `DELETE` → hard delete + recompute avg_rating → toast → refetch list.
  - Nếu row đã `deleted_at IS NOT NULL` (author đã xoá) → chỉ hiện button "Xoá" (cho phép admin hard-delete dọn DB), ẩn nút Hide/Unhide.
- **D-18:** Admin endpoints (route qua gateway `/api/admin/**` đã có):
  - `GET /api/admin/reviews?page=&size=&filter=all|visible|hidden|deleted` → `ApiResponse<Page<AdminReviewDTO>>`. Trả tất cả field + `hidden` + `deletedAt` + `productSlug` (BE join hoặc batch).
  - `PATCH /api/admin/reviews/{reviewId}/visibility` body `{ hidden: boolean }` → 200 + recompute avg_rating.
  - `DELETE /api/admin/reviews/{reviewId}` → **hard delete** (xoá row khỏi DB) + recompute avg_rating. Khác với author soft-delete: admin xoá vĩnh viễn để dọn spam.
- **D-19:** Authorization: dùng existing `JwtRoleGuard.requireAdmin()` pattern (như AdminProductController). Service-side check `roles` claim chứa `ADMIN`.

### E. Database migration (V7)

- **D-20:** V7 migration `V7__add_review_moderation_columns.sql`:
  ```sql
  ALTER TABLE product_svc.reviews
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

  ALTER TABLE product_svc.reviews DROP CONSTRAINT uq_review_product_user;

  CREATE UNIQUE INDEX uq_review_product_user_active
    ON product_svc.reviews(product_id, user_id)
    WHERE deleted_at IS NULL;

  CREATE INDEX idx_reviews_visibility
    ON product_svc.reviews(product_id, hidden, deleted_at);
  ```
  - ReviewEntity thêm fields `deletedAt: Instant?` + `hidden: boolean` + accessor methods + mutator `markDeleted()` / `setHidden(boolean)` / `applyEdit(rating, content)`.

### F. Frontend ReviewList changes

- **D-21:** `ReviewList.tsx` nhận thêm prop `currentUserId?: string` + `editWindowHours: number` (từ BE config response hoặc fetched once). Mỗi review mà `review.userId === currentUserId` → render thêm cụm action "Sửa | Xoá" cạnh `reviewDate`.
  - Button "Sửa" disabled (+ tooltip "Đã quá thời hạn chỉnh sửa (24h)") khi `Date.now() - createdAt > editWindowHours * 3600 * 1000`.
  - Button "Xoá" luôn enabled cho owner.
- **D-22:** Edit UX: **inline** — click "Sửa" → review item collapse content + render `<ReviewForm mode="edit" initialRating initialContent onCancel onSubmit>` ngay trong li đó. Reuse component `ReviewForm.tsx` với prop `mode: 'create' | 'edit'`. Submit → PATCH → reflect mới → recompute avg trên header.
- **D-23:** Delete UX: `window.confirm("Xoá đánh giá này? Hành động không thể hoàn tác.")` → DELETE → toast "Đã xoá đánh giá" → refetch danh sách trang hiện tại + cập nhật avg_rating display (refetch product header).

### G. Service layer & API surface (BE summary)

- **D-24:** Mở rộng `ReviewController.java`:
  - Thêm `@PatchMapping("/{reviewId}")` (author edit)
  - Thêm `@DeleteMapping("/{reviewId}")` (author soft-delete)
  - Authorization helper `requireOwnerOrAdmin(claims, review)` reuse pattern parseToken.
- **D-25:** Tạo `AdminReviewController.java` mới — `RequestMapping("/admin/reviews")`. 3 endpoints (D-18). Dùng `JwtRoleGuard.requireAdmin(authHeader)`.
- **D-26:** Mở rộng `ReviewService.java`:
  - `editReview(reviewId, userId, rating, content)` — check owner + window + sanitize + recompute nếu rating thay đổi.
  - `softDeleteReview(reviewId, userId)` — check owner + set deletedAt + recompute.
  - `listAdminReviews(page, size, filter)` — không filter visibility.
  - `setVisibility(reviewId, hidden)` — set + recompute.
  - `hardDelete(reviewId)` — delete + recompute.
- **D-27:** Mở rộng `ReviewRepository.java`:
  - Thêm `findByProductIdAndDeletedAtIsNullAndHiddenFalseOrderByCreatedAtDesc` (+ rating sort variants) HOẶC chuyển sang JPQL với Specification để hỗ trợ multiple sort modes.
  - **Recommended:** dùng JPA `Sort` parameter — chỉ cần một method `findVisibleByProductId(String productId, Pageable pageable)` với `Pageable` đã chứa Sort.
  - `computeStats` cập nhật WHERE clause: `r.deletedAt IS NULL AND r.hidden = false`.
  - Thêm `existsByIdAndUserIdAndDeletedAtIsNull` cho ownership check fast-path nếu cần.

### Claude's Discretion

- Chính xác CSS layout của action buttons (Sửa/Xoá) trong review item — hài hoà với existing ReviewSection.module.css.
- Component confirmation dialog — reuse nếu repo đã có `ConfirmDialog`, fallback `window.confirm` nếu chưa có.
- Việc resolve `productSlug` cho admin list (join SQL vs batch fetch FE) — chọn approach đơn giản hơn sau khi planner inspect ProductRepository.
- Optimistic update vs full refetch sau edit/delete — chọn full refetch cho phase này (đơn giản, ít edge case).
- Toast wording chính xác (giữ tone Vietnamese hiện có).
- Kiểm tra `ProductEntity.updateRatingStats` đã handle `count = 0` (avg_rating reset về 0) — nếu chưa, fix trong cùng phase.

### Folded Todos

Không có todo nào trong backlog match phase này (cross_reference_todos: 0 matches).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents PHẢI đọc các file dưới trước khi plan/implement.**

### Project-level
- `.planning/PROJECT.md` §"Active (v1.3)" — visible-first priority, tone Vietnamese
- `.planning/REQUIREMENTS.md` §REV-04, §REV-05, §REV-06 — full requirement spec (lines 61-63)
- `.planning/ROADMAP.md` §"Phase 21: Hoàn Thiện Reviews" — Goal + 3 Success Criteria (lines 162-171)
- `.planning/STATE.md` — locked decisions carry-over

### Prior phase context (CRITICAL — Phase 13 nền móng)
- `.planning/phases/13-reviews-ratings/13-CONTEXT.md` — D-01..D-14 review foundation (eligibility, JWT, Jsoup sanitize, avg_rating recompute pattern)
- `.planning/phases/13-reviews-ratings/13-RESEARCH.md` — pitfalls đã giải quyết
- `.planning/phases/13-reviews-ratings/13-PATTERNS.md` — code conventions Phase 13

### Codebase intel
- `.planning/codebase/CONVENTIONS.md` — `ApiResponse` envelope, error code string pattern (`REVIEW_NOT_ELIGIBLE`)
- `.planning/codebase/INTEGRATIONS.md` — gateway routes `/api/admin/**` đã sẵn sàng

### Backend code (đụng tới Phase 21)
- `sources/backend/product-service/src/main/resources/db/migration/V4__create_reviews.sql` — schema gốc cần migrate (drop UNIQUE, add columns)
- `sources/backend/product-service/src/main/resources/db/migration/V5__add_avg_rating_review_count.sql` — recompute target
- `sources/backend/product-service/src/main/resources/db/migration/V6__alter_reviews_rating_to_integer.sql` — V-number gần nhất, V7 tiếp theo
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ReviewEntity.java` — cần add `deletedAt`, `hidden` + mutators
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ReviewController.java` — extend PATCH/DELETE author endpoints
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ReviewService.java` — extend edit/delete/admin methods + recompute scope update
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ReviewRepository.java` — update `computeStats` WHERE + add sort-aware finder
- `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java` — `requireAdmin()` pattern cho AdminReviewController
- `sources/backend/api-gateway/src/main/resources/application.yml` — verify `/api/admin/**` route (đã tồn tại từ admin products/orders/users)

### Backend tests
- `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/service/ReviewServiceTest.java` — extend test edit/delete/admin
- `sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ReviewControllerTest.java` — extend MockMvc cho 5 endpoint mới

### Frontend code (đụng tới Phase 21)
- `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewSection.tsx` — orchestrator: thêm sort state + currentUserId, fetch khi sort change
- `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewList.tsx` — thêm sort dropdown header + per-item edit/delete actions + inline edit form swap
- `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewForm.tsx` — accept `mode: 'create' | 'edit'` + `initialValues`
- `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewSection.module.css` — extend styles cho actions row + sort dropdown
- `sources/frontend/src/services/reviews.ts` — thêm `editReview()`, `deleteReview()`, list signature thêm `sort` param
- `sources/frontend/src/app/admin/` — sibling existing `products/`, `orders/`, `users/` cho pattern; tạo mới `admin/reviews/page.tsx`
- `sources/frontend/src/app/admin/products/page.tsx` — pattern table + filter + pagination để sao chép
- `sources/frontend/src/types/index.ts` — extend `Review` type với `hidden?: boolean`, `deletedAt?: string | null`

### Auth context
- `sources/frontend/src/providers/AuthProvider.tsx` (hoặc tương đương) — lấy `currentUserId` cho ReviewList
- JWT claim `sub` = userId (Phase 13 D-10 đã verify)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ReviewSection.tsx` orchestrator + `ReviewForm.tsx` — extendable cho edit mode
- `ReviewList.tsx` — đã có `ReadOnlyStars` component + relative date formatter, cần thêm action slot per item
- `JwtRoleGuard.requireAdmin()` — admin authorization pattern (đang dùng ở AdminProduct/Order/User controllers)
- `useToast()` hook — toast success/error
- `ApiResponse<T>` + `PaginatedResponse<T>` — wrap mọi response
- `httpGet`/`httpPost` từ `services/http.ts` — cần thêm `httpPatch`/`httpDelete` (verify đã có chưa)
- `ReviewService.recomputeProductRating(product)` — reuse cho mọi mutation path mới
- Admin layout `app/admin/layout.tsx` + sidebar — `/admin/reviews` link cần thêm vào sidebar nav

### Established Patterns
- Soft-delete `@SQLRestriction` đã dùng ở `ProductEntity` — KHÔNG dùng cho ReviewEntity vì admin cần xem deleted (filter ở service layer thay vì entity-level restriction)
- Error code string pattern (`REVIEW_NOT_ELIGIBLE`, `ADDRESS_LIMIT_EXCEEDED`) — đặt mới: `REVIEW_EDIT_WINDOW_EXPIRED`, `REVIEW_NOT_OWNER`, `REVIEW_NOT_FOUND`
- Server-side pagination `Pageable` + `?page=&size=` — chuẩn cho cả public list, admin list
- rhf + zod form validation (Phase 10/13) — apply cho edit form
- Native `<select>` dropdown cho filter (admin/products dùng pattern này)
- `JwtRoleGuard.extractUserIdFromBearer()` cho ownership check

### Integration Points
- Gateway route `/api/products/{id}/reviews/**` đã ánh xạ tới product-svc — PATCH/DELETE auto follow
- Gateway route `/api/admin/**` đã có (admin products/orders/users) — `/api/admin/reviews/**` tự động hoạt động khi tạo `AdminReviewController` mapping `/admin/reviews`
- Admin sidebar `app/admin/layout.tsx` — thêm link "Reviews" giữa Products và Users

### Anti-patterns to avoid
- KHÔNG dùng `@SQLRestriction` ở `ReviewEntity` (admin cần thấy deleted)
- KHÔNG hard-delete ở author endpoint (chỉ admin được hard-delete)
- KHÔNG để FE tự tin tưởng `editWindowHours` — BE phải re-check
- KHÔNG quên recompute avg_rating ở mọi mutation path (5 path mới: edit-rating-changed, author-delete, admin-hide, admin-unhide, admin-hard-delete)

</code_context>

<specifics>
## Specific Ideas

- Sort dropdown wording (chốt cho REV-05): "Mới nhất" / "Đánh giá cao nhất" / "Đánh giá thấp nhất"
- Edit window expired tooltip: "Đã quá thời hạn chỉnh sửa (24h)"
- Author delete confirm: "Xoá đánh giá này? Hành động không thể hoàn tác."
- Admin hard-delete confirm: "Xoá vĩnh viễn review này? Không thể hoàn tác."
- Admin status badge: 🟢 Hiện | 🔴 Ẩn | ⚫ Đã xoá
- Toast messages: "Đã cập nhật đánh giá" / "Đã xoá đánh giá" / "Đã ẩn review" / "Đã bỏ ẩn review" / "Đã xoá vĩnh viễn"
- Error envelope mapping (BE → FE):
  - `REVIEW_EDIT_WINDOW_EXPIRED` → "Đã quá thời hạn chỉnh sửa (24h kể từ lúc đăng)"
  - `REVIEW_NOT_OWNER` → "Bạn không có quyền chỉnh sửa review này"
  - `REVIEW_NOT_FOUND` → "Review không tồn tại hoặc đã bị xoá"

</specifics>

<deferred>
## Deferred Ideas

- **REV-05 helpful sort** — cần system votes (`POST /api/reviews/{id}/helpful`) + `helpful_count` column. Defer v1.4. Phase 21 sort dropdown chỉ có 3 lựa chọn; "helpful" chưa hiện.
- **Admin keyword search trên /admin/reviews** — lọc theo product name / reviewer / content. Defer khi danh sách review > 100 rows.
- **Bulk moderation actions** — checkbox + bulk hide / bulk delete. Defer.
- **Audit log admin actions** — bảng `review_moderation_log` (admin_id, review_id, action, before, after, ts). Defer cho compliance milestone.
- **Edit history / revisions** — giữ snapshots trước khi edit. Defer (REV-04 chỉ yêu cầu sửa/xoá, không yêu cầu revert).
- **Email notify reviewer khi bị hide** — defer.
- **Rate limit author edit** — defer (đã có 24h window là đủ chống abuse).
- **i18n cho admin moderation page** — phase này Vietnamese only.

### Reviewed Todos (not folded)

(Không có todo trong backlog match scope phase này.)

</deferred>

---

*Phase: 21-hoan-thien-reviews*
*Context gathered: 2026-05-02 (auto mode)*
