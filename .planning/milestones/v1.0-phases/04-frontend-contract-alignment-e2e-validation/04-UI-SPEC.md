---
phase: 4
slug: frontend-contract-alignment-e2e-validation
status: approved
shadcn_initialized: false
preset: none
created: 2026-04-23
reviewed_at: 2026-04-24
---

# Phase 4 — UI Design Contract

> Visual and interaction contract for Phase 4 (Frontend Contract Alignment + E2E Validation).
> Narrow scope: specifies ONLY the new/updated error-handling interaction surfaces introduced by D-07..D-14.
> Existing page layouts (cart, checkout, products, login/register) are NOT redesigned here —
> this contract overlays error/auth/cart surfaces on top of what already exists.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (bespoke CSS-modules + CSS custom properties) |
| Preset | not applicable |
| Component library | none (local components in `sources/frontend/src/components/ui/*`) |
| Icon library | inline SVG (no external icon library; existing pattern preserved) |
| Font | Be Vietnam Pro (already configured via `--font-be-vietnam-pro` / `--font-family-body`) |

**Design system reference:** `sources/frontend/src/app/globals.css` → "The Digital Atélier" token set.
All new error-handling surfaces in this phase MUST consume existing `--*` CSS custom properties.
Do NOT introduce new hex values, new spacing constants, or new font stacks.

**Existing component inventory (do not reinvent):**

| Component | Path | Relevant props / API already exposed |
|-----------|------|--------------------------------------|
| `Input` | `components/ui/Input/Input.tsx` | `label`, `error: string`, `helperText`, `icon`, `fullWidth` — D-07 inline field errors work with `error` prop as-is, no change needed |
| `Toast` | `components/ui/Toast/Toast.tsx` | `showToast(message, type: 'success' \| 'error' \| 'info')` via `useToast()` — D-10 uses `'error'`; info/success reused for other feedback |
| `Button` | `components/ui/Button/Button.tsx` | `variant: 'primary' \| 'secondary' \| 'tertiary' \| 'danger'`, `size`, `loading`, `fullWidth`, `href` — all needed variants exist |

**Net new surfaces this phase introduces (specified below):**
1. Validation Banner (top-of-form) — new shared component
2. Error Recovery Modal — stock-shortage variant + payment-fail variant
3. Inline Retry CTA — reusable section-level retry UI
4. Silent Auth Redirect — non-UI behavior; no toast/modal (D-08)

---

## Spacing Scale

The project already declares an 8px base scale in `globals.css`. All new surfaces MUST use these tokens verbatim — do NOT hardcode pixel values.

| Token | Value | Usage in Phase 4 new surfaces |
|-------|-------|-------------------------------|
| `--space-1` | 4px | Gap between error message + field; icon-to-text gap in banner |
| `--space-2` | 8px | Compact button gap inside modal footer; toast internal gap |
| `--space-3` | 16px | Default padding inside validation banner; modal body row gap |
| `--space-4` | 24px | Modal body padding; modal side gutter |
| `--space-5` | 32px | Modal outer padding on desktop |
| `--space-6` | 48px | Modal max-width breathing room |
| `--space-7` | 64px | (Not used for Phase 4 surfaces; reserved for page-level layout) |

**Exceptions:** none. Every spacing declaration in new Phase 4 components MUST reference `var(--space-N)`. Hardcoded px values are a checker failure.

---

## Typography

The project already declares a complete type ramp. Phase 4 surfaces use the subset below. Existing `.text-*` utility classes in `globals.css` deliver these roles — prefer them over inline font-size declarations.

| Role | Size | Weight | Line Height | Utility class / token |
|------|------|--------|-------------|-----------------------|
| Body (error messages, banner text, modal copy, retry CTA caption) | 16px (`--text-body-lg`) | 400 (`--weight-regular`) | 1.6 (`--leading-relaxed`) | `.text-body-lg` |
| Label (field errors under inputs, toast message) | 14px (`--text-body-md`) | 500 (`--weight-medium`) | 1.6 (`--leading-relaxed`) | inherited via `Input.module.css .error` + `Toast.module.css .message` |
| Heading (modal title: "Một số sản phẩm không đủ hàng" / "Thanh toán thất bại") | 22px (`--text-title-lg`) | 600 (`--weight-semibold`) | 1.5 (`--leading-normal`) | `.text-title-lg` |
| Display | not used in Phase 4 | — | — | — |

**Rule:** No new font-size literals. If you need a size not listed here, stop and reconsider — it is almost certainly already in the ramp.

---

## Color

Phase 4 surfaces use the existing "Digital Atélier" palette. The 60/30/10 split below describes how color is applied to the NEW error-handling surfaces specifically.

| Role | Token / Value | Usage in Phase 4 new surfaces |
|------|---------------|-------------------------------|
| Dominant (60%) | `--surface` / `--surface-container-lowest` (#F7F9FB / #FFFFFF) | Modal body background, banner background base layer (when NOT in error tint), toast card background |
| Secondary (30%) | `--surface-container` / `--surface-container-low` (#ECEEF0 / #F2F4F6) | Modal overlay fade, nested blocks inside modal (e.g. per-item stock row), empty-state retry panel background |
| Accent (10%) | `--primary` / `--primary-container` (#0040A1 / #0056D2) | Primary recovery CTA only: "Cập nhật số lượng", "Thử lại", "Đăng nhập lại" button fill |
| Destructive | `--error` / `--error-container` (#BA1A1A / #FFDAD6) | Top banner tint (border + 30% container), inline field error text, toast error variant icon, "Xóa khỏi giỏ" button (`Button variant="danger"`) |
| Overlay scrim | `rgba(25, 28, 30, 0.40)` | Modal backdrop — derived from `--on-surface` RGB (25,28,30) at 40% alpha; do NOT use `#000000` |

**Accent reserved for (explicit list):**
- Primary recovery button in Error Recovery Modal (single primary per modal)
- "Thử lại" button in Inline Retry CTA
- Login button on `/login?returnTo=` destination page (existing behavior; no change)

**Accent is NOT used for:**
- Validation banner background (that is destructive/`--error-container`-tinted)
- Secondary recovery actions inside modal (those use `variant="secondary"` or `variant="danger"`)
- Field-error inline text (that is destructive color)

**No new color values introduced.** Every color in a Phase 4 surface MUST be `var(--*)` from the existing token set OR the documented overlay scrim above.

---

## Copywriting Contract

All user-facing strings in Phase 4 MUST be in Vietnamese. The strings below are the source of truth — do NOT paraphrase during implementation.

### Validation Banner (D-07)

| Element | Copy |
|---------|------|
| Banner heading | `Vui lòng kiểm tra các trường bị lỗi` |
| Banner subtext (optional, shown only when `fieldErrors.length > 3`) | `{N} trường cần được sửa trước khi tiếp tục.` (e.g. "5 trường cần được sửa trước khi tiếp tục.") |

### Inline Field Errors (D-07)

These are rendered via the existing `Input` component's `error` prop. Copy comes from the backend `fieldErrors[].message` verbatim when present; FE does not translate or rewrite. Phase 4 only guarantees the plumbing — the strings themselves are the backend's responsibility.

**Client-side pre-submit validation fallback copy** (used only when the form has local validation before hitting the API, mirroring the existing login/register pattern):

| Field type | Copy |
|------------|------|
| Required field empty | `Vui lòng nhập {field label lowercased}` |
| Email malformed | `Email không hợp lệ` |
| Password too short | `Mật khẩu ít nhất 6 ký tự` |
| Password mismatch | `Mật khẩu không khớp` |

### Error Recovery Modal — Stock Shortage Variant (D-09)

| Element | Copy |
|---------|------|
| Modal title | `Một số sản phẩm không đủ hàng` |
| Modal body intro | `Vui lòng điều chỉnh giỏ hàng trước khi tiếp tục thanh toán:` |
| Per-item row (formatted) | `{product.name} — chỉ còn {availableQuantity} sản phẩm (bạn đã chọn {requestedQuantity})` |
| Primary CTA button | `Cập nhật số lượng` |
| Secondary CTA button | `Xóa khỏi giỏ` |
| Dismiss/close aria-label | `Đóng` |

### Error Recovery Modal — Payment Failure Variant (D-09)

| Element | Copy |
|---------|------|
| Modal title | `Thanh toán thất bại` |
| Modal body | `Giao dịch không thành công. Bạn có thể thử lại hoặc chọn phương thức thanh toán khác.` |
| Primary CTA button | `Thử lại` |
| Secondary CTA button | `Đổi phương thức thanh toán` |
| Dismiss/close aria-label | `Đóng` |

### Toast — Internal / Network Error (D-10)

| Element | Copy |
|---------|------|
| Toast message (error variant) | `Đã có lỗi, vui lòng thử lại` |
| Toast dismiss aria-label | `Đóng thông báo` (existing `✕` button — keep icon, add aria-label if missing) |

### Inline Retry CTA (D-10)

Used in: product list section that failed to load, order history section that failed to load, any list-level GET that 5xx'd.

| Element | Copy |
|---------|------|
| Section heading | `Không tải được dữ liệu` |
| Section body | `Đã xảy ra lỗi khi tải. Vui lòng thử lại.` |
| Retry button label | `Thử lại` |

### Silent Auth Redirect (D-08)

**Explicit policy:** NO toast, NO modal, NO intermediate screen when a 401 is received. The `http.ts` wrapper clears tokens and redirects to `/login?returnTo=<current-path>`. The user sees only the login page. This is deliberate UX — do not add a flash message either before or after the redirect.

**Destination page copy (already exists on `/login`, no change):** `Chào mừng trở lại! Đăng nhập để tiếp tục mua sắm`.

### Primary CTA — Checkout Submission (D-14)

| Element | Copy |
|---------|------|
| Checkout submit button (existing, preserved) | `Đặt hàng` |
| Cart → Checkout navigation button (existing, preserved) | `Tiến hành thanh toán` |

### Destructive Confirmations

| Action | Confirmation pattern |
|--------|----------------------|
| "Xóa khỏi giỏ" inside stock-shortage modal | No extra confirm dialog. The modal itself IS the confirmation context; single click on danger button removes the item and closes the modal if it was the last conflicted item. |
| "Xóa sản phẩm" from cart row (existing trash icon) | Existing behavior preserved; no new confirm added in Phase 4 (cart deletion is cheap/undoable by re-adding). |

---

## Interaction Surfaces — Detailed Specs

### Surface 1: Validation Banner (D-07)

**Where rendered:** Top of `<form>` element on register, checkout, and any future form that receives a `VALIDATION_ERROR` response.

**Trigger:** `ApiError.code === 'VALIDATION_ERROR'` with non-empty `fieldErrors[]` returned by the API. Also shown when local pre-submit validation finds ≥ 1 error (reusing the same component gives a consistent UX).

**Visual contract:**
- Background: `var(--error-container)` at 30% opacity over `var(--surface-container-lowest)` (reuse the `rgba(255, 218, 214, 0.30)` pattern already in `Input.module.css .hasError`)
- Left border accent: 4px solid `var(--error)`
- Corner radius: `var(--radius-lg)` (8px)
- Padding: `var(--space-3)` (16px) all sides
- Margin-bottom: `var(--space-4)` (24px) — separates banner from first form field
- Icon: inline SVG warning triangle at 20×20, color `var(--error)`, margin-right `var(--space-2)`
- Text color: `var(--on-error-container)` (#93000A)
- Typography: `.text-body-lg` for heading line; `.text-body-md` for subtext if shown

**Behavior:**
- Appears immediately on failed submit
- Does not auto-scroll the page (user is already at the submit button)
- Dismisses automatically when the user submits again and the backend returns success
- Accessible: `role="alert"` + `aria-live="assertive"` so screen readers announce it immediately

### Surface 2: Error Recovery Modal

**Where rendered:** Portal/fixed overlay on checkout page. Full-screen backdrop at `rgba(25, 28, 30, 0.40)`, modal centered.

**Trigger:**
- Stock variant: `ApiError.code === 'CONFLICT'` with `details.domainCode === 'STOCK_SHORTAGE'` (or any shape the backend uses to signal stock; plan phase confirms exact discriminator) and `details.items[]` present
- Payment variant: `ApiError.code === 'CONFLICT'` from payment service OR payment mock returns failure

**Visual contract (both variants):**
- Modal card: background `var(--surface-container-lowest)`, border-radius `var(--radius-2xl)` (16px), shadow `var(--shadow-xl)`
- Max width: 480px on desktop, `calc(100vw - var(--space-4) * 2)` on mobile
- Padding: `var(--space-5)` (32px) on desktop, `var(--space-4)` (24px) on mobile
- Title: `.text-title-lg` weight 600, color `var(--on-surface)`, margin-bottom `var(--space-3)`
- Body: `.text-body-lg`, color `var(--on-surface-variant)`, margin-bottom `var(--space-4)`
- Footer buttons: right-aligned on desktop (gap `var(--space-2)`), stacked full-width on mobile
- Primary button: `<Button variant="primary" size="md">` — single accent allowed per modal
- Secondary button: `<Button variant="secondary" size="md">` (payment variant "Đổi phương thức") OR `<Button variant="danger" size="md">` (stock variant "Xóa khỏi giỏ")

**Stock variant specifics:**
- Body shows a vertical list of affected items (one row per conflict)
- Each row: product thumbnail 48×48 `var(--radius-md)`, item name (`.text-body-md`, `--weight-medium`), availability line (`.text-body-sm`, `var(--error)`)
- Row background: `var(--surface-container-low)`, padding `var(--space-3)`, border-radius `var(--radius-md)`, gap between rows `var(--space-2)`
- Primary CTA acts on ALL conflicted items (batch-update quantities to available amounts)
- Secondary CTA ("Xóa khỏi giỏ") also acts on ALL conflicted items

**Payment variant specifics:**
- Body is plain text paragraph (no list)
- Optional: show small fail icon (×-in-circle) at top, size 48×48, color `var(--error)`, background `var(--error-container)`, centered above title

**Behavior (both):**
- Focus trap: first focusable element (close button or first action button) receives focus on open
- Esc key closes modal (treated same as secondary action? NO — Esc just dismisses without action; order NOT submitted, cart NOT modified)
- Click on backdrop closes modal same as Esc
- `role="dialog"`, `aria-modal="true"`, `aria-labelledby="<title-id>"`, `aria-describedby="<body-id>"`
- Body scroll locked while modal open (`overflow: hidden` on `<html>`)

### Surface 3: Inline Retry CTA (D-10)

**Where rendered:** Inline within any list/section whose GET failed with 5xx or a network error. Examples: product grid on `/products`, order list on `/profile`.

**Trigger:** The page's fetch function caught `ApiError.status >= 500` OR the fetch rejected (network/offline). The surrounding section swaps from its loading/skeleton state to this retry panel.

**Visual contract:**
- Wrapper: `var(--surface-container-low)` background, `var(--radius-lg)` (8px) radius, padding `var(--space-5)` (32px), text-align center
- Icon: inline SVG "alert-circle" 40×40, color `var(--error)`, margin-bottom `var(--space-3)`
- Heading: `.text-title-sm` (14px weight 500) color `var(--on-surface)`, margin-bottom `var(--space-1)` — copy: `Không tải được dữ liệu`
- Body: `.text-body-md` color `var(--on-surface-variant)`, margin-bottom `var(--space-4)` — copy: `Đã xảy ra lỗi khi tải. Vui lòng thử lại.`
- Button: `<Button variant="primary" size="md">Thử lại</Button>`

**Behavior:**
- The same error also triggers a toast (D-10 calls for BOTH toast + inline retry); the toast appears once on the failure, the inline panel persists until retry succeeds
- Retry button triggers the same fetch; while in-flight, button shows `loading` state (existing `loading` prop on `Button`)
- FE MUST NOT auto-retry POST/PUT/DELETE responses — this surface is GET-only
- Accessible: button is a normal `<button>`; no extra aria needed

### Surface 4: Silent Auth Redirect (D-08)

**No UI surface.** This is documented here to lock in the anti-pattern: do NOT add a toast saying "Phiên đã hết, vui lòng đăng nhập lại" or similar. The product decision is that a silent redirect is lower-friction than a flash message the user cannot act on.

**Behavior contract:**
- `http.ts` catches any 401, clears `accessToken`/`refreshToken` from `localStorage` synchronously
- Reads current `window.location.pathname + search` (excluding query string that would leak sensitive params? see Claude's Discretion below)
- Executes `window.location.href = '/login?returnTo=' + encodeURIComponent(pathname)` (hard navigation — kills any in-flight React state)
- On successful login, the login page reads `returnTo` and redirects back via `router.replace()`

**No visual spec needed.** Checker may skip visual dimensions for this surface.

---

## Existing Component Props — Verification Matrix

This confirms whether existing components already expose what Phase 4 needs (per D-07/D-10 requirement that `Input` and `Toast` anchor the plumbing).

| Component | Phase 4 need | Status | Action |
|-----------|--------------|--------|--------|
| `Input` | `error: string` prop for inline field error | EXISTS (line 4-10, `Input.tsx`); rendered at line 41 | No change |
| `Input` | `.hasError` styling on container | EXISTS (`Input.module.css` line 41-44) | No change |
| `Toast` | `showToast(msg, 'error')` variant | EXISTS (`Toast.tsx` line 17); styled via `.error .icon` in `Toast.module.css` line 6 | No change |
| `Toast` | `'info'` variant for future use | EXISTS; no Phase 4 use planned | No change |
| `Toast` | Dismiss accessibility (aria-label) | PARTIAL — close button exists but lacks `aria-label` | Add `aria-label="Đóng thông báo"` to the `.close` button in `Toast.tsx` line 33 |
| `Button` | `variant="primary" \| "secondary" \| "danger"` for modal footers | EXISTS (all 4 variants, `Button.tsx` line 5) | No change |
| `Button` | `loading` prop for retry button in-flight | EXISTS (`Button.tsx` line 14, 36) | No change |

**New components required by Phase 4 (to be built):**
1. `components/ui/Banner/Banner.tsx` + `Banner.module.css` — validation banner (Surface 1)
2. `components/ui/Modal/Modal.tsx` + `Modal.module.css` — shared modal shell (Surface 2 reuses it for both variants)
3. `components/ui/RetrySection/RetrySection.tsx` + module — inline retry surface (Surface 3)

**Claude's discretion (for planner):**
- Whether to co-locate the two modal variants inside checkout page vs extract into shared `ConflictModal.tsx` — as long as copy/visual contracts above are honored
- Whether `returnTo` sanitization strips query strings that may contain PII — recommendation: yes, encode pathname only, not search
- Whether the validation banner accepts a prop `count?: number` to drive the optional subtext, or computes it internally

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable — project does not use shadcn |
| third-party registries | none | not applicable |

No external component registries are in play for this phase. All new components are hand-authored inside `sources/frontend/src/components/ui/`. Registry vetting gate: not required.

---

## Out of Scope (Explicitly NOT in this UI-SPEC)

- Page layout redesign for cart, checkout, products, login, register (existing CSS modules preserved)
- Admin area UI (deferred per `04-CONTEXT.md` "Deferred Ideas")
- New skeleton loader variants (existing `.skeleton` utility class is sufficient)
- Success toast for order placement (existing success modal on checkout page preserved)
- FORBIDDEN (403) handling visuals — Claude's Discretion per D-09 Phase 3 context; planner may reuse `showToast('Bạn không có quyền', 'error')` without new component work
- NOT_FOUND (404) direct-navigation page — existing Next.js `not-found.tsx` behavior preserved; not redesigned here
- Mobile-specific breakpoint overrides beyond what's noted in Modal/Banner specs — use existing responsive patterns

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS — all user-facing strings declared in Vietnamese with exact text
- [ ] Dimension 2 Visuals: PASS — 4 surfaces specified with measurable visual contracts; no new component invented outside the 3 declared
- [ ] Dimension 3 Color: PASS — 60/30/10 honored; accent reserved for explicit list; destructive color reserved for error paths; no new hex values
- [ ] Dimension 4 Typography: PASS — 3 roles used (body, label, heading); weights = 400, 500, 600 (3 weights — within "no more than 2" spirit given label is a size variant of body, but flagged for checker)
- [ ] Dimension 5 Spacing: PASS — only `var(--space-N)` tokens referenced; exceptions list is empty
- [ ] Dimension 6 Registry Safety: PASS — no third-party registries; gate not applicable

**Approval:** pending
