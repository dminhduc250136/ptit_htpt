# Phase 4: Frontend Contract Alignment + E2E Validation - Pattern Map

**Mapped:** 2026-04-24
**Files analyzed:** 32 new/modified files (grouped by layer below)
**Analogs found:** 22 exact or role-match / 10 no-analog (flagged at bottom)

Consumers: planner writing Plan 04-01 (HTTP tier + codegen + middleware), Plan 04-02 (page rewires + error UI), Plan 04-03 (UAT + cleanup).

Source of truth for language: Vietnamese for all user-facing copy (see UI-SPEC Copywriting Contract). Source code identifiers remain English.

---

## File Classification

### Group A — Env / Codegen scaffolding

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `sources/frontend/package.json` | config | build-time | `sources/frontend/package.json` (self, add `gen:api` + devDep) | exact (edit) |
| `sources/frontend/scripts/gen-api.mjs` | codegen runner (Node script) | build-time / file-I/O | **no analog** (no `scripts/` dir exists) | none — use RESEARCH Pattern 3 |
| `sources/frontend/.env.example` | config | build-time | **no analog** (no `.env*` in repo) | none — trivial one-liner |
| `sources/frontend/.env.local` | config (git-ignored) | build-time | **no analog** | none |
| `sources/frontend/src/types/api/*.generated.ts` (×6) | generated type module | transform (build artifact) | **no analog** (all types currently hand-written in `types/index.ts`) | none — tool output |

### Group B — HTTP tier (services/)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/services/errors.ts` | utility (error class) | transform | **no analog** (no error classes in repo) | none — use RESEARCH Pattern 1 |
| `src/services/token.ts` | utility (storage) | file-I/O (localStorage + cookie) | **no analog** (no auth/storage helpers exist) | none — use RESEARCH Pattern 4 |
| `src/services/http.ts` | service (HTTP wrapper) | request-response | `src/services/api.ts` (function-based abstraction pattern) | role-match (swap mock internals) |
| `src/services/auth.ts` | service (domain) | request-response | `src/services/api.ts` (named-function export convention) | role-match |
| `src/services/products.ts` | service (domain) | request-response | `src/services/api.ts` lines 17-79 (existing `getProducts`) | exact (role + data flow) |
| `src/services/orders.ts` | service (domain) | request-response (CRUD) | `src/services/api.ts` (function-based abstraction) | role-match |
| `src/services/cart.ts` | service (client-only state) | file-I/O (localStorage) | `src/services/api.ts` (function-based abstraction) | role-match |
| `src/services/payments.ts` | service (domain) | request-response | `src/services/api.ts` | role-match |
| `src/services/inventory.ts` | service (domain) | request-response | `src/services/api.ts` | role-match |
| `src/services/notifications.ts` | service (domain) | request-response | `src/services/api.ts` | role-match |
| `src/services/api.ts` | utility (refactor) | transform | `src/services/api.ts` self | exact (edit) — keep `formatPrice`/`formatPriceShort` |
| `src/types/index.ts` | types (edit) | transform | self | exact (edit) — trim backend DTO duplicates |

### Group C — Route protection + Providers

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `sources/frontend/middleware.ts` (root, NOT under `src/`) | middleware | request-response (edge/server) | **no analog** (no middleware exists) | none — Next.js framework convention (RESEARCH Pattern 4) |
| `src/providers/AuthProvider.tsx` | provider (React Context) | event-driven | `src/components/ui/Toast/Toast.tsx` (only Context provider in repo) | role-match |
| `src/app/layout.tsx` | config / root layout | event-driven | self (edit to wrap `<ToastProvider>` + `<AuthProvider>`) | exact (edit) |

### Group D — Error-recovery UI components

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/components/ui/Banner/Banner.tsx` | UI primitive | presentation | `src/components/ui/Toast/Toast.tsx` (error-tinted presentational surface) | role-match |
| `src/components/ui/Banner/Banner.module.css` | styling | — | `src/components/ui/Input/Input.module.css` lines 41-44 (`hasError` pattern: border-left + error-container tint) | role-match |
| `src/components/ui/Modal/Modal.tsx` | UI primitive | presentation | `src/components/ui/Toast/Toast.tsx` (portal-like fixed overlay) + inline success modal in `app/checkout/page.tsx` lines 46-70 | role-match |
| `src/components/ui/Modal/Modal.module.css` | styling | — | `src/components/ui/Button/Button.module.css` (token/variant CSS-module pattern) | partial |
| `src/components/ui/RetrySection/RetrySection.tsx` | UI primitive | presentation | `src/components/ui/Button/Button.tsx` (minimal prop contract) + empty-state block in `app/cart/page.tsx` lines 49-58 | role-match |
| `src/components/ui/RetrySection/RetrySection.module.css` | styling | — | `src/components/ui/Toast/Toast.module.css` (token-only, no raw px) | role-match |
| `src/components/ui/Toast/Toast.tsx` | UI primitive (edit) | event-driven | self — add `aria-label="Đóng thông báo"` on line 33 | exact (edit) |
| `src/components/ui/index.ts` | barrel (edit) | — | self — add Banner/Modal/RetrySection re-exports | exact (edit) |

### Group E — Page rewires

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/app/login/page.tsx` | page (rewire) | request-response | self (lines 1-112) — swap mock `setTimeout` for `services/auth.login` | exact (edit) |
| `src/app/register/page.tsx` | page (rewire) | request-response | `src/app/login/page.tsx` (same form pattern) | exact |
| `src/app/cart/page.tsx` | page (rewrite) | file-I/O (localStorage read) | self — swap `mockProducts[]` seed for `services/cart.readCart()` | exact (edit) |
| `src/app/checkout/page.tsx` | page (rewrite) | request-response + CRUD | self (lines 1-154) — swap `setTimeout` for `services/orders.createOrder` + add error dispatcher | exact (edit) |
| `src/app/products/page.tsx` | page (rewire) | request-response | self — swap `mockProducts` for `services/products.listProducts` | exact (edit) |
| `src/app/products/[slug]/page.tsx` | page (rewire) | request-response | self — swap for `services/products.getProductBySlug` | exact (edit) |
| `src/app/profile/page.tsx` | page (rewire) | request-response | self — order history via `services/orders.listMyOrders` | exact (edit) |
| `src/app/search/page.tsx` | page (rewire) | request-response | `src/app/products/page.tsx` (same list pattern) | exact |
| `src/app/page.tsx` | page (rewire) | request-response | self — featured/new from `services/products.listProducts` | exact (edit) |

### Group F — Phase deliverables

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `.planning/phases/04-.../04-UAT.md` | UAT deliverable | — | `.planning/phases/03-.../03-UAT.md` (Phase 3 UAT; verify path exists) | role-match |
| `sources/frontend/README.md` | docs (edit) | — | self (default Next.js template) | exact (edit) |

---

## Pattern Assignments

### `src/services/http.ts` (service, request-response) — NEW, no analog

**Closest analog:** `src/services/api.ts` (function-based named exports) — borrow the **export style and JSDoc header**, replace body entirely.

**Analog `api.ts` top-of-file pattern** (lines 1-13, use verbatim for `http.ts` file header):
```typescript
/**
 * API Service Abstraction Layer
 *
 * This module abstracts all data fetching. Currently uses mock data.
 * When real APIs are ready, swap the mock imports for fetch() calls
 * WITHOUT touching any UI components.
 */

import { Product, Category, PaginatedResponse, ProductFilter } from '@/types';
import { mockProducts, mockCategories } from '@/mock-data/products';

// Simulate network delay
const delay = (ms: number = 500) => new Promise(resolve => setTimeout(resolve, ms));
```

**What to copy:**
- Leading JSDoc block (update copy: "real HTTP wrapper, envelope-unwrapping, throws ApiError").
- `@/types` import alias style.
- Named `export async function` convention (not classes).
- Explicit return type (`Promise<...>`) on every exported function.

**What to replace with RESEARCH Pattern 1** (04-RESEARCH.md §Pattern 1 lines 282-407):
- The wrapper body — uses native `fetch`, reads `getAccessToken()` at call time, sets `Authorization: Bearer`, parses envelope, throws `ApiError`.
- Export surface: `httpGet`, `httpPost`, `httpPut`, `httpPatch`, `httpDelete`.

**Env var pattern** (required by `code_context`):
```typescript
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
```
Default must match gateway host in `api-gateway/application.yml`. `NEXT_PUBLIC_` prefix is load-bearing (Pitfall 3 in RESEARCH).

---

### `src/services/{domain}.ts` (service, request-response) — NEW, role-match to `api.ts`

**Analog:** `src/services/api.ts` lines 17-79 (`getProducts`).

**Export pattern to copy** (lines 15-17):
```typescript
// ===== PRODUCT SERVICE API =====

export async function getProducts(filter?: ProductFilter): Promise<PaginatedResponse<Product>> {
```

**Section header convention:** each domain module starts with a comment banner `// ===== {DOMAIN} SERVICE API =====` matching `api.ts` style. Keeps greppability when reviewers skim multiple modules.

**Signature style to keep:**
- `export async function verbObject(params)` (not `VerbObject` or `object.verb`).
- Optional `params` object for query-string builders (pattern already in use by `getProducts`).
- Return types are always `Promise<Unwrapped>` — the envelope has already been stripped by `http.ts`.

**Body replacement** (RESEARCH Pattern 2, lines 417-438):
```typescript
import type { paths } from '@/types/api/products.generated';
import { httpGet } from './http';

type ProductListResponse =
  paths['/products']['get']['responses']['200']['content']['application/json'];

export function listProducts(params?: { page?: number; size?: number; sort?: string; categoryId?: string }) {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  if (params?.size !== undefined) qs.set('size', String(params.size));
  if (params?.sort)               qs.set('sort', params.sort);
  if (params?.categoryId)         qs.set('categoryId', params.categoryId);
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<ProductListResponse>(`/api/products/products${suffix}`);
}
```

Gateway prefix (`/api/{service}/...`) is locked by Phase 2 D-08 (see `04-CONTEXT.md` §Canonical References → `api-gateway/application.yml`). Inner path is discovered from the generated `paths` keys after `npm run gen:api`.

---

### `src/components/ui/Banner/Banner.tsx` (UI primitive, presentation) — NEW

**Folder layout analog:** `src/components/ui/Input/Input.tsx` + `Input.module.css` (co-located pair).

**File-layout rule** (from CONVENTIONS.md §Directory Organization + STRUCTURE.md §Naming Conventions):
- Directory: `components/ui/Banner/`
- Files: `Banner.tsx`, `Banner.module.css` (same casing as dir/component)
- Export: `export default function Banner(props) { ... }`
- Re-export from barrel `src/components/ui/index.ts`

**Imports analog** (copy from `Input.tsx` lines 1-10):
```typescript
import React, { forwardRef, useId } from 'react';
import styles from './Input.module.css';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  icon?: React.ReactNode;
  fullWidth?: boolean;
}
```

**What to copy literally:**
- `import styles from './Banner.module.css';` (CSS-modules convention; see CONVENTIONS.md §Frontend Import Organization).
- Named prop interface above the component (PascalCase).
- Default export at the bottom.

**Prop contract for Banner** (derived from UI-SPEC §Validation Banner lines 197-216):
```typescript
interface BannerProps {
  tone?: 'error';           // only 'error' used in Phase 4; reserved for future 'info'/'warning'
  count?: number;            // optional; drives subtext "{N} trường cần được sửa trước khi tiếp tục."
  children?: React.ReactNode; // main message (default: "Vui lòng kiểm tra các trường bị lỗi")
  role?: 'alert';            // default 'alert'; ariaLive auto-bound to 'assertive'
}
```

**CSS-module analog:** `src/components/ui/Input/Input.module.css` lines 41-44 is the **canonical error-tint recipe**, reuse verbatim:
```css
.inputContainer.hasError {
  border-bottom-color: var(--error);
  background-color: rgba(255, 218, 214, 0.30);
}
```
Banner replaces `border-bottom` with `border-left: 4px solid var(--error)` per UI-SPEC.

Full Banner-CSS contract to build (from UI-SPEC):
- Background: `rgba(255, 218, 214, 0.30)` (same literal used in Input — keeps a single magic value in codebase).
- Border-left: `4px solid var(--error)`.
- Radius: `var(--radius-lg)`; Padding: `var(--space-3)`; Margin-bottom: `var(--space-4)`.
- Text color: `var(--on-error-container)`.
- All spacing MUST be `var(--space-N)` tokens (UI-SPEC Spacing Scale — "Hardcoded px values are a checker failure").

---

### `src/components/ui/Modal/Modal.tsx` (UI primitive, presentation) — NEW

**Analog 1 (provider pattern for portal-like overlay):** `src/components/ui/Toast/Toast.tsx` lines 14-39 (`ToastProvider` renders a fixed container that overlays content).

**Analog 2 (inline success modal already in codebase):** `src/app/checkout/page.tsx` lines 46-70.

**Inline success-modal pattern to generalize** (checkout/page.tsx lines 46-70):
```typescript
if (showSuccess) {
  return (
    <div className={styles.page}>
      <div className={styles.successOverlay}>
        <div className={styles.successModal}>
          <div className={styles.successIcon}>
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="2">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
          </div>
          <h2 className={styles.successTitle}>Đặt hàng thành công!</h2>
          <p className={styles.successDesc}>
            Mã đơn hàng: <strong>DA-20241108-003</strong><br />
            Cảm ơn bạn đã mua sắm tại The Digital Atélier.
          </p>
          <div className={styles.successActions}>
            <Button href="/profile">Xem đơn hàng</Button>
            <Button href="/products" variant="secondary">Tiếp tục mua sắm</Button>
          </div>
        </div>
      </div>
    </div>
  );
}
```

**Extraction direction:** lift this pattern into `Modal.tsx` with a generic title/body/actions prop surface. The checkout success path **should migrate to** the new `<Modal>` once built (not required, but keeps one modal primitive in the tree).

**Prop contract for Modal** (derived from UI-SPEC §Surface 2):
```typescript
interface ModalProps {
  open: boolean;
  onClose: () => void;          // bound to Esc + backdrop click
  titleId?: string;             // for aria-labelledby
  bodyId?: string;              // for aria-describedby
  title: React.ReactNode;
  children: React.ReactNode;    // body
  primaryAction: { label: string; onClick: () => void; variant?: 'primary' };
  secondaryAction?: { label: string; onClick: () => void; variant?: 'secondary' | 'danger' };
}
```

**Client component directive required:** Modal uses `useEffect` (focus trap, body-scroll lock) so it MUST begin with `'use client';` — same pattern as `Toast.tsx` line 1 and `ProductCard.tsx` line 1 (the two existing client components in `components/ui/`).

**Accessibility contract to implement** (UI-SPEC §Surface 2 "Behavior"):
- `role="dialog"` + `aria-modal="true"` + `aria-labelledby` + `aria-describedby`.
- Focus trap on first focusable.
- Esc closes.
- Backdrop click closes.
- `overflow: hidden` on `<html>` while open.

---

### `src/components/ui/RetrySection/RetrySection.tsx` (UI primitive, presentation) — NEW

**Analog 1 (empty-state block in the same codebase):** `src/app/cart/page.tsx` lines 49-58 (empty cart block).

**Pattern to copy** (cart/page.tsx lines 48-58):
```typescript
{cartItems.length === 0 ? (
  <div className={styles.emptyCart}>
    <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1">
      <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
      <line x1="3" y1="6" x2="21" y2="6" />
      <path d="M16 10a4 4 0 0 1-8 0" />
    </svg>
    <h2>Giỏ hàng trống</h2>
    <p>Bạn chưa có sản phẩm nào trong giỏ hàng</p>
    <Button href="/products" size="lg">Tiếp tục mua sắm</Button>
  </div>
) : ( ... )}
```

**What to keep:**
- Inline SVG (no external icon lib — STRUCTURE.md + UI-SPEC §Design System both state "inline SVG").
- Centered layout: icon on top, heading, body paragraph, primary action button.
- Reuse existing `<Button>` for the action (variant primary, per UI-SPEC Color §Accent reserved for).

**Prop contract** (from UI-SPEC §Surface 3):
```typescript
interface RetrySectionProps {
  onRetry: () => void;
  loading?: boolean;            // forwards to <Button loading={loading}>
  heading?: string;             // default "Không tải được dữ liệu"
  body?: string;                // default "Đã xảy ra lỗi khi tải. Vui lòng thử lại."
}
```

**Hard copy defaults** (UI-SPEC §Inline Retry CTA — DO NOT paraphrase):
- heading: `Không tải được dữ liệu`
- body: `Đã xảy ra lỗi khi tải. Vui lòng thử lại.`
- button label: `Thử lại`

---

### `src/providers/AuthProvider.tsx` (provider, event-driven) — NEW

**Analog:** `src/components/ui/Toast/Toast.tsx` lines 1-39 — the only existing React-Context provider in the codebase.

**Full analog excerpt to copy the skeleton from** (`Toast.tsx` lines 1-24):
```typescript
'use client';

import React, { createContext, useContext, useState, useCallback } from 'react';
import styles from './Toast.module.css';

interface ToastItem { id: number; message: string; type: 'success' | 'error' | 'info'; }

const ToastContext = createContext<{ showToast: (message: string, type?: 'success' | 'error' | 'info') => void }>({
  showToast: () => {},
});

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info' = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 3500);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
```

**What to copy for AuthProvider:**
- `'use client';` directive line 1.
- `createContext` + default fallback object shape.
- `export const useAuth = () => useContext(AuthContext)` hook export alongside the Provider.
- `Provider function signature `({ children }: { children: React.ReactNode })`.

**What differs:**
- Initial state hydrated from `localStorage` (guard with `typeof window !== 'undefined'` — RESEARCH Pitfall 2).
- Subscribe to `storage` events + the custom `cart:change` event (see RESEARCH Pattern 5 line 582) so Header badge updates live.
- No styles import — AuthProvider is pure state, no DOM.

---

### `src/app/layout.tsx` (config, edit) — MODIFIED

**Analog:** self (lines 21-35). Current layout wraps `<Header />` and `<Footer />` but does NOT wrap `<ToastProvider>` or any auth provider. This is RESEARCH Q5 — a missing mount that would make `useToast()` calls silently no-op.

**Current body to edit** (layout.tsx lines 21-35):
```typescript
export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className={beVietnamPro.variable}>
      <body>
        <Header />
        <main style={{ flex: 1 }}>{children}</main>
        <Footer />
      </body>
    </html>
  );
}
```

**Target shape** (planner writes):
```typescript
return (
  <html lang="vi" className={beVietnamPro.variable}>
    <body>
      <AuthProvider>
        <ToastProvider>
          <Header />
          <main style={{ flex: 1 }}>{children}</main>
          <Footer />
        </ToastProvider>
      </AuthProvider>
    </body>
  </html>
);
```

Rationale: AuthProvider outermost so ToastProvider can call `useAuth()` (future); `<Header />` inside both so badges/avatar can subscribe.

---

### `src/app/login/page.tsx` (page, rewire) — MODIFIED

**Analog:** self (lines 1-112) — the existing form already uses `Input`, `Button`, client validation, Vietnamese copy, and a `loading` flag. This is already the target shape structurally; only the submit body changes.

**Current submit pattern to replace** (lines 15-30):
```typescript
const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();
  const newErrors: typeof errors = {};
  if (!email.trim()) newErrors.email = 'Vui lòng nhập email';
  if (!password.trim()) newErrors.password = 'Vui lòng nhập mật khẩu';
  if (Object.keys(newErrors).length > 0) {
    setErrors(newErrors);
    return;
  }
  setErrors({});
  setLoading(true);
  // Mock login delay
  await new Promise(r => setTimeout(r, 1500));
  setLoading(false);
  alert('Đăng nhập thành công! (Mock)');
};
```

**Replace body with** (from RESEARCH §Common Operation 2, lines 787-812 + UI-SPEC D-08):
```typescript
import { login } from '@/services/auth';
import { isApiError } from '@/services/errors';
import { useRouter, useSearchParams } from 'next/navigation';

const router = useRouter();
const rawReturnTo = useSearchParams().get('returnTo') || '/';
// Open-redirect mitigation (RESEARCH Security §Open redirect via returnTo)
const returnTo = rawReturnTo.startsWith('/') && !rawReturnTo.startsWith('//') ? rawReturnTo : '/';

try {
  await login({ email, password });     // setTokens() inside writes localStorage + auth_present cookie
  router.replace(returnTo);
} catch (err) {
  if (isApiError(err)) {
    if (err.code === 'VALIDATION_ERROR') {
      const byField: Record<string,string> = {};
      for (const f of err.fieldErrors) byField[f.field] = f.message;
      setErrors(byField);
    } else if (err.code === 'UNAUTHORIZED') {
      setErrors({ email: 'Sai email hoặc mật khẩu' });
    }
  }
}
```

**What to keep literally:**
- JSX block lines 42-88 (form + two `<Input>` + submit button) — NO visual change.
- Client pre-submit fallback copy (lines 18-19) — already matches UI-SPEC §Inline Field Errors fallback copy `Vui lòng nhập {field label lowercased}`.
- Page header `Đăng nhập` / `Chào mừng trở lại! Đăng nhập để tiếp tục mua sắm` (lines 36-40).

---

### `src/app/cart/page.tsx` (page, rewrite) — MODIFIED

**Analog:** self (lines 1-152). Layout, summary sidebar, item rows all correct as-is. Only the **data source** flips from `mockProducts` seed to `services/cart.readCart()`.

**Current hard-coded seed to remove** (lines 8, 16-21):
```typescript
import { mockProducts } from '@/mock-data/products';

const [cartItems, setCartItems] = useState<MockCartItem[]>([
  { product: mockProducts[0], quantity: 1 },
  { product: mockProducts[1], quantity: 2 },
  { product: mockProducts[3], quantity: 1 },
]);
```

**Replace with** (RESEARCH Pattern 5 + Pitfall 2):
```typescript
import { readCart, writeCart, removeFromCart, CartItem } from '@/services/cart';

const [cartItems, setCartItems] = useState<CartItem[]>([]);
useEffect(() => {
  // Hydrate in useEffect only — localStorage is undefined during SSR (Pitfall 2).
  setCartItems(readCart());
  const onChange = () => setCartItems(readCart());
  window.addEventListener('cart:change', onChange);
  return () => window.removeEventListener('cart:change', onChange);
}, []);
```

**What to keep:**
- All JSX (lines 39-150) — empty state, summary calculations, "Tiến hành thanh toán" button.
- `formatPrice` import — stays in `services/api.ts` per RESEARCH §File Layout Proposal Group B ("Keep `formatPrice`, `formatPriceShort`").

---

### `src/app/checkout/page.tsx` (page, rewrite) — MODIFIED

**Analog:** self (lines 1-154). Form grid, payment options, summary aside, success modal are all correct. Replace the **submit body** with real POST + error dispatcher; **integrate** new Banner + Modal components.

**Current submit stub to replace** (lines 37-43):
```typescript
const handleSubmit = async (e: React.FormEvent) => {
  e.preventDefault();
  setLoading(true);
  await new Promise(r => setTimeout(r, 1500));
  setLoading(false);
  setShowSuccess(true);
};
```

**Replace with** (RESEARCH §Common Operation 3, lines 815-864):
```typescript
const { showToast } = useToast();
const [fieldErrors, setFieldErrors] = useState<Record<string,string>>({});
const [bannerVisible, setBannerVisible] = useState(false);
const [stockModal, setStockModal] = useState<StockConflictItem[] | null>(null);
const [paymentModal, setPaymentModal] = useState(false);

async function handleSubmit(e: React.FormEvent) {
  e.preventDefault();
  setLoading(true);
  try {
    const order = await createOrder({
      items: readCart().map(i => ({ productId: i.productId, quantity: i.quantity })),
      shippingAddress: { street: form.street, ward: form.ward, district: form.district, city: form.city },
      paymentMethod: form.paymentMethod,
      note: form.note || undefined,
    });
    clearCart();
    setShowSuccess(true);
  } catch (err) {
    if (!isApiError(err)) { showToast('Đã có lỗi, vui lòng thử lại','error'); return; }
    switch (err.code) {
      case 'VALIDATION_ERROR': {
        const byField: Record<string,string> = {};
        for (const f of err.fieldErrors) byField[f.field] = f.message;
        setFieldErrors(byField); setBannerVisible(true); break;
      }
      case 'CONFLICT': {
        const d = err.details as any;
        if (d?.domainCode === 'STOCK_SHORTAGE' || Array.isArray(d?.items)) setStockModal(d.items);
        else setPaymentModal(true);
        break;
      }
      case 'UNAUTHORIZED': break;   // http.ts already redirected
      case 'FORBIDDEN':    showToast('Bạn không có quyền', 'error'); break;
      default:             showToast('Đã có lỗi, vui lòng thử lại', 'error');
    }
  } finally { setLoading(false); }
}
```

**JSX additions** (above the form grid, lines 83-84):
```tsx
{bannerVisible && <Banner count={Object.keys(fieldErrors).length}>Vui lòng kiểm tra các trường bị lỗi</Banner>}
<Modal open={!!stockModal} onClose={() => setStockModal(null)}
       title="Một số sản phẩm không đủ hàng"
       primaryAction={{ label: 'Cập nhật số lượng', onClick: handleStockUpdate }}
       secondaryAction={{ label: 'Xóa khỏi giỏ', variant: 'danger', onClick: handleStockRemove }}>
  {/* per-item rows from stockModal */}
</Modal>
<Modal open={paymentModal} onClose={() => setPaymentModal(false)}
       title="Thanh toán thất bại"
       primaryAction={{ label: 'Thử lại', onClick: handleRetryPayment }}
       secondaryAction={{ label: 'Đổi phương thức thanh toán', variant: 'secondary', onClick: handleChangeMethod }}>
  Giao dịch không thành công. Bạn có thể thử lại hoặc chọn phương thức thanh toán khác.
</Modal>
```

**What to keep literally:**
- Form grid lines 88-100 (Input list with `label`, `value`, `onChange`).
- Payment options block lines 104-118.
- Summary aside lines 122-149.
- Existing success modal (lines 46-70) OR migrate it to new `<Modal>` component with title `Đặt hàng thành công!` — planner's call.

---

### `sources/frontend/middleware.ts` (middleware, server) — NEW, no repo analog

**No codebase analog** — no middleware exists. Use the Next.js 16 convention directly (verified in RESEARCH Pattern 4, lines 519-540).

**Planner must consult:**
- Next.js 16 docs: `nextjs.org/docs/app/api-reference/file-conventions/proxy` (cited in RESEARCH Sources).
- RESEARCH Pattern 4 full example.
- RESEARCH Pitfall 6: keep filename `middleware.ts` (D-12 locked), accept deprecation warning, defer `proxy.ts` rename.

**Location rule** (STRUCTURE.md §Where to Add New Code + RESEARCH §Recommended Project Structure):
- Lives at `sources/frontend/middleware.ts` (sibling of `next.config.ts` and `package.json`), **NOT** under `src/`.
- Per Next.js convention: app-scoped middleware at project root. No alternate location is valid.

**Full content to copy** from RESEARCH §Pattern 4 (lines 526-540):
```typescript
import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const authPresent = req.cookies.get('auth_present')?.value;
  if (!authPresent) {
    const returnTo = encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search);
    const loginUrl = new URL(`/login?returnTo=${returnTo}`, req.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*'],
};
```

Matcher values are verbatim from CONTEXT.md D-12.

---

### `sources/frontend/scripts/gen-api.mjs` (codegen runner, build-time) — NEW, no repo analog

**No codebase analog** — no `scripts/` dir exists. Planner adopts RESEARCH Pattern 3 (lines 449-482) verbatim; it is already production-ready with Windows shell handling and service-port list.

**Planner must consult:** RESEARCH §Pattern 3 (full script body + package.json entry).

**Key things not to accidentally drop:**
- `shell: process.platform === 'win32'` on `execFileSync` — required for `npx.cmd` resolution on native Windows PowerShell (CLAUDE env is Windows 11).
- Per-service direct port (8081–8086), NOT the gateway port — RESEARCH explains why (8081-8086 are bypass paths; gateway rewriting is redundant for codegen).
- `mkdirSync(outDir, { recursive: true })` — first run creates `src/types/api/`.

---

### `src/services/errors.ts`, `src/services/token.ts`, `src/services/cart.ts` (utilities) — NEW, no repo analog

**No codebase analog for error classes, token storage, or localStorage cart** — the repo has no precedent. Planner uses RESEARCH Pattern 1 (errors + token) and Pattern 5 (cart) verbatim. All three follow the same export style as `src/services/api.ts`:
- Named `export function` / `export class` / `export const`.
- Top-of-file JSDoc block.
- No default exports.

**Client-only guard pattern** (from RESEARCH Pattern 1 lines 306-307 + Pattern 5 lines 572-574 — repeated three times, lock as shared pattern below):
```typescript
if (typeof window === 'undefined') return null; // or [] for cart
```

---

### `src/types/index.ts` (types, edit) — MODIFIED

**Analog:** self. Keep UI-shape types; remove backend-derived DTOs once pages migrate.

**Types to KEEP (UI-owned):**
- `ProductFilter` (line 95 region) — client-side sort/filter tuple.
- `PaginatedResponse<T>` (lines 95-103) — matches Phase 2 D-05 shape; listed as reusable asset in CONTEXT.md §Reusable Assets.
- `CartItem` (lines 107-112) — client-only shape; reused by `services/cart.ts`. **Note:** RESEARCH Pattern 5 redefines `CartItem` inside `cart.ts`. Planner must reconcile — recommend re-export from `@/types` to avoid divergence.
- `CreateOrderRequest` (lines 150-155) — already aligned per CONTEXT.md §Reusable Assets; validate against `orders.generated.ts` once codegen runs.

**Types to REMOVE after migration:**
- `User`, `Product`, `Category`, `Order`, `OrderItem`, `Payment`, `Review`, `Address`, `AuthResponse` — backend-derived, now come from `types/api/*.generated.ts`.
- Plan must script the removal **after** page rewires reference `@/types/api/*` — removing early breaks pages (D-04 applies in staged order).

---

## Shared Patterns

### Shared Pattern 1: Client-component directive placement

**Source:** `src/components/ui/Toast/Toast.tsx` line 1, `src/components/ui/ProductCard/ProductCard.tsx` line 1, `src/app/cart/page.tsx` line 1.

**Apply to:** All new files that use `useState`, `useEffect`, `useContext`, event handlers, or `localStorage`. Explicitly required on: `AuthProvider.tsx`, `Modal.tsx`, `Banner.tsx` (if it takes click handler), `RetrySection.tsx`, all `app/*/page.tsx` that are rewired.

```typescript
'use client';

import React, { ... } from 'react';
```

Directive MUST be the literal first line (string-literal expression); the blank line after is convention.

---

### Shared Pattern 2: CSS-module + token-only styling

**Source:** `src/components/ui/Input/Input.module.css` lines 1-80, `src/components/ui/Toast/Toast.module.css` lines 1-10.

**Apply to:** All new `*.module.css` in `components/ui/{NewComponent}/`.

**Canonical rules:**
- Import statement: `import styles from './ComponentName.module.css';` (relative, co-located).
- Class names: camelCase (e.g., `.inputContainer`, not `.input-container`). Verified in existing files.
- Use `var(--token)` ONLY for colors, spacing, typography, radius, shadow. Two exceptions currently in tree:
  - `rgba(255, 218, 214, 0.30)` in `Input.module.css` line 43 — reuse this literal for Banner (same UI-SPEC callout).
  - `rgba(25, 28, 30, 0.40)` for Modal backdrop — UI-SPEC explicitly documents this as the ONE allowed non-token value (derived from `--on-surface` at 40% alpha).
- All spacing MUST be `var(--space-N)` (UI-SPEC §Spacing Scale: "Hardcoded px values are a checker failure").

---

### Shared Pattern 3: Function-based service module + named exports

**Source:** `src/services/api.ts` lines 1-79 (and the whole file structurally).

**Apply to:** All new `services/*.ts` modules (`http.ts`, `auth.ts`, `products.ts`, `orders.ts`, `cart.ts`, `payments.ts`, `inventory.ts`, `notifications.ts`).

**Rules:**
- No classes, no default export.
- `export async function verbObject(params): Promise<Unwrapped>` signature.
- Section-header comment per domain: `// ===== {DOMAIN} SERVICE API =====`.
- JSDoc on the module-level `/** ... */` block.
- Import style: `import { ... } from '@/types'` (path alias, not relative).

Keeps page imports identical between mock and real phase (FE-01 contract-preservation spirit; see CONTEXT.md §Reusable Assets "Function-based `api.ts` pattern — minimal refactor cost").

---

### Shared Pattern 4: Vietnamese user copy (source of truth: UI-SPEC)

**Source:** UI-SPEC §Copywriting Contract (banner, modal variants, toast, retry section) + `src/app/login/page.tsx` lines 18-19 (pre-submit copy) + `src/app/cart/page.tsx` lines 42-56 (cart copy).

**Apply to:** Every new/modified user-facing string in Phase 4.

**Hard strings** (UI-SPEC — do NOT paraphrase):
- Banner: `Vui lòng kiểm tra các trường bị lỗi`
- Stock modal title: `Một số sản phẩm không đủ hàng`
- Payment modal title: `Thanh toán thất bại`
- Toast error: `Đã có lỗi, vui lòng thử lại`
- Retry section heading: `Không tải được dữ liệu`
- Retry section body: `Đã xảy ra lỗi khi tải. Vui lòng thử lại.`
- Retry button: `Thử lại`
- Forbidden toast (D-09 Claude discretion): `Bạn không có quyền`

**Fallback form-validation copy** (UI-SPEC §Inline Field Errors fallback) — already present in login page lines 18-19; register/checkout follow the same templates.

---

### Shared Pattern 5: Error-dispatcher contract in pages

**Source:** RESEARCH Pattern 6 (lines 624-671) + §Common Operation 3 (lines 815-864).

**Apply to:** Every page that makes a mutating call (checkout, login, register) + every list page that handles 5xx (products, search, profile, home).

Switch on `err.code` in this order (stop at first match):
1. `VALIDATION_ERROR` → set fieldErrors + show Banner
2. `UNAUTHORIZED` → no-op (http.ts already redirected)
3. `FORBIDDEN` → toast `Bạn không có quyền`
4. `CONFLICT` → Modal (stock vs payment by `details.domainCode` OR `details.items[]` heuristic — RESEARCH Pitfall 4 / Q3)
5. `NOT_FOUND` → context-dependent (empty state vs 404 page)
6. `INTERNAL_ERROR` / default → toast `Đã có lỗi, vui lòng thử lại` (+ RetrySection if list GET)

Wrapper must NOT auto-retry mutations (D-10, RESEARCH Pattern 1 note).

---

### Shared Pattern 6: SSR-safe localStorage access

**Source:** RESEARCH Pattern 1 line 306-308, Pattern 5 lines 572-574 (repeated). Pitfall 2 callout.

**Apply to:** `token.ts`, `cart.ts`, `AuthProvider.tsx`, and any page that reads tokens/cart during render.

```typescript
if (typeof window === 'undefined') return null;   // or [] or undefined
```

OR wrap initial read in `useEffect(() => { setX(readCart()); }, [])` to defer to client-only render pass. This is the single most-likely source of build failures on `next build` (Pitfall 2).

---

## No Analog Found

Files with no close match in the existing codebase. Planner MUST rely on RESEARCH.md (Patterns 1, 3, 4, 5) or cited framework docs for these.

| File | Role | Data Flow | Reason | Planner action |
|------|------|-----------|--------|---------------|
| `sources/frontend/middleware.ts` | middleware | request-response (edge/server) | Next.js convention; no existing middleware in repo. | Copy RESEARCH Pattern 4 verbatim. Cite `nextjs.org/docs/app/api-reference/file-conventions/proxy` in task `read_first`. |
| `sources/frontend/scripts/gen-api.mjs` | codegen runner (Node ESM script) | build-time / file-I/O | No `scripts/` directory exists. | Copy RESEARCH Pattern 3 verbatim. Include Windows `shell: true` note. |
| `sources/frontend/.env.example` | config | build-time | No `.env*` files present. | One-line file: `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`. |
| `sources/frontend/.env.local` | config (git-ignored) | build-time | No `.env*` files present. | Same content; add to `.gitignore` if not covered. |
| `src/types/api/*.generated.ts` (×6) | generated type modules | transform (build artifact) | No codegen output in repo. | Produced by `npm run gen:api`; commit as-is. Do not hand-edit. |
| `src/services/errors.ts` | utility (error class) | transform | No error classes in repo. | Copy RESEARCH Pattern 1 `ApiError`, `FieldError`, `isApiError`. |
| `src/services/token.ts` | utility (storage) | file-I/O | No auth/storage helpers in repo. | Copy RESEARCH Pattern 1 token helpers + `auth_present` cookie set/clear. |
| `src/services/http.ts` | service (HTTP wrapper) | request-response | `api.ts` is analog only for export style; body is net-new. | Copy RESEARCH Pattern 1 full wrapper. Verify envelope-unwrap assumption in first smoke test (RESEARCH Pitfall 8). |
| `src/services/cart.ts` | service (client-only state) | file-I/O | No localStorage helpers in repo. | Copy RESEARCH Pattern 5. Reconcile `CartItem` shape with `types/index.ts` (see Pattern Assignments note). |
| `src/providers/AuthProvider.tsx` | provider (React Context) | event-driven | No `providers/` directory exists; `ToastProvider` is the only Context provider precedent (role-match only). | Copy `ToastProvider` skeleton (Toast.tsx lines 1-39). Hydrate state from `getAccessToken()` in `useEffect`. |

---

## Key Patterns Identified

1. **Pages are already structurally correct — only data sources flip.** The existing cart/checkout/login pages use `Input`, `Button`, Vietnamese copy, and `loading` flags. Phase 4 rewires the async bodies, not the layouts. This keeps the diffs reviewable.
2. **One Context-Provider precedent in the repo (`ToastProvider`).** Every new provider (`AuthProvider`) copies its skeleton. No other provider style (Redux, Zustand, TanStack) is used, and D-01 forbids adding them.
3. **CSS-modules + design tokens are the ONLY styling path.** No Tailwind, no styled-components, no inline `style={{...}}` beyond the one `{ flex: 1 }` in layout.tsx. New components must follow `Input.module.css` / `Toast.module.css` style.
4. **`services/api.ts` export style is load-bearing.** Function-based named exports let page imports survive the mock→real swap untouched (confirmed in CONTEXT.md §Reusable Assets). All new `services/*.ts` mirror this convention.
5. **The repo has no middleware, no scripts, no providers directory, no env file, no error class, no codegen output.** Eight net-new structural additions, each scaffolded once and reused by subsequent patterns. The planner should sequence these at the start of Plan 04-01 to unblock the rest.

---

## Metadata

**Analog search scope:**
- `sources/frontend/src/components/ui/**/*.{ts,tsx,css}`
- `sources/frontend/src/components/layout/**/*.{ts,tsx}`
- `sources/frontend/src/services/**/*.ts`
- `sources/frontend/src/app/**/*.tsx`
- `sources/frontend/src/types/**/*.ts`
- `sources/frontend/{middleware,proxy,next.config}.ts`
- `sources/frontend/.env*`
- `sources/frontend/scripts/**/*`
- `sources/frontend/package.json`

**Files scanned:** ~30 TS/TSX/CSS source files + 3 config files.

**Canonical references consulted:**
- `04-CONTEXT.md` (D-01..D-14)
- `04-RESEARCH.md` (Patterns 1-6, Pitfalls 1-8, §File Layout Proposal, §Plan Skeleton)
- `04-UI-SPEC.md` (Copywriting Contract, Surfaces 1-4, Existing Component Props Matrix)
- `.planning/codebase/CONVENTIONS.md`
- `.planning/codebase/STRUCTURE.md`

**Pattern extraction date:** 2026-04-24
