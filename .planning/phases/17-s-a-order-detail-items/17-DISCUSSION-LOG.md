# Phase 17: Sửa Order Detail Items - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 17-s-a-order-detail-items
**Mode:** Auto (auto-selected recommended option per area, harness Auto Mode active)
**Areas discussed:** Source ảnh+brand, AdminOrder interface, Layout admin items, Shipping/payment render, Empty state, Image rendering

---

## Source ảnh + brand cho line item

| Option | Description | Selected |
|--------|-------------|----------|
| FE-side enrichment (Promise.allSettled getProductById) | KHÔNG sửa BE, fetch 1-5 product song song. Visible-first. | ✓ |
| BE join trong order-svc | Order-svc gọi product-svc lúc read, merge vào OrderItemDto. Tăng coupling. | |
| Snapshot image/brand vào OrderItemEntity (Flyway V6) | Đảm bảo không phụ thuộc product-svc khi render. Cần migration + sửa Phase 8 logic. | |

**User's choice (auto):** FE-side enrichment
**Notes:** Visible-first preference (PROJECT.md key decision) → tránh BE migration. Best-effort fallback nếu fetch fail.

---

## AdminOrder TypeScript interface

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse `Order` type từ @/types | Order đã có items/shippingAddress/paymentMethod. Xóa interface inline. | ✓ |
| Extend `AdminOrder` interface với extra fields | Giữ tách biệt admin vs user. Duplicate fields. | |

**User's choice (auto):** Reuse Order
**Notes:** Tránh type duplicate. `getAdminOrderById` đã `Promise<Order>`.

---

## Layout admin items table

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse layout user (table 4 cột + thumbnail) | Visual consistency, dễ support customer. | ✓ |
| Custom admin layout (table 6+ cột với productId, status per-line) | Power-user UX, nhiều info. Phá pattern. | |

**User's choice (auto):** Reuse user layout, inline style theo admin pattern
**Notes:** Admin page 100% inline-style → giữ pattern. KHÔNG tạo CSS module mới.

---

## Shipping address & payment method (admin card)

| Option | Description | Selected |
|--------|-------------|----------|
| Render thật từ order.shippingAddress + order.paymentMethod | Data đã có trong response. | ✓ |
| Giữ "—" placeholder | KHÔNG tốn effort. Không có giá trị. | |

**User's choice (auto):** Render thật
**Notes:** Data sẵn sàng từ Phase 8. Extract `paymentMethodMap` sang `@/lib/orderLabels.ts` để DRY.

---

## Empty items state

| Option | Description | Selected |
|--------|-------------|----------|
| Hiển thị "Đơn hàng không có sản phẩm" | Graceful empty. | ✓ |
| Hide section toàn bộ | Đỡ tốn space. Confusing. | |
| Render table header rỗng | Default fallback. Trông broken. | |

**User's choice (auto):** Show empty message
**Notes:** Edge case rare (legacy orders) nhưng cần handle.

---

## Image rendering

| Option | Description | Selected |
|--------|-------------|----------|
| `next/image` 64×64 + placeholder div nếu null | Pattern consistent với Phase 15 hero. | ✓ |
| `<img>` tag thường | Đơn giản, không optimize. | |
| 80×80 hoặc 96×96 | Lớn hơn, ăn nhiều space hàng. | |

**User's choice (auto):** next/image 64×64
**Notes:** 64px đủ nhận biết. Placeholder fallback dùng `var(--surface-container-high)`.

---

## Claude's Discretion

- Naming class CSS / inline style keys
- Order của items trong table (theo `id` BE return — KHÔNG re-sort)
- Spacing/gap chính xác giữa thumbnail và text
- Có hiển thị `productId` cho admin debugging hay không (lean: KHÔNG)

## Deferred Ideas

- Batch product fetch endpoint (defer v1.4+)
- Snapshot image/brand vào OrderItemEntity (defer)
- Click thumbnail → product detail page (nice-to-have)
- Admin AI suggest reply (đã thuộc AI-05 Phase 22)
- Coupon display row (đã thuộc COUP-05 Phase 20)
- Review CTA per item (nice-to-have)
