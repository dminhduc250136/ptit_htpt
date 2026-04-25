---
phase: 04-frontend-contract-alignment-e2e-validation
reviewed: 2026-04-25T00:00:00Z
depth: standard
files_reviewed: 31
files_reviewed_list:
  - sources/frontend/middleware.ts
  - sources/frontend/next.config.ts
  - sources/frontend/scripts/gen-api.mjs
  - sources/frontend/playwright.config.ts
  - sources/frontend/e2e/uat.spec.ts
  - sources/frontend/src/app/cart/page.tsx
  - sources/frontend/src/app/checkout/page.tsx
  - sources/frontend/src/app/layout.tsx
  - sources/frontend/src/app/login/page.tsx
  - sources/frontend/src/app/not-found.tsx
  - sources/frontend/src/app/page.tsx
  - sources/frontend/src/app/products/[slug]/page.tsx
  - sources/frontend/src/app/products/page.tsx
  - sources/frontend/src/app/profile/page.tsx
  - sources/frontend/src/app/register/page.tsx
  - sources/frontend/src/components/ui/Banner/Banner.tsx
  - sources/frontend/src/components/ui/Modal/Modal.tsx
  - sources/frontend/src/components/ui/RetrySection/RetrySection.tsx
  - sources/frontend/src/components/ui/Toast/Toast.tsx
  - sources/frontend/src/components/ui/index.ts
  - sources/frontend/src/providers/AuthProvider.tsx
  - sources/frontend/src/services/auth.ts
  - sources/frontend/src/services/cart.ts
  - sources/frontend/src/services/errors.ts
  - sources/frontend/src/services/http.ts
  - sources/frontend/src/services/inventory.ts
  - sources/frontend/src/services/notifications.ts
  - sources/frontend/src/services/orders.ts
  - sources/frontend/src/services/payments.ts
  - sources/frontend/src/services/products.ts
  - sources/frontend/src/services/token.ts
findings:
  critical: 0
  warning: 7
  info: 6
  total: 13
status: issues_found
---

# Phase 4: Báo cáo Code Review

**Reviewed:** 2026-04-25
**Depth:** standard
**Files Reviewed:** 31
**Status:** issues_found (0 critical / 7 warning / 6 info)

## Tóm tắt

Phase 4 đã xây dựng tầng HTTP có kiểu (typed wrapper), middleware bảo vệ route, AuthProvider, các page đã rewire sang services thật, bộ component error-recovery (Banner/Modal/RetrySection), pipeline OpenAPI codegen và bộ Playwright UAT. Các điểm nóng về **bảo mật được kiểm tra trực tiếp đều đạt**:

- `services/token.ts.clearTokens()` xoá ĐÚNG cả localStorage `accessToken`/`refreshToken` lẫn cookie `auth_present` (`Max-Age=0`) — T-04-04 OK.
- `middleware.ts` matcher hẹp đúng phạm vi `/checkout/:path*`, `/profile/:path*`, `/admin/:path*` — T-04-02 OK; KHÔNG đọc / verify JWT, chỉ presence check.
- Cả `services/http.ts` (line 80) và `app/login/page.tsx` (line 27) đều có guard `pathname.startsWith('/') && !pathname.startsWith('//')` cho returnTo — T-04-03 OK; UAT B4b PASS.
- KHÔNG tìm thấy `dangerouslySetInnerHTML`, `innerHTML`, `eval(`, `Function(` ở bất kỳ đâu trong `src/` — T-04-01 sinks không bị mở.
- KHÔNG có `fetch()` trực tiếp ngoài `services/http.ts` (D-10 OK).
- Tất cả accessor localStorage/document.cookie đều có guard `typeof window`.

**Vấn đề chính cần xử lý** xoay quanh độ chắc chắn dữ liệu thay vì lỗ hổng bảo mật:
1. `next/image` nhận `src=""` (chuỗi rỗng) sẽ throw runtime — đây là khả năng cao gây crash render trên `/cart` và phần summary của `/checkout` khi seed cart từ UAT (B1/B2/B5 cố tình set `thumbnailUrl: ''`). Trùng với FE-01 contract gap.
2. ProductCard và một số page tiêu thụ trường rich (`product.category.name`, `product.rating`, `product.reviewCount`, `product.images[]`) không có null-guard — đúng là FE-01 gap được UAT phát hiện; vẫn flag ở đây như code-quality để tracking.
3. `services/http.ts` parse JSON không bọc try/catch — body 5xx không phải JSON (ví dụ HTML error page của gateway) sẽ throw `SyntaxError` thay vì biến thành `ApiError('INTERNAL_ERROR', ...)`, dispatcher mất tác dụng và rơi xuống `catch`/network branch.
4. `http.ts` 401 handler chỉ dùng `pathname` (mất `search`), trong khi `middleware.ts` lưu `pathname + search` — mất query string khi quay lại sau đăng nhập.
5. `Toast.tsx` dùng `Date.now()` làm key/id — trùng id khi 2 toast bắn cùng millisecond → cảnh báo React + dismiss nhầm.
6. `Modal.tsx` không trap Tab focus trong dialog (focus có thể nhảy ra background) — a11y regression nhẹ.
7. `register/page.tsx` không honor `?returnTo=` (login có); thiếu nhất quán.

## Critical Issues

Không có.

## Warnings

### WR-01: `next/image` crash với `src=""` từ cart/checkout

**File:** `sources/frontend/src/app/cart/page.tsx:70`, `sources/frontend/src/app/checkout/page.tsx:253`
**Issue:** Cả hai page render trực tiếp `<Image src={item.thumbnailUrl} ... />`. Type `CartItem.thumbnailUrl: string` không cấm chuỗi rỗng và bộ test UAT (B1/B2/B5 trong `e2e/uat.spec.ts:283, 328, 396, 540`) đều seed `thumbnailUrl: ''`. `next/image` ném `Error: Image is missing required "src" property` khi nhận chuỗi rỗng → unmount cây React, page trắng. Có khả năng cao đây là một trong các nguyên nhân làm UAT FAIL khi `/cart` hoặc summary `/checkout` render bằng cart đã seed.
**Fix:**
```tsx
// app/cart/page.tsx (≈ line 69)
<Image
  src={item.thumbnailUrl?.trim() ? item.thumbnailUrl : '/placeholder.png'}
  alt={item.name}
  fill
  sizes="120px"
  className={styles.itemImg}
/>
```
Áp dụng cùng pattern ở `app/checkout/page.tsx:253` (summary). Hoặc, an toàn hơn, đổi `services/cart.ts.addToCart` để fallback khi `product.thumbnailUrl` rỗng/undefined trước khi ghi localStorage. Tham khảo `app/profile/page.tsx:151` — đã guard đúng bằng `item.productImage ? <Image ... /> : null`.

### WR-02: `services/http.ts` không bắt lỗi `JSON.parse` cho body lỗi

**File:** `sources/frontend/src/services/http.ts:62`
**Issue:** Sau khi `await res.text()`, gọi thẳng `JSON.parse(text)` không try/catch. Khi gateway/Nginx trả HTML 502/504 (rất phổ biến trong dev khi `docker compose stop`), parse lỗi → throw `SyntaxError` xuyên qua phần dispatcher, lên đến page và rơi vào nhánh `if (!isApiError(err))` (toast generic) — đúng *kết quả* mong muốn nhưng KHÔNG đi qua đường `ApiError('INTERNAL_ERROR', ...)`, mất `traceId`/`path`/`status` trên log và phá vỡ contract của `httpGet/httpPost`. Nếu UI sau này dispatch theo `err.status` cho 5xx, branch này sẽ không bao giờ chạy.
**Fix:**
```ts
let parsed: unknown = null;
if (text) {
  try { parsed = JSON.parse(text); }
  catch {
    // Non-JSON body (HTML 5xx page, empty 204, etc.). Throw a normalized ApiError
    // so the dispatcher contract holds.
    if (!res.ok) {
      throw new ApiError('INTERNAL_ERROR', res.status, `Request failed (${res.status})`, [], undefined, path);
    }
    parsed = null;
  }
}
```

### WR-03: Mất query string khi 401 redirect trong `http.ts`

**File:** `sources/frontend/src/services/http.ts:79-85`
**Issue:** Handler 401 chỉ encode `window.location.pathname`, trong khi `middleware.ts:24` lưu `pathname + search`. Nếu user đang ở `/products?category=x&keyword=y` và session hết hạn ở giữa một call, sau khi đăng nhập sẽ quay lại `/products` — mất bộ lọc. Hai code path này nên đồng bộ.
**Fix:**
```ts
const pathname = window.location.pathname;
const search = window.location.search; // already includes leading '?'
if (pathname.startsWith('/') && !pathname.startsWith('//')) {
  const returnTo = encodeURIComponent(pathname + search);
  window.location.href = `/login?returnTo=${returnTo}`;
} else {
  window.location.href = `/login`;
}
```
Lưu ý: guard `startsWith('/')` không cần áp dụng lên `search` vì search luôn là chuỗi sau `?` cùng pathname.

### WR-04: ProductCard truy cập field optional/non-existent không guard (FE-01)

**File:** `sources/frontend/src/components/ui/ProductCard/ProductCard.tsx:72, 95-96, 103`
**Issue:** ProductCard render `product.category.name`, `Math.floor(product.rating)`, `product.reviewCount` trực tiếp. Theo lưu ý phase: backend trả thin DTO, nhiều trường là `undefined` → `Math.floor(undefined)` = `NaN` (không crash nhưng render `NaN ngôi sao`); `product.category.name` khi `category` undefined → `TypeError: Cannot read properties of undefined`, unmount cây — đây chính là contract gap UAT đã ghi (`A1` notes). Đã có gap-closure plan, nhưng cần flag rõ:
**Fix:** Áp dụng pattern null-coalescing y như `app/products/[slug]/page.tsx:174, 183, 191`:
```tsx
{product.category?.name && <span className={styles.category}>{product.category.name}</span>}
...
fill={i < Math.floor(product.rating ?? 0) ? '...' : 'none'}
...
<span className={styles.reviewCount}>({product.reviewCount ?? 0})</span>
```
Đồng thời loại bỏ field-leak khi card render `<Image src={product.thumbnailUrl}>` (line 35) — same pattern as WR-01: nếu `thumbnailUrl` rỗng hoặc undefined, fallback placeholder.

### WR-05: `Toast.tsx` collide id khi tạo 2 toast cùng millisecond

**File:** `sources/frontend/src/components/ui/Toast/Toast.tsx:18`
**Issue:** `const id = Date.now();` — nếu hai `showToast(...)` chạy back-to-back trong cùng một tick (ví dụ catch dispatcher cùng lúc với optimistic-update toast), cả hai cùng id. React phát warning "encountered two children with the same key", và `setTimeout`-cleanup đầu tiên (`filter(t => t.id !== id)`) sẽ xoá CẢ HAI toast → toast thứ hai biến mất sớm 0–3.5s.
**Fix:**
```tsx
const idCounter = useRef(0);
const showToast = useCallback((message, type = 'success') => {
  idCounter.current += 1;
  const id = idCounter.current;
  ...
}, []);
```
Hoặc dùng `crypto.randomUUID()` (đã có ở Node 18+/Next 16 client) — `id: crypto.randomUUID()`.

### WR-06: `Modal.tsx` không trap Tab focus

**File:** `sources/frontend/src/components/ui/Modal/Modal.tsx:52-72`
**Issue:** Modal có `aria-modal="true"`, focus phần tử đầu tiên khi mở, và đóng bằng Esc — nhưng KHÔNG capture phím Tab. Người dùng nhấn Tab có thể nhảy focus ra Header/Footer ở DOM nền (vẫn được scroll-locked nhưng vẫn nhận focus). Đây là regression nhẹ về a11y so với contract `aria-modal`.
**Fix:** Thêm trap đơn giản trong cùng `useEffect`:
```tsx
const onKey = (e: KeyboardEvent) => {
  if (e.key === 'Escape') { onClose(); return; }
  if (e.key !== 'Tab') return;
  const focusables = dialogRef.current?.querySelectorAll<HTMLElement>(
    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
  );
  if (!focusables || focusables.length === 0) return;
  const first = focusables[0];
  const last = focusables[focusables.length - 1];
  if (e.shiftKey && document.activeElement === first) {
    e.preventDefault(); last.focus();
  } else if (!e.shiftKey && document.activeElement === last) {
    e.preventDefault(); first.focus();
  }
};
```

### WR-07: `register/page.tsx` không honor `?returnTo=` như `login`

**File:** `sources/frontend/src/app/register/page.tsx:64`
**Issue:** Sau register thành công gọi `router.replace('/')` cứng, không đọc `searchParams.get('returnTo')`. Login (`app/login/page.tsx:55`) đã làm đúng (bao gồm cả guard open-redirect). Người dùng thường được middleware đẩy vào `/login?returnTo=/checkout`; nếu họ chuyển sang đường dẫn `/register` (qua link "Đăng ký ngay") rồi đăng ký, họ sẽ bị thả về `/` thay vì quay lại `/checkout` — UX bug.
**Fix:** Bọc trong `Suspense` rồi đọc `useSearchParams()` y hệt login, áp cùng guard `startsWith('/') && !startsWith('//')`, rồi `router.replace(returnTo)`.

## Info

### IN-01: `home page.tsx` raw `<img>` cho hero — chỉ là style smell

**File:** `sources/frontend/src/app/page.tsx:82-95`
**Issue:** Đã có `// eslint-disable-next-line @next/next/no-img-element` nên không phải bug, nhưng `next/image` đang được cấu hình cho `images.unsplash.com` ở `next.config.ts:18` — có thể chuyển sang `<Image>` để hưởng tối ưu LCP/AVIF.
**Fix:** Đổi sang `next/image` với `fill` + `sizes` chuẩn; xoá hai dòng eslint-disable.

### IN-02: `playwright.config.ts` — trace='on' luôn ghi credential

**File:** `sources/frontend/playwright.config.ts:11`
**Issue:** `trace: 'on'` ghi mọi network request bao gồm header `Authorization: Bearer ...` ra `e2e/results/`. Trong dev là token mock nên không sao, nhưng nếu UAT chạy CI với token thật cần đổi thành `'on-first-retry'` hoặc `'retain-on-failure'`. Đáng note để Phase 5/CI.
**Fix:** `trace: 'retain-on-failure'`.

### IN-03: `errors.ts` — `code` là `string`, đánh mất exhaustiveness check

**File:** `sources/frontend/src/services/errors.ts:19`
**Issue:** Comment liệt kê union (`'VALIDATION_ERROR' | 'UNAUTHORIZED' | ...`) nhưng type khai báo là `string`. Switch ở `app/checkout/page.tsx:101` không được TS check exhaustive — nếu thêm code mới (ví dụ `'TOO_MANY_REQUESTS'`) sẽ không được nhắc.
**Fix:**
```ts
export type ApiErrorCode =
  | 'VALIDATION_ERROR' | 'UNAUTHORIZED' | 'FORBIDDEN'
  | 'CONFLICT' | 'NOT_FOUND' | 'INTERNAL_ERROR';

constructor(
  public readonly code: ApiErrorCode,
  ...
)
```
Lưu ý: ApiError được construct từ `err.code ?? 'INTERNAL_ERROR'` (string từ network), nên cần cast hoặc giữ rộng — có thể giữ string nhưng export union type cho dispatcher dùng.

### IN-04: `gen-api.mjs` — `npx --yes` auto install không prompt

**File:** `sources/frontend/scripts/gen-api.mjs:38`
**Issue:** `npx --yes openapi-typescript@7.13.0` chạy mỗi lần `npm run gen:api`. Cờ `--yes` khoá supply-chain check — nếu lock-file không pin, vẫn có thể chạy. Mức độ rủi ro thấp (chạy local, version pinned exact), nhưng có thể chuyển vào `devDependencies` để pin qua lockfile.
**Fix:** `npm i -D openapi-typescript@7.13.0` rồi `execFileSync('node', ['./node_modules/openapi-typescript/bin/cli.js', s.url, '-o', outFile])`.

### IN-05: `cart.ts.addToCart` mutate `existing.quantity`

**File:** `sources/frontend/src/services/cart.ts:54`
**Issue:** `existing.quantity += qty` mutate item ngay trong array trả về từ `readCart()`. Hiện tại OK vì `readCart()` luôn JSON.parse fresh, nhưng pattern này dễ vỡ nếu sau này ai đó cache `readCart()` rồi mutate qua tham chiếu. Pattern immutable rõ ràng hơn.
**Fix:**
```ts
const items = readCart();
const idx = items.findIndex(i => i.productId === product.id);
const next = idx >= 0
  ? items.map((i, n) => n === idx ? { ...i, quantity: i.quantity + qty } : i)
  : [...items, { productId: product.id, name: product.name, thumbnailUrl: product.thumbnailUrl, price: product.price, quantity: qty }];
writeCart(next);
```

### IN-06: `e2e/uat.spec.ts` dùng `waitForTimeout` thay vì assertion-based wait

**File:** `sources/frontend/e2e/uat.spec.ts:68, 105, 169, 231, 307, ...`
**Issue:** Nhiều `await page.waitForTimeout(2000)` cố định. Trên runner chậm/CI lần đầu, 2s có thể không đủ; trên dev nhanh, lãng phí thời gian. Playwright khuyến nghị `await expect(locator).toBeVisible()` / `await page.waitForResponse(...)`. Nguy cơ flake tăng dần theo số test.
**Fix:** Mỗi chỗ `waitForTimeout`, đổi sang `await expect(page.getByText('...')).toBeVisible({ timeout: 5000 })` hoặc `await page.waitForResponse(res => res.url().includes('/api/orders'))`.

---

## Tham chiếu chéo (đã verify, KHÔNG flag — chỉ ghi nhận)

| Mục | Vị trí | Trạng thái |
|-----|--------|-----------|
| `clearTokens()` xoá đủ | `services/token.ts:38-43` | OK — xoá cả localStorage + cookie `Max-Age=0` |
| 401 handler clear trước redirect | `services/http.ts:74-86` | OK — `clearTokens()` gọi trước `window.location.href` |
| Open-redirect guard ở http.ts | `services/http.ts:80` | OK — `pathname.startsWith('/') && !startsWith('//')` |
| Open-redirect guard ở login | `app/login/page.tsx:26-27` | OK — cùng pattern |
| Middleware matcher hẹp | `middleware.ts:32` | OK — chỉ `/checkout`, `/profile`, `/admin` |
| Không có direct `fetch()` ngoài http.ts | grep toàn `src/` | OK — chỉ `http.ts:50` |
| Không có `dangerouslySetInnerHTML` / `eval` / `innerHTML` | grep toàn `src/` | OK — không match |
| SSR-safe localStorage | tất cả accessor | OK — đều có guard `typeof window` |
| `not-found.tsx` đã được tạo | `app/not-found.tsx` | OK — phục vụ slug 404 |
| Auth `services/auth.ts` chưa có endpoint backend | `services/auth.ts` | Known deviation — login/register page dùng mock |

---

_Reviewed: 2026-04-25_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
