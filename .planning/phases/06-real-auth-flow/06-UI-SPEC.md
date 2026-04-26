---
phase: 6
slug: real-auth-flow
status: draft
shadcn_initialized: false
preset: none
created: 2026-04-26
---

# Phase 6 — UI Design Contract: Real Auth Flow

> Visual và interaction contract cho Phase 6. Được tạo bởi gsd-ui-researcher, xác thực bởi gsd-ui-checker.
> Nguồn chính: globals.css (CSS custom properties đã khai báo đầy đủ), login/register pages hiện có, CONTEXT.md.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none — CSS Modules thuần (không dùng shadcn) |
| Preset | not applicable |
| Component library | Custom — Button, Input, Banner đã có tại `src/components/ui/` |
| Icon library | Inline SVG (stroke-based, 18×18 trong form fields, 20×20 cho button icon) |
| Font | Be Vietnam Pro (đã load qua `--font-be-vietnam-pro` Next.js font variable) |

**shadcn Gate:** `components.json` KHÔNG tồn tại. Stack là Next.js App Router + CSS Modules thuần — shadcn không phù hợp với design system này. Ghi nhận: `Tool: none`.

---

## Spacing Scale

Kế thừa từ `globals.css` — `--space-*` tokens (8px base):

| Token | Value | Usage trong Phase 6 |
|-------|-------|---------------------|
| xs (`--space-1`) | 4px | Icon gap trong Input, khoảng cách label error |
| sm (`--space-2`) | 8px | Gap giữa icon và text trong checkbox, gap giữa elements trong form options |
| md (`--space-3`) | 16px | Padding ngang/dọc input field, gap giữa form fields |
| lg (`--space-4`) | 24px | Gap giữa các nhóm field trong form, margin switchAuth |
| xl (`--space-5`) | 32px | Padding formContainer trên mobile (max-width ≤ 480px) |
| 2xl (`--space-6`) | 48px | Padding formContainer desktop, page vertical padding |
| 3xl (`--space-7`) | 64px | Không dùng trực tiếp trong Phase 6 |

**Exceptions:** Touch target tối thiểu 44px cho Button `size="lg"` (height: 48px — đã đạt). Checkbox input 16×16px — ngoại lệ vì là inline control, không phải standalone touch target.

---

## Typography

Kế thừa từ `globals.css` — chỉ khai báo subset dùng trong Phase 6:

| Role | Token | Size | Weight | Line Height | Usage |
|------|-------|------|--------|-------------|-------|
| Heading | `--text-headline-md` | 28px (1.75rem) | 700 (`--weight-bold`) | 1.2 (`--leading-tight`) | Form title: "Đăng nhập", "Tạo tài khoản", "403" |
| Label | `--text-label-lg` | 14px (0.875rem) | 500 (`--weight-medium`) | 1.5 (`--leading-normal`) | Input field labels |
| Body | `--text-body-lg` | 16px (1rem) | 400 (`--weight-regular`) | 1.6 (`--leading-relaxed`) | Input value text, button text (size lg), subtitle |
| Caption | `--text-body-sm` | 12px (0.75rem) | 400 (`--weight-regular`) | 1.6 (`--leading-relaxed`) | Error messages dưới field, helper text, checkbox label |

**Quy tắc:** Không dùng quá 4 font sizes trên cùng một màn hình. Không dùng weight < 400 trong Phase 6.

---

## Color

Kế thừa từ `globals.css` — Material Design 3 tonal architecture "The Digital Atélier":

| Role | Token | Hex | Usage trong Phase 6 |
|------|-------|-----|---------------------|
| Dominant (60%) — surface | `--surface` / `--surface-container-lowest` | #f7f9fb / #ffffff | Page background (`--gradient-hero`), formContainer background (`#ffffff`) |
| Secondary (30%) — container | `--surface-container-low` / `--surface-container` | #f2f4f6 / #eceef0 | Input focus background, hover states |
| Accent (10%) — brand primary | `--primary` / `--primary-container` | #0040a1 / #0056d2 | Xem "Accent reserved for" bên dưới |
| Destructive | `--error` | #ba1a1a | Input border khi có lỗi, error message text |

**Accent reserved for** (chỉ những elements này, không dùng accent cho mọi thứ interactive):
- Button `variant="primary"` — gradient `#0040a1 → #0056d2` (CTA chính: Đăng nhập, Tạo tài khoản, Đăng ký)
- Input border-bottom khi focus (`--secondary` = `#a04100` — orange accent từ secondary palette)
- Link text: `forgotLink`, `switchLink` (`--primary` = `#0040a1`)
- Focus-visible outline: `--primary-container` (`#0056d2`)

**Semantic thứ hai:** Orange `--secondary` (#a04100) — dùng độc quyền cho input bottom-border khi focus. Không dùng cho text hay background.

---

## Copywriting Contract

### Login Page (`/login`)

| Element | Copy |
|---------|------|
| Page title (h1) | Đăng nhập |
| Subtitle | Chào mừng trở lại! Đăng nhập để tiếp tục mua sắm |
| Email field label | Email |
| Email placeholder | your@email.com |
| Password field label | Mật khẩu |
| Password placeholder | •••••••• |
| Checkbox label | Ghi nhớ đăng nhập |
| Forgot link | Quên mật khẩu? |
| Primary CTA | Đăng nhập |
| Switch to register | Chưa có tài khoản? **Đăng ký ngay** |
| Error — email trống | Vui lòng nhập email |
| Error — password trống | Vui lòng nhập mật khẩu |
| Error — sai credentials (401) | Email hoặc mật khẩu không chính xác. Vui lòng thử lại |
| Loading state CTA | (spinner thay text — dùng `loading` prop của Button) |

### Register Page (`/register`)

| Element | Copy |
|---------|------|
| Page title (h1) | Tạo tài khoản |
| Subtitle | Đăng ký để trải nghiệm mua sắm đẳng cấp |
| Username field label | Tên đăng nhập |
| Username placeholder | ten_dang_nhap |
| Email field label | Email |
| Email placeholder | your@email.com |
| Password field label | Mật khẩu |
| Password placeholder | Ít nhất 6 ký tự |
| Confirm password field label | Xác nhận mật khẩu |
| Confirm password placeholder | Nhập lại mật khẩu |
| Primary CTA | Tạo tài khoản |
| Switch to login | Đã có tài khoản? **Đăng nhập** |
| Error — username trống | Vui lòng nhập tên đăng nhập |
| Error — username quá ngắn | Tên đăng nhập ít nhất 3 ký tự |
| Error — email trống | Vui lòng nhập email |
| Error — email không hợp lệ | Email không hợp lệ |
| Error — password trống | Vui lòng nhập mật khẩu |
| Error — password quá ngắn | Mật khẩu ít nhất 6 ký tự |
| Error — confirm không khớp | Mật khẩu không khớp |
| Error — username đã tồn tại (409) | Tên đăng nhập này đã được sử dụng |
| Error — email đã tồn tại (409) | Email này đã được đăng ký. **Đăng nhập** |
| Banner (multiple errors) | Vui lòng kiểm tra {N} trường thông tin bên dưới (Banner component với `count` prop) |

### /403 Page

| Element | Copy |
|---------|------|
| Page title (h1) | Không có quyền truy cập |
| Body | Bạn không có quyền xem trang này. |
| Primary CTA | Về trang chủ |
| Secondary info | (Không cần thêm — minimal per D-09) |

### Interaction States

| State | Behavior |
|-------|----------|
| Loading (submit) | Button disabled + spinner thay text label, không block hành động khác trên page |
| Success (login) | Redirect về `returnTo` query param (default: `/`) — không show toast |
| Success (register) | Auto-login → redirect về `/` — không show toast |
| Network error | Banner `count={1}` + generic message "Có lỗi xảy ra, vui lòng thử lại" |
| 409 Conflict | Highlight field bị conflict bằng `error` prop của Input + copy cụ thể (xem trên) |
| 401 Invalid credentials | Banner `count={1}` + message ở level form, KHÔNG highlight field cụ thể |

---

## Component Inventory cho Phase 6

Tất cả reuse existing — không tạo component mới trừ `/403` page layout:

| Component | Source | Phase 6 Usage |
|-----------|--------|---------------|
| `Button` | `src/components/ui/Button/Button` | Primary CTA (login/register/403 home), Secondary (deferred — không cần thêm) |
| `Input` | `src/components/ui/Input/Input` | Tất cả form fields |
| `Banner` | `src/components/ui/Banner/Banner` | Multiple field errors, server errors |
| `page.module.css` | `src/app/login/page.module.css` | Register page REUSE cùng file (đã có pattern này) |

### Form Layout Spec (Login + Register)

```
.page
  padding: --space-6 --space-4  (48px 24px)
  background: --gradient-hero
  min-height: calc(100vh - 200px)
  display: flex align-items:center justify-content:center

.formContainer
  max-width: 440px
  background: --surface-container-lowest  (#ffffff)
  border-radius: --radius-2xl  (16px)
  padding: --space-6  (48px)
  box-shadow: --shadow-xl
  animation: fadeInUp 0.5s

.formHeader
  text-align: center
  margin-bottom: --space-5  (32px)

.form
  display: flex flex-direction:column
  gap: --space-4  (24px)
```

### /403 Page Layout Spec

```
Centered vertically + horizontally, full viewport height.
Max-width: 440px (đồng nhất với form pages).
Background: --surface (không dùng gradient — page này không phải auth flow).
h1: --text-headline-md (28px) weight-bold --on-surface
p: --text-body-lg (16px) weight-regular --on-surface-variant, margin-top: --space-3
Button primary size="lg": "Về trang chủ", margin-top: --space-5, fullWidth.
Không có header/footer navigation.
```

---

## Register Form Field Changes (Phase 6 specific)

**Bỏ:** `fullName` field (label "Họ và tên", placeholder "Nguyễn Văn A") — per D-01/Deferred

**Bỏ:** `phone` field (label "Số điện thoại", helperText "Không bắt buộc") — per D-01/Deferred

**Thêm mới:** `username` field — vị trí: FIRST field (trước email)
- Label: "Tên đăng nhập"
- Placeholder: "ten_dang_nhap"
- Type: text
- Icon: SVG user-circle (18×18, giống icon fullName hiện tại — tái dùng)
- Validation FE: required + minLength 3

**Giữ nguyên:** `email`, `password`, `confirmPassword` fields — không đổi label/placeholder/icon.

**Thứ tự fields sau update:**
1. Tên đăng nhập (username) — NEW
2. Email — giữ
3. Mật khẩu — giữ
4. Xác nhận mật khẩu — giữ

---

## Middleware + Route Changes (no new UI)

Các thay đổi này không tạo UI mới — ghi nhận để executor không bỏ sót:

- Middleware matcher thêm `/account/*` — không ảnh hưởng visual
- Cookie `user_role` set sau login/register — không visible
- `/403` page là UI duy nhất mới trong Phase 6 (ngoài form field changes)

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none — không dùng shadcn | not applicable |
| Third-party | none | not applicable |

Không có third-party registry. Không cần vetting gate.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
