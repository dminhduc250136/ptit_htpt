---
phase: 04-frontend-contract-alignment-e2e-validation
reviewed: 2026-04-25T00:00:00Z
depth: standard
files_reviewed: 18
files_reviewed_list:
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java
  - sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java
  - sources/backend/product-service/pom.xml
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java
  - sources/backend/product-service/src/test/java/com/ptit/htpt/productservice/web/ProductControllerSlugTest.java
  - sources/frontend/e2e/observations.json
  - sources/frontend/e2e/uat.spec.ts
  - sources/frontend/src/app/cart/page.tsx
  - sources/frontend/src/app/checkout/page.tsx
  - sources/frontend/src/components/ui/ProductCard/ProductCard.tsx
  - sources/frontend/src/services/http.ts
  - sources/frontend/src/services/orders.ts
  - sources/frontend/src/types/api/orders.generated.ts
  - sources/frontend/src/types/api/products.generated.ts
  - sources/frontend/src/types/index.ts
findings:
  critical: 0
  warning: 5
  info: 7
  total: 12
status: issues_found
---

# Phase 4: Báo cáo Code Review (gộp 04-01..06)

**Reviewed:** 2026-04-25
**Depth:** standard
**Files Reviewed:** 18 (delta của các plan 04-04 / 04-05 / 04-06 + các file FE đã touch lại trong gap-closure)
**Status:** issues_found (0 critical / 5 warning / 7 info)

## Tóm tắt

Phase 4 đóng lại gap FE↔BE bằng ba commit chính:

- **04-04 (BE product-service)** — thêm `GET /products/slug/{slug}`, đổi `GET /products`, `GET /products/{id}` sang trả về `ProductResponse` (rich shape) và build pom đã có sẵn `springdoc-openapi-starter-webmvc-ui` cho codegen FE.
- **04-05 (BE order-service)** — thêm `CreateOrderCommand` + `createOrderFromCommand()` để map đúng vào hợp đồng FE checkout (`items[].unitPrice`, `shippingAddress`, `paymentMethod`, header `X-User-Id` thay cho userId trong body, `totalAmount`/`status` server-side).
- **04-06 (FE hardening + Playwright UAT)** — `services/http.ts` đã wrap `JSON.parse` (WR-02), `services/orders.ts` truyền `X-User-Id`, `app/checkout/page.tsx` thêm dispatcher CONFLICT (STOCK_SHORTAGE / PAYMENT_FAILED) + Modal, `ProductCard` thêm null-guards (`thumbnailUrl?.trim()`, `tags && tags.length > 0`, `rating ?? 0`, `reviewCount ?? 0`, `category?.name`), `e2e/uat.spec.ts` tự động hoá 12/12 row UAT (A1-A6 + B1-B5/B4a-B4b) với `observations.json` đính kèm.

**Bảo mật / hợp đồng đã được kiểm chứng:**

- `services/http.ts` (line 108) vẫn giữ guard open-redirect cho 401 returnTo (`pathname.startsWith('/') && !pathname.startsWith('//')`) — T-04-03 OK; UAT B4b PASS.
- 401 branch gọi `clearTokens()` trước khi redirect — T-04-04 OK; UAT B4a PASS.
- `OrderCrudService.createOrderFromCommand()` từ chối khi `userId` null/blank với `ResponseStatusException(BAD_REQUEST, "Missing X-User-Id session header")` — `OrderControllerCreateOrderCommandTest` cover đủ ba nhánh (200/201, 400 missing header, 400 validation empty items).
- `ProductControllerSlugTest` xác nhận envelope `ApiResponse` 200 (rich shape) và 404 với `code="NOT_FOUND"` qua GlobalExceptionHandler.
- KHÔNG có hardcoded secret, `eval(`, `Function(`, `innerHTML`, `dangerouslySetInnerHTML` trong các file đang review.

**Tồn đọng theo plan đã được chấp nhận:**

- Backend chưa persist `stock` → UAT A4 phải seed cart trực tiếp qua `localStorage` (Phase 5).
- Backend chưa emit `STOCK_SHORTAGE` / `PAYMENT_FAILED` → UAT B2/B3 dùng `page.route()` stub (Phase 5).
- `/auth/register` chưa ship → UAT A2 chạy mock setTimeout flow (Phase 5).

Các phát hiện dưới đây là rủi ro CHẤT LƯỢNG, KHÔNG chặn release v1.0.

## Cảnh báo (Warnings)

### WR-01: `clearTokens()` chỉ chạy khi `res.status === 401` nhưng `throw` luôn xảy ra — caller có thể double-handle UNAUTHORIZED

**File:** `sources/frontend/src/services/http.ts:102-126`
**Issue:**
Khi nhận 401, `request()` (1) xoá token, (2) trigger `window.location.href` redirect, rồi (3) tiếp tục `throw new ApiError('UNAUTHORIZED', ...)`. Trong `app/checkout/page.tsx` line 129-131 có comment "http.ts already redirected; no-op here." — đúng là no-op nhưng ApiError vẫn lan ra `submitOrder()` và đi qua `setLoading(false)` ở `finally`. Nếu trong tương lai có thêm dispatcher case bắt `default` ở phía trên `UNAUTHORIZED` (ví dụ ai đó refactor switch), error sẽ bị toast "Đã có lỗi" trước khi redirect kịp thực hiện. Hành vi hiện tại đúng nhưng phụ thuộc vào THỨ TỰ case trong switch — fragile.

**Fix:** Sau khi gọi `clearTokens()` + set `window.location.href`, nên `throw` một sentinel `ApiError` đã được đánh dấu rõ ràng (hoặc trả về `Promise<never>` với `new Promise(() => {})` để dừng caller hẳn vì trang sắp navigate).

```ts
if (res.status === 401) {
  clearTokens();
  if (typeof window !== 'undefined') {
    const pathname = window.location.pathname;
    const target = pathname.startsWith('/') && !pathname.startsWith('//')
      ? `/login?returnTo=${encodeURIComponent(pathname)}`
      : '/login';
    window.location.href = target;
    // Stop the caller — page is navigating away, no point in propagating.
    return new Promise<T>(() => {}); // never resolves
  }
}
```

Nếu giữ pattern `throw` hiện tại thì BẮT BUỘC document rằng case `UNAUTHORIZED` phải đứng riêng và là no-op trong mọi dispatcher (đã có ở checkout, cần đảm bảo profile/products page cũng vậy).

---

### WR-02: `createOrderFromCommand` không validate `command.items()` không null trước khi `.stream()`

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:91-104`
**Issue:**
`@NotEmpty List<@Valid OrderItemRequest> items` ở `CreateOrderCommand` đảm bảo Jackson + Bean Validation chặn payload thiếu `items` ở controller (`@Valid @RequestBody`). Tuy nhiên service `createOrderFromCommand` là `public` — nếu sau này có internal caller (queue consumer, scheduled job, test khác) gọi trực tiếp với `command.items() == null` thì `command.items().stream()` sẽ throw `NullPointerException` trồi ra `handleFallback` → 500 INTERNAL_ERROR, mất chuỗi chẩn đoán so với 400 VALIDATION_ERROR.

**Fix:** Defensive guard ngay đầu method, đồng nhất với guard `userId`:

```java
public OrderEntity createOrderFromCommand(String userId, CreateOrderCommand command) {
  if (userId == null || userId.isBlank()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-User-Id session header");
  }
  if (command == null || command.items() == null || command.items().isEmpty()) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items must not be empty");
  }
  // ...
}
```

---

### WR-03: `ProductCrudService.toResponse()` bị gọi 2 lần trong `listProducts` cast `List<ProductEntity>` không an toàn

**File:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java:34-40`
**Issue:**

```java
Map<String, Object> page0 = paginate(all, page, size);
@SuppressWarnings("unchecked")
List<ProductEntity> content = (List<ProductEntity>) page0.get("content");
page0.put("content", content.stream().map(this::toResponse).toList());
```

Cast unchecked này hoạt động vì hôm nay `paginate()` luôn trả `List<T>`. Nhưng việc đi qua `Map<String, Object>` rồi cast lại là dấu hiệu thiết kế chưa đúng — `paginate()` đáng lẽ nhận một `Function<T, R>` mapper hoặc trả `PaginatedResponse<T>` typed. Nếu một dev sau refactor `paginate()` cho phép boxing/unboxing khác, compile vẫn pass nhưng runtime sẽ `ClassCastException` ở dòng `(List<ProductEntity>) page0.get("content")`.

**Fix:** Generics hoá `paginate()` để nhận mapper, hoặc tạo riêng overload `paginateAndMap(List<E>, page, size, Function<E,R>)`:

```java
private <T, R> Map<String, Object> paginate(List<T> source, int page, int size, Function<T, R> mapper) {
  // ... same body, apply mapper.apply(...) before putting "content"
}
// hoặc đơn giản: map TRƯỚC khi paginate
List<ProductResponse> mapped = all.stream().map(this::toResponse).toList();
return paginate(mapped, page, size);
```

Bonus: phương án thứ 2 tránh được `@SuppressWarnings("unchecked")` hoàn toàn.

---

### WR-04: `app/checkout/page.tsx` đọc `order.orderCode` nhưng `Order` type có thể không khớp với envelope thực tế từ backend

**File:** `sources/frontend/src/app/checkout/page.tsx:95-100`
**Issue:**

```ts
const order = await createOrder({...}, user?.id);
clearCart();
setShowSuccess({
  orderCode:
    order?.orderCode ??
    (order as { code?: string } | undefined)?.code ??
    '—',
});
```

`createOrder` trả `Promise<Order>` (theo `services/orders.ts`), và `Order` từ `@/types/index.ts:155-171` định nghĩa `orderCode: string`. Nhưng backend `OrderEntity.create(...)` (xem 04-05) hôm nay KHÔNG tự sinh `orderCode` — `OrderEntity` chỉ có `id`. Cast `as { code?: string }` là dấu hiệu type Order đang nói dối: response thực tế là `OrderEntity { id, userId, totalAmount, status, note, ... }`. Khi UAT A5 chạy thật trên backend, `order.orderCode` luôn `undefined` → modal hiển thị "Mã đơn hàng: —".

**Fix:** Hoặc backend tự generate `orderCode` ở `OrderEntity.create()` (Phase 5 — cần persist column riêng), hoặc FE fallback dùng `order.id`:

```ts
setShowSuccess({
  orderCode:
    order?.orderCode ??
    (order as { code?: string; id?: string } | undefined)?.code ??
    (order as { id?: string } | undefined)?.id ??
    '—',
});
```

Đồng thời cập nhật `Order` interface ở `types/index.ts` để `orderCode` thành `orderCode?: string` (optional), tránh gây hiểu lầm cho dev sau.

---

### WR-05: Spec UAT phụ thuộc nặng vào `waitForTimeout` và state seeded trước khi reload — flaky risk

**File:** `sources/frontend/e2e/uat.spec.ts:240-254, 333-348, 405-417, 478-490, 627-639`
**Issue:**
Trong A5, B1, B2, B3, B5 đều có pattern:

```ts
await page.goto('/checkout');
await page.waitForLoadState('domcontentloaded');
await page.reload();
await page.waitForLoadState('domcontentloaded');
await page.waitForFunction(() => { ... });
await page.evaluate(() => window.dispatchEvent(new CustomEvent('cart:change')));
await page.waitForTimeout(500);
```

Cộng thêm `await page.waitForTimeout(2000)` / `3000` sau khi click submit. Tổng cộng mỗi test "wait" ~5-7s không cần thiết — sẽ flake trên CI chậm và gây timeout giả nếu network thật mất hơn 3s. Comment trong code đã giải thích lý do (Turbopack lazy initializer race) nhưng vẫn nên thay bằng `expect.poll(...)` hoặc `waitForResponse(...)` thay cho `waitForTimeout` cứng.

**Fix:** Thay `waitForTimeout(2000)` (đợi response sau submit) bằng `waitForResponse`:

```ts
const [resp] = await Promise.all([
  page.waitForResponse(
    (r) => r.url().includes('/api/orders/orders') && r.request().method() === 'POST',
    { timeout: 10000 },
  ),
  submit.click(),
]);
```

Tương tự, thay `waitForTimeout(500)` sau dispatch `cart:change` bằng `expect.poll(() => readCartLengthFromDom()).toBeGreaterThan(0)`. Giảm tổng thời gian spec, ổn định hơn trên CI.

## Info

### IN-01: Hardcoded `localhost:8080` fallback trong `services/http.ts`

**File:** `sources/frontend/src/services/http.ts:16`
**Issue:**

```ts
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
```

Fallback hardcoded cho dev là OK, nhưng khi build production mà quên set `NEXT_PUBLIC_API_BASE_URL`, FE sẽ lặng lẽ trỏ về `localhost:8080` của user → confusing failure. Nên `throw` ở module load nếu `NODE_ENV === 'production'` mà env thiếu.

**Fix:**

```ts
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL
  ?? (process.env.NODE_ENV === 'production'
    ? (() => { throw new Error('NEXT_PUBLIC_API_BASE_URL must be set in production'); })()
    : 'http://localhost:8080');
```

---

### IN-02: `OrderControllerCreateOrderCommandTest` dùng substring assert thay vì JSONPath/Jackson

**File:** `sources/backend/order-service/src/test/java/com/ptit/htpt/orderservice/web/OrderControllerCreateOrderCommandTest.java:60-67`
**Issue:**
Comment giải thích lý do dùng `body.contains("198000")` (relaxed substring để né `198000.0` / `"198000"`) là hợp lý, nhưng substring `"\"userId\":\"user-uat-1\""` sẽ false-positive nếu sau này response thêm field `userId` ở vị trí lồng (ví dụ `nestedAudit.userId`). Test sẽ vẫn pass dù primary `userId` ở root sai.

**Fix:** Dùng `JsonPath` / `ObjectMapper.readTree`:

```java
JsonNode root = new ObjectMapper().readTree(body);
JsonNode data = root.path("data");
assertThat(data.path("userId").asText()).isEqualTo("user-uat-1");
assertThat(data.path("status").asText()).isEqualTo("PENDING");
assertThat(new BigDecimal(data.path("totalAmount").asText()))
    .isEqualByComparingTo(new BigDecimal("198000"));
```

---

### IN-03: `ProductCrudService.categorySlugFor()` regex chạy 2 lần — minor duplication

**File:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java:167-171`
**Issue:**

```java
return c.name() == null ? c.id() : c.name().toLowerCase(Locale.ROOT)
    .replaceAll("[^a-z0-9]+", "-")
    .replaceAll("^-+|-+$", "");
```

Đây là util slug, không phải hot-path nhưng `replaceAll` compile regex mỗi lần gọi. Khi list 100 products mỗi product gọi 2 regex compile → 200 compile cho 1 request. Nhỏ nhưng dễ cải thiện.

**Fix:** Cache compiled `Pattern` ở class-level constants:

```java
private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
private static final Pattern EDGE_DASH = Pattern.compile("^-+|-+$");
// ...
return EDGE_DASH.matcher(NON_ALPHANUM.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-")).replaceAll("");
```

Phase 5 sẽ có cột `slug` thật trên `CategoryEntity` thì xoá luôn util này.

---

### IN-04: ProductCard rating array `[...Array(5)].map(...)` tốn memory không cần thiết

**File:** `sources/frontend/src/components/ui/ProductCard/ProductCard.tsx:91-103`
**Issue:**

```tsx
{[...Array(5)].map((_, i) => (
  <svg key={i} ... />
))}
```

Tạo array 5 phần tử rồi destructure mỗi render. Không nghiêm trọng, nhưng dễ thay bằng const:

**Fix:**

```tsx
const STAR_INDICES = [0, 1, 2, 3, 4] as const;
// ...
{STAR_INDICES.map((i) => (
  <svg key={i} ... />
))}
```

---

### IN-05: `ProductCard.hasDiscount` truthy check dễ false-positive với `discount === 0`

**File:** `sources/frontend/src/components/ui/ProductCard/ProductCard.tsx:25`
**Issue:**

```tsx
const hasDiscount = product.originalPrice && product.discount;
```

Nếu backend trả `discount: 0` (sale ảo) thì `hasDiscount` = 0 (falsy) → không render. Hôm nay backend đặt `null` cho discount nên không vấn đề, nhưng tên biến gợi ý boolean trong khi giá trị là `number | null`.

**Fix:**

```tsx
const hasDiscount = (product.originalPrice ?? 0) > 0 && (product.discount ?? 0) > 0;
```

---

### IN-06: `CartItem` (types/index.ts) vs `services/cart.ts` shape khác nhau — đã ghi chú nhưng dễ nhầm

**File:** `sources/frontend/src/types/index.ts:140-145` và `sources/frontend/src/services/cart.ts:22-28`
**Issue:**
Comment ở `cart.ts` đã giải thích: `types/index.ts` định nghĩa `CartItem { id, product: Product, quantity, ... }` (UI-layer), còn `cart.ts` định nghĩa flat `CartItem { productId, name, thumbnailUrl, price, quantity }` (storage-layer). Hôm nay không xung đột vì hai type đều export `CartItem`, nhưng IDE auto-import sẽ ngẫu nhiên chọn 1 trong 2 → có ngày sẽ ship bug. Phase 5 đã có TODO clean up.

**Fix:** Đổi tên flat shape thành `StoredCartItem` ở `services/cart.ts` ngay bây giờ — refactor 5 phút, tránh trap auto-import:

```ts
// services/cart.ts
export interface StoredCartItem {
  productId: string;
  // ...
}
```

Sau đó update `app/cart/page.tsx` và `app/checkout/page.tsx` import `StoredCartItem`.

---

### IN-07: `observations.json` có thể commit screenshots binary — kiểm tra `.gitignore` cho `e2e/screenshots/`

**File:** `sources/frontend/e2e/observations.json` (toàn bộ) + path tham chiếu `screenshots/A1.png`...
**Issue:**
`uat.spec.ts:33-37` ghi screenshot vào `e2e/screenshots/{id}.png` và `observations.json` reference các path này. Nếu thư mục `screenshots/` chưa được `.gitignore` thì mỗi lần re-run CI sẽ bloat repo bằng PNG binary. Đối với phase này (verification artifact), screenshots có thể đáng commit lần đầu nhưng không nên track lâu dài.

**Fix:** Thêm vào `sources/frontend/.gitignore`:

```
# Playwright UAT artifacts (commit observations.json only)
e2e/screenshots/
e2e/test-results/
e2e/playwright-report/
```

Nếu muốn giữ screenshot làm bằng chứng phase, copy chúng sang `.planning/phases/04-.../artifacts/screenshots/` rồi reset thư mục FE.

---

_Reviewed: 2026-04-25_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
_Auto Mode: active_
