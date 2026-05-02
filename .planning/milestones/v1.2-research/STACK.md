# Technology Stack — v1.2 UI/UX Completion (Additions Only)

**Project:** tmdt-use-gsd — milestone v1.2 (subsequent)
**Researched:** 2026-04-26
**Confidence Level:** HIGH on backend additions (Spring/JPA standard patterns) · MEDIUM-HIGH on FE library picks (Context7-verified versions)
**Scope:** ONLY new capabilities required by 11 v1.2 features. Existing v1.0/v1.1 stack (Spring Boot 3.3.2 microservices, Next.js 16.2.3 + React 19.2.4, Postgres 16 + JPA + Flyway, OpenAPI codegen, Playwright 1.59) is locked and NOT re-researched.

---

## TL;DR — What Gets Added

| Layer | Add | Reject | Rationale (one-liner) |
|-------|-----|--------|----------------------|
| Backend persistence | 4 Flyway V4 migrations + 4 JPA entities (`wishlist_items`, `product_reviews`, `user_addresses`, optional `user_avatars` blob col) | QueryDSL, jOOQ | Search filters bằng Spring Data JPA Specifications đủ — không bloat |
| Backend search | `JpaSpecificationExecutor<ProductEntity>` + `Specification` builders | QueryDSL APT, Hibernate Search/Lucene | Brand/price/rating/in-stock là 4 predicate AND đơn giản |
| Backend file upload | `MultipartFile` → resize bằng `Thumbnailator` → lưu bytes vào `users.avatar_blob` (BYTEA) + `avatar_content_type` | S3, presigned URLs, MinIO | Local Docker Compose, không có object store; BYTEA <100KB OK cho avatar |
| FE form validation | `react-hook-form` 7.55.x + `zod` 3.24.x + `@hookform/resolvers` 5.x | Formik, native HTML only | Profile/address/review forms đa field; zod schema reuse cho client+server contract |
| FE image gallery | `yet-another-react-lightbox` 3.21.x (thumbnails plugin) | swiper, react-image-gallery, photoswipe | Zero-dep, 11KB gz, có thumbnails plugin built-in, fit Next 16 RSC |
| FE star rating | Custom 30-LOC component (SVG `<polygon>` + CSS) | react-rating, react-star-ratings | Trivial UI; không kéo lib cho 1 component |
| FE date range | `<input type="date">` native | react-datepicker, dayjs picker | Order history filter chỉ cần from/to — native đủ + a11y free |
| Avatar upload UX | Native `<input type=file accept="image/*">` + client preview qua `URL.createObjectURL` | react-dropzone, uppy | 1 file, không drag-drop multi |
| Homepage carousel | CSS scroll-snap + grid (no JS lib) | swiper, embla-carousel | Hero static; featured/new arrivals = horizontal scroll snap |
| Backend test | Giữ nguyên Testcontainers Postgres | — | Spec specification tests + repo tests đã có pattern |

**Net-new dependency footprint:** 1 backend lib (Thumbnailator, ~70KB), 3 FE libs (rhf+zod+resolvers ~30KB gz, lightbox ~11KB gz). No new infra, no new services.

---

## 1. Backend Additions (Spring Boot 3.3.2, Java 17)

### 1.1 New Persistence — JPA Entities + Flyway V4 Migrations

Đặt migrations ở `src/main/resources/db/migration/V4__<feature>.sql` (V1=baseline, V2=dev seed, V3=stock đã dùng ở v1.1). V4 là cluster cho v1.2.

#### Entity #1 — `WishlistItemEntity` (user-service)
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT FK → users(id) | NOT NULL, indexed |
| `product_id` | BIGINT | NOT NULL — cross-service ID, không FK (microservice boundary) |
| `created_at` | TIMESTAMPTZ | DEFAULT now() |
| Unique | `(user_id, product_id)` | Idempotent toggle |

**Why user-service không phải product-service:** wishlist là user-owned data. Product-service chỉ resolve productIds → details qua existing `GET /api/products?ids=...` (đã có ở v1.1 search).

#### Entity #2 — `ProductReviewEntity` (product-service)
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGSERIAL PK | |
| `product_id` | BIGINT FK → products(id) | NOT NULL, indexed |
| `user_id` | BIGINT | NOT NULL — cross-service, không FK |
| `user_display_name` | VARCHAR(120) | Snapshot — tránh N+1 cross-service khi list reviews |
| `rating` | SMALLINT | CHECK (rating BETWEEN 1 AND 5) |
| `comment` | TEXT | NULL allowed (rating-only review) |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |
| Unique | `(product_id, user_id)` | One review per user per product (edit thay vì duplicate) |

**Average rating:** computed view hoặc cached column trên `products` (`avg_rating NUMERIC(2,1)`, `review_count INT`) updated qua `@EventListener` post-insert/update/delete. Cached column rẻ + FE list product không phải JOIN aggregate.

**Decision:** dùng cached column. Trigger-free (Java service tầng update). FE list trang `/products` đã sẵn `Product` DTO — chỉ thêm 2 field.

#### Entity #3 — `UserAddressEntity` (user-service)
| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGSERIAL PK | |
| `user_id` | BIGINT FK → users(id) | NOT NULL, indexed |
| `label` | VARCHAR(60) | "Nhà", "Công ty", optional |
| `recipient_name` | VARCHAR(120) | NOT NULL |
| `phone` | VARCHAR(20) | NOT NULL |
| `street` | VARCHAR(255) | NOT NULL |
| `ward` | VARCHAR(120) | NULL |
| `district` | VARCHAR(120) | NULL |
| `city` | VARCHAR(120) | NOT NULL |
| `is_default` | BOOLEAN | DEFAULT false; partial unique idx `WHERE is_default` per user |
| `created_at` / `updated_at` | TIMESTAMPTZ | |

**Integration với checkout v1.1:** `OrderEntity.shipping_address` đã là JSON column ở v1.1 (PERSIST-02). Address book chỉ cần serialize `UserAddressEntity` → JSON khi user chọn → POST `/api/orders` không đổi shape. Zero migration impact lên order-service.

#### Entity #4 — Avatar storage (user-service, ALTER existing `users` table)
```sql
ALTER TABLE users
  ADD COLUMN avatar_blob BYTEA,
  ADD COLUMN avatar_content_type VARCHAR(40),
  ADD COLUMN avatar_updated_at TIMESTAMPTZ;
```

**Why BYTEA inline thay vì separate table / object store:**
- Local Docker Compose stack — không có S3/MinIO
- Avatar resize → 200×200 JPEG ≤ 30KB → BYTEA fine (<1MB threshold gây toast cho Postgres)
- One row per user, đọc qua dedicated endpoint `GET /api/users/{id}/avatar` (Cache-Control: max-age=300, ETag = avatar_updated_at)
- Tránh "1 service mới cho file" anti-pattern cho dự án thử nghiệm GSD

**Reject:** Base64 trong cột TEXT (33% bloat, browser không cache binary), separate `user_avatars` table (over-normalize — 1:1 với users), filesystem volume (không persist qua container rebuild).

### 1.2 Search Filters — Spring Data JPA Specifications

**Need:** brand IN (...), priceMin/priceMax, ratingMin, inStock — all AND-combined, all optional.

**Approach:**
```java
public interface ProductRepository
    extends JpaRepository<ProductEntity, Long>,
            JpaSpecificationExecutor<ProductEntity> {}
```

`ProductSpecifications.java` static factory:
- `hasBrand(List<String> brands)` → `root.get("brand").in(brands)`
- `priceBetween(BigDecimal min, BigDecimal max)` → `cb.between(...)`
- `ratingAtLeast(BigDecimal min)` → `cb.ge(root.get("avgRating"), min)`
- `inStock()` → `cb.gt(root.get("stock"), 0)`

Controller combine bằng `Specification.where(s1).and(s2)...`, skip nulls.

**Verified pattern:** Spring Data JPA reference docs — Specifications API (Context7: `/spring-projects/spring-data-jpa`, topic "specifications"). Stable API, không cần annotation processor như QueryDSL.

**Reject QueryDSL:**
- Cần Maven APT plugin (`apt-maven-plugin`) — extra build complexity
- Generated Q-classes phải gitignore + regen — fragile
- 4 predicates không xứng đáng infrastructure cost
- Specifications tự nhiên typesafe đủ qua `JpaSort` / Pageable

**Reject Hibernate Search / Lucene / Elasticsearch:**
- Full-text search v1.1 đã giải bằng `LIKE '%kw%'` (UI-04) — đủ cho dataset thử nghiệm
- v1.2 chỉ thêm structured filters → SQL WHERE đủ

### 1.3 File Upload — Multipart + Thumbnailator

**Endpoint:** `PUT /api/users/{id}/avatar` (multipart/form-data, field name `file`).

**Library:** [`net.coobird:thumbnailator:0.4.20`](https://github.com/coobird/thumbnailator) — pure Java, no native deps, MIT.

```xml
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.20</version>
</dependency>
```

```java
byte[] resized = Thumbnails.of(file.getInputStream())
    .size(256, 256)
    .outputFormat("jpg")
    .outputQuality(0.85)
    .asBufferedImage(); // wrap to ByteArrayOutputStream
```

**Validation:** Spring Boot `multipart.max-file-size=2MB`, content-type whitelist (`image/jpeg`, `image/png`, `image/webp`), magic-byte check (`Files.probeContentType` không tin browser).

**Why not presigned S3:** không có S3 trong stack. Adding MinIO chỉ cho avatar = scope creep.

**Why not base64 trong JSON body:** mất ETag/streaming, payload bloat, FE phải encode/decode.

---

## 2. Frontend Additions (Next.js 16.2.3, React 19.2.4, TypeScript 5)

**Existing FE state (verified từ `sources/frontend/package.json`):** dependencies = `next`, `react`, `react-dom` ONLY. Zero UI lib, zero form lib, zero validation lib. Mọi thứ hiện tại là plain CSS modules + custom components (`Button`, `Badge`, `ProductCard`, `RetrySection`, `Toast`).

Đây là cố ý minimalism — additions phải justify từng KB.

### 2.1 Form Validation — react-hook-form + zod

**Need:** Profile editing (5 fields + password), address create/edit (8 fields), review submit (rating + comment) — all need client validation, error states, dirty tracking.

**Picks:**
| Lib | Version | Size (gz) | Why |
|-----|---------|-----------|-----|
| `react-hook-form` | 7.55.0 | ~9KB | Uncontrolled inputs = perf default, hook API minimal, Next 16 / React 19 compat |
| `zod` | 3.24.1 | ~13KB | Schema once, infer TS type + validate runtime; reuse schemas đã sẵn nếu later mirror BE DTO |
| `@hookform/resolvers` | 5.0.1 | <1KB | Glue zod ↔ rhf |

Total: ~23KB gz cho 3 forms. Acceptable.

**Reject Formik:** abandoned-ish (last release patchy), 13KB gz, controlled-input perf cliff với arrays.

**Reject native HTML5 validation only:** `pattern` regex error messages không i18n, không tích hợp với existing `Toast` system, password confirmation cross-field check phải custom anyway.

**Pattern:**
```ts
// schemas/profile.ts
export const profileSchema = z.object({
  fullName: z.string().min(2).max(120),
  phone: z.string().regex(/^(\+84|0)\d{9,10}$/),
});
export type ProfileForm = z.infer<typeof profileSchema>;

// component
const { register, handleSubmit, formState: { errors } } =
  useForm<ProfileForm>({ resolver: zodResolver(profileSchema) });
```

### 2.2 Image Gallery — yet-another-react-lightbox

**Need:** Product detail có gallery (zoom, fullscreen, thumbnails strip, keyboard nav). Hiện `app/products/[slug]/page.tsx` đã có `selectedImage` state nhưng chỉ render 1 ảnh — phải nâng lên thumbnail strip + lightbox.

**Pick:** [`yet-another-react-lightbox`](https://yet-another-react-lightbox.com) v3.21.x
- Zero dependencies (peer: react)
- ~11KB gz core, tree-shakeable plugins
- Plugins: `Thumbnails`, `Zoom`, `Fullscreen`, `Counter` — pick chỉ cái cần
- React 19 compat (Context7-verified)
- TypeScript native

```tsx
import Lightbox from 'yet-another-react-lightbox';
import Thumbnails from 'yet-another-react-lightbox/plugins/thumbnails';
import 'yet-another-react-lightbox/styles.css';
import 'yet-another-react-lightbox/plugins/thumbnails.css';
```

**Reject Swiper:** 40KB+ gz, overkill cho gallery (designed cho carousel slideshows).
**Reject react-image-gallery:** stale (last commit >18 months), no React 19 support badge.
**Reject PhotoSwipe vanilla:** không có React wrapper officially maintained, manual lifecycle.

**Image strategy:** dùng existing `next/image` (đã import ở product detail page) cho main + thumbnails. Lightbox chỉ active on click.

### 2.3 Star Rating — Custom Component (no lib)

**Reject `react-rating`, `react-star-ratings`:** 1 component, 5 SVG `<polygon>` với fill state, 30 LOC, không xứng dependency.

```tsx
// components/ui/StarRating/StarRating.tsx
function StarRating({ value, onChange, readOnly }: Props) {
  return [1,2,3,4,5].map(n => (
    <button type="button" onClick={() => !readOnly && onChange?.(n)}
            aria-label={`${n} sao`}>
      <svg><polygon className={n <= value ? 'filled' : 'empty'} ... /></svg>
    </button>
  ));
}
```

A11y: `role="radiogroup"` wrapper khi editable, `aria-label` per star.

### 2.4 Date Range — Native `<input type="date">`

Order history filter chỉ cần from/to. Native input:
- Zero JS
- Picker UI free từ browser (Chromium/Firefox 2026 đều có calendar UI tốt)
- Format ISO `YYYY-MM-DD` parse trivial backend-side
- A11y free

**Reject react-datepicker / dayjs picker:** không cần range overlay, không cần locale customization beyond browser default.

### 2.5 Avatar Upload UX

```tsx
const [preview, setPreview] = useState<string | null>(null);
<input type="file" accept="image/jpeg,image/png,image/webp"
       onChange={e => {
         const f = e.target.files?.[0];
         if (!f) return;
         if (f.size > 2_000_000) return showToast({ kind:'error', text:'File >2MB' });
         setPreview(URL.createObjectURL(f));
         setFile(f);
       }} />
{preview && <img src={preview} alt="preview" />}
```

Submit qua `FormData` + existing typed http client (cần thêm 1 method `postMultipart` ở `services/api.ts` — không bắt OpenAPI codegen vì multipart codegen flaky).

**Reject react-dropzone:** drag-drop UX không cần thiết cho 1 avatar — extra 7KB gz cho zero gain.

### 2.6 Homepage Carousel — CSS Scroll-Snap

```css
.featuredRow {
  display: grid; grid-auto-flow: column; grid-auto-columns: 280px;
  gap: 16px; overflow-x: auto;
  scroll-snap-type: x mandatory;
}
.featuredRow > * { scroll-snap-align: start; }
```

Touch + trackpad swipe miễn phí. Arrow buttons optional bằng `scrollBy({left: 296})`.

**Reject swiper / embla-carousel:** hero là 1 ảnh static + featured là horizontal list — không cần JS carousel engine.

---

## 3. Integration Points với Existing Stack

| Existing piece | v1.2 hook |
|----------------|-----------|
| **OpenAPI codegen pipeline** (`scripts/gen-api.mjs`) | Phải regen sau khi thêm endpoints `/wishlist`, `/reviews`, `/addresses`, `/users/{id}/avatar`, `/products?brand=&minPrice=&...`. Avatar multipart endpoint có thể cần manual type stub vì codegen multipart flaky. |
| **Typed `services/*.ts` modules** (cart, orders, products, users) | Add `services/wishlist.ts`, `services/reviews.ts`, `services/addresses.ts`. Pattern khớp existing — 1 file per resource. |
| **`ApiError` dispatcher** (5 failure branches) | Reuse — không thêm error class. Validation 422 đã handled. |
| **AuthProvider hydration** | Wishlist/addresses page là protected → middleware extension (AUTH-06) cover. Component chỉ cần read `useAuth()` user.id. |
| **Middleware route gate** (AUTH-06 closure) | Matcher mở rộng: `['/admin/:path*', '/account/:path*', '/profile/:path*', '/checkout/:path*']`. JWT cookie check identical. |
| **`OrderEntity.shipping_address` JSON** (v1.1 PERSIST-02) | Address book → serialize chosen `UserAddressEntity` thành JSON trước khi POST `/api/orders`. Order-service ZERO change. |
| **`ProductEntity.stock`** (v1.1 PERSIST-01) | `inStock` filter dùng stock>0. Stock badge ở product detail dùng same field. |
| **Cached `avg_rating` + `review_count` trên products** | New cached cols → product list/search responses tự nhiên có rating → "rating filter" + "rating display ở card" share data. |
| **Playwright E2E** (`@playwright/test` 1.59) | Re-baseline cover v1.1 + thêm 8 specs cho v1.2 features. Selectors: `data-testid` thêm trên new components. |
| **Toast system** (`components/ui/Toast`) | Reuse cho wishlist add/remove, profile saved, address default switch, review submitted. Zero new notification lib. |

---

## 4. Versions Summary (copy-paste install)

### Backend — `user-service/pom.xml`
```xml
<dependency>
  <groupId>net.coobird</groupId>
  <artifactId>thumbnailator</artifactId>
  <version>0.4.20</version>
</dependency>
```

### Backend — `product-service/pom.xml`
No new deps. JPA Specifications là core Spring Data JPA (đã có).

### Backend — `order-service/pom.xml`
No new deps.

### Frontend — `sources/frontend/package.json`
```json
{
  "dependencies": {
    "react-hook-form": "7.55.0",
    "zod": "3.24.1",
    "@hookform/resolvers": "5.0.1",
    "yet-another-react-lightbox": "3.21.5"
  }
}
```

Install:
```bash
cd sources/frontend
npm install react-hook-form@7.55.0 zod@3.24.1 @hookform/resolvers@5.0.1 yet-another-react-lightbox@3.21.5
```

---

## 5. Explicitly NOT Adding (Anti-Bloat)

| Tempting addition | Why reject |
|-------------------|-----------|
| **Redux / Zustand** | AuthProvider context + per-page useState đủ. Wishlist count = 1 fetch on mount + invalidate. |
| **TanStack Query / SWR** | Existing services đã có manual loading/error pattern; introducing query lib = rewrite tất cả services. Defer. |
| **Tailwind** | Existing là CSS modules — switching = full restyling outside scope. |
| **shadcn/ui, MUI, Chakra** | Component lib hiện tại nhỏ, prescriptive — adding design system = rewrite. v1.2 chỉ cần ~6 new components, viết tay. |
| **QueryDSL / jOOQ** | Specifications cover use case; APT/codegen overhead không xứng. |
| **MinIO / S3 / presigned** | Avatar = inline BYTEA đủ cho local Docker stack thử nghiệm. |
| **Redis cache** | avg_rating cached column đã giải N+1; product list <100 rows local, không cần cache layer. |
| **WebSocket / SSE** | Reviews refresh on submit = optimistic update + refetch list, không cần realtime. |
| **react-dropzone, uppy** | Avatar = 1 file native input. |
| **Algolia / Meilisearch / Elasticsearch** | Search dataset nhỏ, JPA Specification + LIKE đủ. |
| **dayjs / date-fns** | Native `<input type="date">` + `Intl.DateTimeFormat` cho display. Order history đã có ISO format. |

---

## 6. Confidence & Sources

| Claim | Confidence | Source |
|-------|-----------|--------|
| Spring Data JPA Specifications là API stable trong Spring Boot 3.3.x | HIGH | Spring Data JPA reference docs (current) |
| Thumbnailator 0.4.20 stable, MIT, pure Java | HIGH | github.com/coobird/thumbnailator releases |
| react-hook-form 7.55.x React 19 compat | HIGH | rhf docs (Context7) + npm peerDeps |
| zod 3.24.x stable, no breaking from 3.23 | HIGH | zod CHANGELOG |
| yet-another-react-lightbox 3.21.x React 19 compat, ~11KB gz | MEDIUM-HIGH | official docs site + bundlephobia (April 2026) |
| BYTEA <100KB OK Postgres performance | HIGH | Postgres docs — TOAST handles inline ≤2KB, external ≤8KB chunks transparently |
| Cached avg_rating column update via service-tier event listener | HIGH | Spring `@TransactionalEventListener` pattern, standard |
| Native `<input type="date">` browser support 2026 | HIGH | caniuse — universal Chromium/FF/Safari 16+ |

**Verified nothing:** Avatar BYTEA performance under high concurrency (irrelevant — local stack). Lightbox bundle size đo qua bundlephobia historical, không re-verified hôm nay.

---

## 7. Roadmap Implications

**Phase 9 (đề xuất):** AUTH-06 middleware + UI-02 admin dashboard + Playwright re-baseline (residual closure, zero new deps).

**Phase 10:** Backend persistence cluster — V4 migrations (wishlist, reviews + cached cols, addresses, avatar BYTEA cols) + entities + repositories. Tất cả backend libs install ở phase này (chỉ Thumbnailator).

**Phase 11:** FE foundation deps + cross-cutting UI:
- Install rhf+zod+resolvers+lightbox cùng lúc
- Build StarRating component
- Profile editing form (first form = thiết lập rhf+zod pattern cho phases sau)

**Phase 12:** Discovery features (Reviews UI, Advanced search filters) — reuse rhf+StarRating.

**Phase 13:** Account features (Wishlist, Order history filtering, Address book) — reuse rhf+forms.

**Phase 14:** Public polish (Homepage redesign, Product detail enhancements với lightbox).

**Phase 15:** Playwright E2E expansion + audit.

Phase 11 = critical foundation — bug ở rhf/zod setup sẽ block 12/13. Suggest extra audit-verify gate sau Phase 11.
