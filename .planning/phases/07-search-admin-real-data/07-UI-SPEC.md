---
phase: 7
slug: search-admin-real-data
status: draft
shadcn_initialized: false
preset: none
created: 2026-04-26
---

# Phase 7 — UI Design Contract: Search + Admin Real Data

> Visual và interaction contract cho Phase 7. Được tạo bởi gsd-ui-researcher, xác thực bởi gsd-ui-checker.
> Nguồn chính: globals.css (kế thừa đầy đủ từ Phase 6 UI-SPEC), 07-CONTEXT.md (D-01..D-10),
> các admin page files hiện tại (admin/products, admin/orders, admin/users, search).

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none — CSS Modules thuần (không dùng shadcn) |
| Preset | not applicable |
| Component library | Custom — Button, Input, Badge, Modal, Toast, RetrySection, Banner đã có tại `src/components/ui/` |
| Icon library | Inline SVG (stroke-based, 18×18 cho toolbar/action icons, 20×20 cho search input) |
| Font | Be Vietnam Pro (đã load qua `--font-be-vietnam-pro` Next.js font variable) |

**shadcn Gate:** `components.json` KHÔNG tồn tại. Stack là Next.js App Router + CSS Modules thuần — shadcn không phù hợp. Design system CSS custom properties đã khai báo đầy đủ trong `globals.css`. Ghi nhận: `Tool: none`.

**Kế thừa Phase 6:** Toàn bộ tokens (spacing, typography, color, radius, shadow, transition) kế thừa nguyên vẹn từ Phase 6 UI-SPEC và `globals.css`. Phase 7 KHÔNG override hoặc thêm token mới.

---

## Spacing Scale

Kế thừa từ `globals.css` — `--space-*` tokens (8px base). Không thay đổi so với Phase 6.

| Token | Value | Usage trong Phase 7 |
|-------|-------|---------------------|
| xs (`--space-1`) | 4px | Action button padding, badge padding (vertical) |
| sm (`--space-2`) | 8px | Gap giữa action buttons trong row, gap giữa toast items |
| md (`--space-3`) | 16px | Table cell padding (ngang + dọc), modal form gap, toolbar gap |
| lg (`--space-4`) | 24px | Modal padding, page section gap, toolbar justify-between |
| xl (`--space-5`) | 32px | Modal confirm padding, confirm description margin-bottom |
| 2xl (`--space-6`) | 48px | Search header padding, page vertical padding |
| 3xl (`--space-7`) | 64px | RetrySection wrapper padding |

**Exceptions:** Touch target tối thiểu 44px cho `Button size="lg"` (đã đạt từ Phase 6). Action icon buttons (✏️, 🗑️) giữ min 32×32px để dễ click trong table row — không cần full 44px vì context là desktop admin panel.

---

## Typography

Kế thừa từ `globals.css` — subset dùng trong Phase 7:

| Role | Token | Size | Weight | Line Height | Usage |
|------|-------|------|--------|-------------|-------|
| Page heading | `--text-headline-md` | 28px (1.75rem) | 700 (`--weight-bold`) | 1.2 (`--leading-tight`) | `h1` tiêu đề mỗi admin page: "Quản lý sản phẩm", "Quản lý đơn hàng", "Quản lý tài khoản", "Tìm kiếm" |
| Section heading | `--text-title-lg` | 22px (1.375rem) | 700 (`--weight-bold`) | 1.5 (`--leading-normal`) | Modal title (`h3`), order detail section headings (`h4`), page section titles — size difference (22px vs 14px) tạo hierarchy đủ mà không cần weight riêng |
| Table header | `--text-label-md` | 12px (0.75rem) | 700 (`--weight-bold`) | 1.5 (`--leading-normal`) | `<th>` — uppercase + `--tracking-wide` (0.02em) + 700 weight thay thế 500 để tạo contrast với body |
| Body / Cell | `--text-body-md` | 14px (0.875rem) | 400 (`--weight-regular`) | 1.6 (`--leading-relaxed`) | `<td>` data, modal form labels, order detail info text |
| Caption | `--text-body-sm` | 12px (0.75rem) | 400 (`--weight-regular`) | 1.6 (`--leading-relaxed`) | Count label ("3 sản phẩm"), secondary info trong product cell (brand) |

**Quy tắc:** Đúng 2 font weights — `400 (--weight-regular)` và `700 (--weight-bold)`. Nhất quán với Phase 6 UI-SPEC. Không dùng weight 500 hay 600. Giá tiền và highlight cells dùng `400` + color `--primary` để tạo visual emphasis thay vì thay đổi weight.

---

## Color

Kế thừa từ `globals.css` — Material Design 3 tonal architecture "The Digital Atélier":

| Role | Token | Hex | Usage trong Phase 7 |
|------|-------|-----|---------------------|
| Dominant (60%) — surface | `--surface-container-lowest` | #ffffff | Table wrapper background, modal background, confirm modal background |
| Secondary (30%) — container | `--surface-container-low` | #f2f4f6 | Table header row (`<thead>`), table row hover, search header background |
| Accent (10%) — brand primary | `--primary` | #0040a1 | Xem "Accent reserved for" bên dưới |
| Destructive | `--error` / `--error-container` | #ba1a1a / #ffdad6 | Delete button hover background, confirm delete icon, "Xóa" button (variant="danger"), low stock count text |
| Success (toast only) | #dcfce7 / #16a34a | n/a | Toast success icon background/color (đã có trong Toast.module.css — không thêm mới) |

**Accent reserved for** (chỉ những elements này):
- Button `variant="primary"` — gradient `#0040a1 → #0056d2` (CTA chính: "Thêm sản phẩm", "Lưu thay đổi", "Cập nhật trạng thái")
- Giá tiền trong bảng (`.price` — `color: var(--primary)`)
- Tổng cộng trong order detail (color: `var(--primary)`)
- Focus-visible outline: `--primary-container` (#0056d2)

**Status badge colors** (dùng BadgeVariant đã có — KHÔNG thêm variant mới):

| Status | BadgeVariant | Background token | Text token | Label tiếng Việt |
|--------|-------------|-----------------|-----------|-----------------|
| PENDING | `default` | `--surface-container-high` (#e6e8ea) | `--on-surface-variant` (#424654) | Chờ xác nhận |
| CONFIRMED | `new` | `--primary-fixed` (#dae2ff) | `--on-primary-fixed` (#001847) | Đã xác nhận |
| SHIPPING | `hot` | `--secondary` (#a04100) | `--on-secondary` (#ffffff) | Đang giao |
| DELIVERED | `sale` | `--secondary-container` (#fe6b00) | `--on-secondary` (#ffffff) | Đã giao |
| CANCELLED | `out-of-stock` | `--error-container` (#ffdad6) | `--on-error-container` (#93000a) | Đã hủy |

**Product status badge colors:**

| Status | BadgeVariant | Label tiếng Việt |
|--------|-------------|-----------------|
| ACTIVE | `new` | Đang bán |
| OUT_OF_STOCK | `out-of-stock` | Hết hàng |
| INACTIVE | `default` | Ẩn |

**User role badge colors:**

| Role | BadgeVariant | Label tiếng Việt |
|------|-------------|-----------------|
| ROLE_ADMIN | `hot` | Admin |
| ROLE_CUSTOMER | `default` | Khách hàng |

---

## Admin Table Pattern

Tất cả 3 admin pages (`/admin/products`, `/admin/orders`, `/admin/users`) dùng chung CSS từ `admin/products/page.module.css`. Phase 7 KHÔNG tạo CSS file mới cho orders và users — tiếp tục import `'../products/page.module.css'` (đã có trong code hiện tại).

**Table wrapper:** `.tableWrapper` — `background: var(--surface-container-lowest)`, `border-radius: var(--radius-xl)` (12px), `overflow: hidden`.

**Table header row:** `background: var(--surface-container-low)`, cell padding `var(--space-3) var(--space-4)` (16px 24px), font `--text-label-md` uppercase + tracking-wide.

**Table data row:** cell padding `var(--space-3) var(--space-4)`, font `--text-body-md`, `border-bottom: 1px solid rgba(195,198,214,0.08)`, hover `background: var(--surface-container-low)`.

### Column Spec: Admin Products Table

| # | Header | Width hint | Content spec |
|---|--------|-----------|-------------|
| 1 | Sản phẩm | ~35% | Thumbnail 48×48px (border-radius `--radius-md`) + product name (`400 --weight-regular`) + brand caption (`--text-body-sm`, `--outline`) |
| 2 | Danh mục | ~15% | Category name string |
| 3 | Giá | ~12% | `formatPrice(price)` — color `--primary`, weight `400` (màu accent đủ tạo emphasis) |
| 4 | Tồn kho | ~10% | Stock number; nếu `stock < 10` → color `--error`, weight `700 --weight-bold` |
| 5 | Trạng thái | ~13% | `<Badge>` theo product status (xem bảng badge colors trên) |
| 6 | Thao tác | ~15% | Icon button ✏️ (edit, `aria-label="Chỉnh sửa sản phẩm"`) + 🗑️ (delete, `aria-label="Xóa sản phẩm"`) — flex gap `--space-2` |

### Column Spec: Admin Orders Table

| # | Header | Width hint | Content spec |
|---|--------|-----------|-------------|
| 1 | Mã đơn | ~12% | `orderCode` — weight `700 --weight-bold` (class `.tdBold`) |
| 2 | Khách hàng | ~18% | `username` từ UserDto (field `username`) |
| 3 | Số sản phẩm | ~12% | `{items.length} sản phẩm` — nếu backend trả `itemCount` dùng trực tiếp |
| 4 | Tổng tiền | ~13% | `formatPrice(totalAmount)` — color `--primary`, weight `400` (màu accent đủ tạo emphasis) |
| 5 | Trạng thái | ~15% | `<Badge>` theo order status (xem bảng badge colors trên) |
| 6 | Ngày đặt | ~15% | `new Date(createdAt).toLocaleDateString('vi-VN')` — color `--on-surface-variant` (class `.tdMuted`) |
| 7 | Thao tác | ~15% | Icon button 📋 (xem chi tiết, `aria-label="Xem chi tiết đơn hàng"`) → `router.push('/admin/orders/${order.id}')` |

### Column Spec: Admin Users Table

| # | Header | Width hint | Content spec |
|---|--------|-----------|-------------|
| 1 | Họ tên | ~20% | `fullName` nếu có và không rỗng, fallback: `username` — weight `700 --weight-bold` |
| 2 | Email | ~25% | `email` string |
| 3 | Điện thoại | ~15% | `phone` nếu có, fallback: `—` |
| 4 | Vai trò | ~12% | `<Badge>` theo role (xem bảng badge colors trên) — role đọc từ field `roles` string |
| 5 | Ngày tạo | ~13% | `new Date(createdAt).toLocaleDateString('vi-VN')` — color `--on-surface-variant` |
| 6 | Thao tác | ~15% | ✏️ (edit modal) + 🗑️ (xóa — ẩn nếu `roles === 'ROLE_ADMIN'`) |

---

## Product Modal (Add / Edit)

**Component:** `ProductUpsertModal` — 1 component, 2 mode nhận qua prop `mode: 'add' | 'edit'` và `initialData?: AdminProductDto`.

**Kích thước:** max-width 560px, max-height 90vh, overflow-y scroll. Dùng `.modal` class từ `page.module.css`.

**Title:**
- Mode add: `Thêm sản phẩm mới`
- Mode edit: `Chỉnh sửa sản phẩm`

**Field list và layout:**

| Field | Label | Type | Required | Row layout | Note |
|-------|-------|------|----------|-----------|------|
| name | Tên sản phẩm | Input text | Bắt buộc | Full width | `@NotBlank` backend |
| price | Giá bán | Input number | Bắt buộc | Left half | `@NotNull` backend, placeholder "0" |
| originalPrice | Giá gốc | Input number | Tùy chọn | Right half | nullable per D-03 |
| shortDescription | Mô tả ngắn | Input text | Tùy chọn | Full width | nullable per D-03 |
| categoryId | Danh mục | `<select>` dropdown | Bắt buộc | Left half | Load từ GET /categories khi modal open; option value = UUID |
| brand | Thương hiệu | Input text | Tùy chọn | Right half | nullable per D-03 |
| stock | Tồn kho | Input number | Bắt buộc | Left half | placeholder "0" |
| thumbnailUrl | URL hình ảnh | Input text | Tùy chọn | Right half | nullable per D-03, placeholder "https://..." |

**Thứ tự fields trong form:**
1. Tên sản phẩm (full width)
2. Giá bán + Giá gốc (grid 2 cột)
3. Mô tả ngắn (full width)
4. Danh mục + Thương hiệu (grid 2 cột)
5. Tồn kho + URL hình ảnh (grid 2 cột)

**Category select style:** Inline style — `width: 100%`, `padding: var(--space-3)`, `border-radius: var(--radius-lg)`, `border: 1.5px solid rgba(195,198,214,0.2)`, `font-family: var(--font-family-body)`, `background: var(--surface-container-lowest)`. Không tạo CSS class mới — dùng inline style nhất quán với pattern trong admin/orders/page.tsx hiện tại.

**Loading state khi load categories:** Option đầu "Đang tải danh mục..." disabled. Nếu load thất bại: option đầu "Không thể tải danh mục — thử lại" disabled + nút refresh nhỏ bên cạnh select.

**Form actions (modal footer):**
- Secondary button: "Hủy" → đóng modal
- Primary button: mode add → "Thêm sản phẩm", mode edit → "Lưu thay đổi"
- Khi submit: button disabled + loading prop = true

**Validation FE trước khi submit:**
- `name`: required — hiện lỗi ngay dưới field: "Vui lòng nhập tên sản phẩm"
- `price`: required + > 0 — "Vui lòng nhập giá bán"
- `categoryId`: required — "Vui lòng chọn danh mục"
- `stock`: required + >= 0 — "Vui lòng nhập số lượng tồn kho"
- Các field optional: không validate, gửi `null` nếu trống

---

## Orders Detail Page (`/admin/orders/[id]`)

**Route:** `src/app/admin/orders/[id]/page.tsx` (tạo mới)

**Navigation:** Từ list page, click icon 📋 trên row → `router.push('/admin/orders/${order.id}')`.

**Back navigation:** Button "← Quay lại" ở đầu trang → `router.back()`.

**Page layout (flex column, gap `--space-4`):**

```
.page
  display: flex, flex-direction: column, gap: --space-4

[Header row]
  ← Quay lại danh sách đơn hàng     (link/button, --text-body-md, --on-surface-variant)
  h1: Chi tiết đơn hàng {orderCode}  (--text-headline-md, --weight-bold)

[2-column info grid, gap --space-4]
  [Left card: Thông tin đơn hàng]
    background: --surface-container-lowest, border-radius: --radius-xl, padding: --space-5
    - Mã đơn: {orderCode}
    - Khách hàng: {username}
    - Ngày đặt: {createdAt formatted vi-VN}
    - Trạng thái: <Badge variant>

  [Right card: Thông tin giao hàng]
    background: --surface-container-lowest, border-radius: --radius-xl, padding: --space-5
    - Địa chỉ: {shippingAddress.street}, {shippingAddress.district}, {shippingAddress.city}
      (nếu backend chưa có shippingAddress → hiển "--" placeholder)
    - Thanh toán: {paymentMethod} — {paymentStatus}
      (nếu backend chưa có → hiển "--" placeholder)
    - Ghi chú: {note} hoặc không hiển thị dòng này nếu null

[Line Items card — full width]
  background: --surface-container-lowest, border-radius: --radius-xl, padding: --space-5
  h3: Sản phẩm (--text-title-lg, --weight-bold)
  [Item list — nếu backend Phase 7 chưa trả items array: hiển "Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện"]
  [Divider: border-top rgba(195,198,214,0.15)]
  [Total row]
    Tổng cộng: {formatPrice(totalAmount)} — --weight-bold, --text-title-sm, color --primary

[Status Update card — full width]
  background: --surface-container-lowest, border-radius: --radius-xl, padding: --space-5
  h3: Cập nhật trạng thái (--text-title-lg, --weight-bold)
  <select> dropdown (5 options — xem Order Status Options)
  Button primary: "Cập nhật trạng thái" — disabled nếu status không thay đổi
```

**Order Status Options (select):**

| Value | Label |
|-------|-------|
| PENDING | Chờ xác nhận |
| CONFIRMED | Đã xác nhận |
| SHIPPING | Đang giao |
| DELIVERED | Đã giao |
| CANCELLED | Đã hủy |

**Loading state:** Skeleton — 2 skeleton blocks (height 120px) cho info cards + 1 skeleton block (height 200px) cho line items. Class `.skeleton` từ globals.css.

**Error state:** `<RetrySection onRetry={load} loading={loading} />` — full page centered.

---

## User Edit Modal

**Component:** `UserEditModal` — tạo mới inline trong `admin/users/page.tsx` (không tách file riêng — nhất quán với pattern hiện tại).

**Trigger:** Click ✏️ trên user row → `setEditTarget(user)` → modal hiện.

**Title:** `Chỉnh sửa tài khoản`

**Field list:**

| Field | Label | Type | Required | Note |
|-------|-------|------|----------|------|
| fullName | Họ và tên | Input text | Tùy chọn | Pre-fill từ `user.fullName` (có thể null → input trống) |
| phone | Số điện thoại | Input text | Tùy chọn | Pre-fill từ `user.phone` (có thể null → input trống) |
| roles | Vai trò | `<select>` | Bắt buộc | Pre-fill từ `user.roles`; options: "ROLE_CUSTOMER" / "ROLE_ADMIN" |

**Role select options:**

| Value | Label hiển thị |
|-------|---------------|
| ROLE_CUSTOMER | Khách hàng |
| ROLE_ADMIN | Quản trị viên |

**Form actions:**
- Secondary: "Hủy" → đóng modal
- Primary: "Lưu thay đổi" → gọi `PATCH /api/users/admin/users/{id}` với `AdminUserPatchRequest`

**Validation:** Không validate fullName/phone (cả hai optional). Roles select required (luôn có giá trị mặc định từ pre-fill → không cần validate).

---

## Toast / Notification Pattern

**Existing component:** `ToastProvider` + `useToast()` đã có tại `src/components/ui/Toast/Toast.tsx`. Admin pages dùng `useToast()` hook thay thế toàn bộ `alert()` calls hiện tại.

**Prerequisite:** `ToastProvider` phải wrap layout admin (`src/app/admin/layout.tsx`) hoặc root layout. Phase 7 executor kiểm tra ToastProvider đã được add vào layout chưa.

**Toast display spec:**
- Position: fixed, top 80px, right `--space-4` (24px)
- Auto-dismiss: 3500ms (đã có trong implementation)
- Animation: slideIn từ phải (40px), 350ms ease (đã có)
- Max-width: 380px, min-width: 280px

**Toast messages cho mỗi action:**

| Action | Type | Message |
|--------|------|---------|
| Tạo sản phẩm thành công | success | Sản phẩm đã được thêm thành công |
| Cập nhật sản phẩm thành công | success | Sản phẩm đã được cập nhật |
| Xóa sản phẩm thành công | success | Sản phẩm đã được xóa |
| Tạo sản phẩm thất bại | error | Không thể thêm sản phẩm. Vui lòng thử lại |
| Cập nhật sản phẩm thất bại | error | Không thể cập nhật sản phẩm. Vui lòng thử lại |
| Xóa sản phẩm thất bại | error | Không thể xóa sản phẩm. Vui lòng thử lại |
| Cập nhật trạng thái đơn hàng thành công | success | Trạng thái đơn hàng đã được cập nhật |
| Cập nhật trạng thái đơn hàng thất bại | error | Không thể cập nhật trạng thái. Vui lòng thử lại |
| Lưu thay đổi user thành công | success | Thông tin tài khoản đã được cập nhật |
| Lưu thay đổi user thất bại | error | Không thể cập nhật tài khoản. Vui lòng thử lại |
| Xóa user thành công | success | Tài khoản đã được xóa |
| Xóa user thất bại | error | Không thể xóa tài khoản. Vui lòng thử lại |

---

## Loading / Error States

### Pattern: Admin List Pages (products / orders / users)

**Loading state (initial fetch):** Skeleton rows — 5 hàng skeleton, mỗi hàng `height: 60px`, class `.skeleton` từ globals.css, `border-radius: var(--radius-md)`. Không dùng spinner cho list load.

**Error state (fetch thất bại):** `<RetrySection onRetry={load} loading={loading} />` — centered trong `.tableWrapper`. Default copy từ RetrySection: heading "Không tải được dữ liệu", body "Đã xảy ra lỗi khi tải. Vui lòng thử lại."

**Empty state (zero results sau khi fetch thành công):**
- Products: heading "Chưa có sản phẩm nào", body "Thêm sản phẩm đầu tiên bằng nút "+ Thêm sản phẩm" ở trên"
- Orders: heading "Chưa có đơn hàng nào", body "Khi có đơn hàng, chúng sẽ hiển thị tại đây"
- Users: heading "Chưa có tài khoản nào", body "Khi có tài khoản đăng ký, chúng sẽ hiển thị tại đây"

Layout empty state:
```
flex column, align-items center, gap --space-3, padding --space-7 (64px vertical)
SVG icon 48×48, stroke --outline-variant, strokeWidth 1
h3: --text-title-lg, color --on-surface
p: --text-body-md, color --on-surface-variant
```

### Pattern: Admin Orders Detail Page

**Loading state:** Skeleton blocks (xem "Orders Detail Page" section)

**Error state:** `<RetrySection>` toàn trang

### Pattern: Search Page

Search page đã có đầy đủ pattern (skeleton grid 8 items × height 360px, RetrySection, empty state). Phase 7 chỉ cần fix backend keyword filter — không thay đổi UI.

---

## Copywriting Contract

### Admin Products (`/admin/products`)

| Element | Copy |
|---------|------|
| Page h1 | Quản lý sản phẩm |
| Add button | + Thêm sản phẩm |
| Search placeholder | Tìm sản phẩm... |
| Count label | {N} sản phẩm |
| Modal add title | Thêm sản phẩm mới |
| Modal edit title | Chỉnh sửa sản phẩm |
| Modal primary CTA (add) | Thêm sản phẩm |
| Modal primary CTA (edit) | Lưu thay đổi |
| Modal secondary | Hủy |
| Delete confirm title | Xác nhận xóa |
| Delete confirm body | Bạn có chắc chắn muốn xóa sản phẩm này? Hành động này không thể hoàn tác. |
| Delete confirm primary | Xóa sản phẩm |
| Delete confirm secondary | Hủy |
| Empty state heading | Chưa có sản phẩm nào |
| Empty state body | Thêm sản phẩm đầu tiên bằng nút "+ Thêm sản phẩm" ở trên |
| Category loading option | Đang tải danh mục... |
| Category error option | Không thể tải danh mục — thử lại |

### Admin Orders (`/admin/orders`)

| Element | Copy |
|---------|------|
| Page h1 | Quản lý đơn hàng |
| Count label | {N} đơn hàng |
| Empty state heading | Chưa có đơn hàng nào |
| Empty state body | Khi có đơn hàng, chúng sẽ hiển thị tại đây |

### Admin Orders Detail (`/admin/orders/[id]`)

| Element | Copy |
|---------|------|
| Back link | ← Quay lại danh sách đơn hàng |
| Page h1 | Chi tiết đơn hàng {orderCode} |
| Info card title | Thông tin đơn hàng |
| Shipping card title | Thông tin giao hàng |
| Items card title | Sản phẩm |
| Items unavailable | Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện |
| Total label | Tổng cộng |
| Status card title | Cập nhật trạng thái |
| Status update CTA | Cập nhật trạng thái |
| Info labels | Mã đơn: / Khách hàng: / Ngày đặt: / Trạng thái: / Địa chỉ: / Thanh toán: / Ghi chú: |
| Empty field fallback | — |

### Admin Users (`/admin/users`)

| Element | Copy |
|---------|------|
| Page h1 | Quản lý tài khoản |
| Count label | {N} tài khoản |
| Edit modal title | Chỉnh sửa tài khoản |
| Edit modal primary CTA | Lưu thay đổi |
| Edit modal secondary | Hủy |
| Delete confirm title | Xác nhận xóa tài khoản |
| Delete confirm body | Bạn có chắc chắn muốn xóa tài khoản này? Hành động này không thể hoàn tác. |
| Delete confirm primary | Xóa tài khoản |
| Delete confirm secondary | Hủy |
| Empty state heading | Chưa có tài khoản nào |
| Empty state body | Khi có tài khoản đăng ký, chúng sẽ hiển thị tại đây |
| fullName placeholder | Nguyễn Văn A |
| phone placeholder | 0901 234 567 |

### Search Page (`/search`)

Copy đã lock trong code hiện tại — Phase 7 không thay đổi. Ghi nhận để executor xác nhận:

| State | Copy (đã có, giữ nguyên) |
|-------|--------------------------|
| Initial empty (no query) | h3: "Nhập từ khóa để tìm kiếm" / p: "Tìm sản phẩm theo tên, thương hiệu hoặc danh mục" |
| Zero results | h3: `Không tìm thấy kết quả cho "{query}"` / p: "Thử sử dụng từ khóa khác hoặc kiểm tra lại chính tả" |
| Has results | `Tìm thấy {N} sản phẩm cho "{query}"` |
| Error | RetrySection default — "Không tải được dữ liệu" / "Đã xảy ra lỗi khi tải. Vui lòng thử lại." |
| Search input placeholder | Nhập tên sản phẩm, thương hiệu, danh mục... |

### Destructive Actions Summary

| Action | Trigger | Confirmation approach |
|--------|---------|----------------------|
| Xóa sản phẩm | Click 🗑️ trong products table | `.confirmModal` overlay — title + body + 2 buttons (Hủy / Xóa sản phẩm) |
| Xóa tài khoản | Click 🗑️ trong users table (chỉ CUSTOMER) | `.confirmModal` overlay — title + body + 2 buttons (Hủy / Xóa tài khoản) |

Không có confirmation cho: status update orders (thao tác reversible), edit product/user (thao tác reversible).

### RetrySection Copy (dùng default — không override)

| Element | Copy |
|---------|------|
| heading | Không tải được dữ liệu |
| body | Đã xảy ra lỗi khi tải. Vui lòng thử lại. |
| button | Thử lại |

---

## Component Inventory cho Phase 7

Tất cả reuse existing — chỉ tạo mới `ProductUpsertModal` (inline trong products/page.tsx) và orders detail page:

| Component | Source | Phase 7 Usage |
|-----------|--------|---------------|
| `Button` | `src/components/ui/Button/Button` | Modal CTAs, delete confirm, status update, back navigation |
| `Input` | `src/components/ui/Input/Input` | Product form fields, user form fields, search toolbar |
| `Badge` | `src/components/ui/Badge/Badge` | Order status, product status, user role |
| `RetrySection` | `src/components/ui/RetrySection/RetrySection` | Admin list pages error state, orders detail error |
| `Modal` | `src/components/ui/Modal/Modal` | Có thể dùng cho ProductUpsertModal và UserEditModal (thay pattern `.modal` inline) — planner chọn |
| `ToastProvider` / `useToast` | `src/components/ui/Toast/Toast` | Tất cả CRUD feedback, thay thế `alert()` |
| `page.module.css` | `src/app/admin/products/page.module.css` | Orders + Users page import chung (đã có pattern này) |

**Mới tạo trong Phase 7:**
- `src/app/admin/orders/[id]/page.tsx` — Orders detail page (không tạo CSS file mới — dùng inline styles hoặc import chung)
- `ProductUpsertModal` — inline trong `admin/products/page.tsx` hoặc tách `ProductUpsertModal.tsx` riêng (planner quyết)
- `UserEditModal` — inline trong `admin/users/page.tsx`

---

## Admin Page Layout Spec (Shared)

```
.page
  display: flex
  flex-direction: column
  gap: --space-4 (24px)
  (Không có padding — layout outer của admin section xử lý)

.header
  display: flex
  justify-content: space-between
  align-items: center

.title (h1)
  font-family: --font-family-headline
  font-size: --text-headline-md (28px)
  font-weight: --weight-bold

.toolbar
  display: flex
  justify-content: space-between
  align-items: center
  gap: --space-3 (16px)
```

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
