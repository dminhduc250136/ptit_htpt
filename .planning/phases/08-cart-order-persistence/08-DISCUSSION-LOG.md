# Phase 8: Cart → Order Persistence Visible - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 08-cart-order-persistence
**Areas discussed:** Stock check flow, Order items storage, Order confirmation flow, Stock decrement logic, UI completion

---

## Stock Check Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Chỉ lúc POST /api/orders | Backend validate stock khi tạo order; FE không check trước | |
| FE check khi add-to-cart + backend validate | FE gọi GET /api/products/{id} khi add-to-cart; backend vẫn validate khi POST /api/orders | ✓ |

**User's choice:** FE check + backend validate (defense in depth)

| Option | Description | Selected |
|--------|-------------|----------|
| Disable nút + badge 'Hết hàng' | Nút bị disable, hiển thị badge khi stock=0 | ✓ |
| Toast error khi click | Button vẫn hiện nhưng toast error khi click | |

**User's choice:** Disable nút + badge "Hết hàng"

---

## Order Items Storage

| Option | Description | Selected |
|--------|-------------|----------|
| @OneToMany separate table | Bảng mới order_svc.order_items, JPA @OneToMany | ✓ |
| JSON column trong orders table | Thêm items JSONB column, không cần bảng mới | |

**User's choice:** @OneToMany separate table

| Option | Description | Selected |
|--------|-------------|----------|
| JSON column (JSONB) | shipping_address JSONB trong bảng orders | ✓ |
| @Embedded (các cột riêng) | shipping_full_name, shipping_street, v.v. | |

**User's choice:** JSON column cho shippingAddress

---

## Order Confirmation Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Redirect sang /account/orders/{id} | Redirect thẳng sau checkout success, bỏ modal | ✓ |
| Giữ modal + thêm link xem đơn hàng | Modal hiện tại + nút "Xem đơn hàng" | |

**User's choice:** Redirect sang /account/orders/{id}

---

## Stock Decrement Logic

| Option | Description | Selected |
|--------|-------------|----------|
| Có — trừ stock ngay khi tạo order | POST /api/orders → deduct stock via product-service | ✓ |
| Không trừ — chỉ validate + hiển thị (MVP) | Stock display only, không tự động trừ | |

**User's choice:** Trừ stock ngay khi tạo order

| Option | Description | Selected |
|--------|-------------|----------|
| Order-service gọi product-service qua gateway | PATCH /api/products/admin/{id} qua gateway | ✓ |
| Endpoint /stock/deduct trong product-service | Internal service call bypass gateway | |

**User's choice:** Qua gateway (existing pattern)

---

## UI Completion

| Item | Status |
|------|--------|
| Product detail — fix add-to-cart + +/- quantity disabled | Cần fix trong Phase 8 |
| Header — user dropdown popup | ✅ Đã fix trước Phase 8 |
| Admin — user role access fix (non-ADMIN vào /admin/*) | ✅ Đã fix trước Phase 8 |
| Admin orders — PATCH state 400 bug | ✅ Đã fix trước Phase 8 |

**Product detail UI:** Fix add-to-cart disabled + +/- quantity disabled; hiển thị stock thật; stock=0 → disable + badge "Hết hàng"; quantity selector max = product.stock.

---

## Claude's Discretion

- Stock display text khi stock > 0
- JPA fetch type cho @OneToMany items (LAZY)
- Jackson/JPA mapping cho shippingAddress JSONB
- ProductUpsertRequest stock field cho admin update

## Deferred Ideas

- Inventory-service sync / stock reservation chain (v1.2)
- Race condition handling khi 2 users mua cùng lúc (v1.2)
- Order cancellation → stock restore (v1.2)
- Account profile page `/profile` (phase sau)
