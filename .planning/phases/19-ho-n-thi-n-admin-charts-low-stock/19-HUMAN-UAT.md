---
phase: 19-ho-n-thi-n-admin-charts-low-stock
created: 2026-05-02
type: human-uat
status: pending
prerequisites:
  - Docker stack up (gateway + order-svc + user-svc + product-svc + DBs)
  - Frontend dev server `npm run dev` tại sources/frontend
  - Admin user đăng nhập (storageState từ e2e/global-setup.ts)
---

# Phase 19 — Human UAT (defer từ verifier do thiếu runtime)

Code-level verification đã PASS 5/5 SCs (xem `19-VERIFICATION.md`). Các items dưới đây cần **chạy stack thực** để verify UX/visual/end-to-end.

## UAT-1: Time-window dropdown drives 3 charts

**Steps:**
1. Mở `http://localhost:3000/admin` (đã login admin).
2. Verify thấy KPI row + dropdown thời gian + 2x2 charts grid + low-stock section.
3. Đổi dropdown lần lượt: `7 ngày` → `30 ngày` → `90 ngày` → `Tất cả`.

**Expected:**
- Revenue / Top-products / User-signups re-fetch + re-render khớp range mới.
- Order-status pie + Low-stock KHÔNG đổi (deps trống — D-06).
- Loading skeleton hiển thị ngắn trước khi data về.
- Tooltip / legend tiếng Việt; số format vi-VN (vd `1.234.567`); ngày format `DD/MM`.

## UAT-2: Status pie semantic colors (visual)

**Expected mỗi slice:**
- PENDING → vàng `#f59e0b`
- CONFIRMED → xanh dương `#3b82f6`
- SHIPPED → cyan `#06b6d4`
- DELIVERED → xanh lá `#10b981`
- CANCELLED → đỏ `#dc2626`

## UAT-3: Low-stock click navigate

**Steps:**
1. Trong LowStockSection, click 1 row (hoặc nút "Sửa").

**Expected:**
- URL chuyển sang `/admin/products?highlight={id}`.
- Product list page hiển thị + highlight đúng item (behavior từ phase trước).

## UAT-4: Empty states (nếu DB rỗng tương ứng)

**Expected:** Mỗi chart hiển thị Vietnamese empty string riêng (D-15):
- Revenue + Signups: wording chung kiểu "Chưa có dữ liệu trong khoảng thời gian này"
- Top-products: wording riêng
- Status pie: "Chưa có đơn hàng nào"
- Low-stock: wording riêng

## UAT-5: Cross-service auth forwarding (functional)

**Steps:** Mở DevTools Network, chuyển dropdown để trigger top-products fetch.

**Expected:**
- Request `/api/orders/admin/charts/top-products` có header `Authorization: Bearer ...`.
- Response 200 + data có `productName` (BE order-svc đã call sang product-svc batch và join).
- Nếu product-svc trả 401 → top-products card hiển thị error state (không crash 3 cards còn lại — Promise.allSettled, D-09).

## UAT-6: Playwright E2E specs

**Steps:**
```
cd sources/frontend
npx playwright test e2e/admin-charts.spec.ts e2e/admin-low-stock.spec.ts
```

**Expected:** Cả 2 specs PASS dùng admin storageState từ global-setup.

---

_Tạo bởi gsd-verifier do Maven/Playwright/Docker không khả dụng trên Windows env tại thời điểm verify._
