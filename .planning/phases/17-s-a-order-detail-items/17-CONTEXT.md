# Phase 17: Sửa Order Detail Items - Context

**Gathered:** 2026-05-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Bug fix độc lập cho 2 trang hiển thị chi tiết đơn hàng:

1. **User**: `/profile/orders/[id]` đã render `order.items` nhưng thiếu **ảnh sản phẩm + brand** (success criteria 1 yêu cầu image / name / brand / price / qty / subtotal).
2. **Admin**: `/admin/orders/[id]` hardcode placeholder `"Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện"` (line 145) — cần xóa, render full line items + sửa interface `AdminOrder` (hiện thiếu `items`, `shippingAddress`, `paymentMethod`).

Backend `OrderDto` đã có `items: List<OrderItemDto>` + `shippingAddress` (JSONB) + `paymentMethod` (Phase 8). Backend KHÔNG cần migration. Vấn đề thuần FE + enrich `image`/`brand` từ product-service.

**Out of scope**: thêm coupon display (đã được COUP-05 cover ở Phase 20), wishlist link, related products, item review CTA.

</domain>

<decisions>
## Implementation Decisions

### D-01 — Source ảnh + brand cho line item: FE-side enrichment
- Sau khi load order, gọi `Promise.allSettled` với `getProductById(productId)` cho mỗi unique productId trong `order.items`.
- Merge `thumbnailUrl` + `brand` vào view-model (KHÔNG mutate types). Cache trong `useMemo` theo `order.id`.
- **Lý do**: Visible-first — KHÔNG migrate DB, KHÔNG sửa BE DTO/service. Order-svc đã có `productClient` nhưng chỉ dùng cho stock validate; mở rộng BE join sẽ tăng coupling + kéo dài phase.
- **Fallback**: nếu fetch product fail (404/timeout) → render placeholder image + brand "—" (best-effort, không block render).

### D-02 — `AdminOrder` TypeScript interface: dùng lại `Order` type
- `AdminOrder` interface inline trong `admin/orders/[id]/page.tsx` (lines 13-22) thiếu `items`, `shippingAddress`, `paymentMethod` → thay bằng import `Order` từ `@/types`.
- `getAdminOrderById` đã `Promise<Order>` rồi (services/orders.ts:77) — chỉ cần xóa interface inline + bỏ `as any` cast (line 58).
- **Lý do**: ROADMAP success criteria 3 yêu cầu "AdminOrder interface có trường items: OrderItem[]". `Order` type đã đủ shape. Tránh tạo type duplicate.

### D-03 — Layout admin items: reuse layout user
- Admin items table dùng cùng cấu trúc với `/profile/orders/[id]` (table 4 cột: Sản phẩm / Số lượng / Đơn giá / Thành tiền) + thêm cột thumbnail bên trái.
- KHÔNG tách riêng style file mới — inline style theo pattern hiện tại của `admin/orders/[id]/page.tsx` (consistency với 4 cards khác trên cùng trang).
- **Lý do**: trang admin đã 100% inline-style; tách CSS module sẽ phá pattern. Visual consistency giữa user và admin tăng confidence cho admin khi support customer.

### D-04 — Admin shipping/payment cards: render từ `order.shippingAddress` + `order.paymentMethod`
- Card "Thông tin giao hàng" (lines 134-139) hiện hardcode `"—"` — render `order.shippingAddress` (street/ward/district/city join `, `) và `order.paymentMethod` (qua paymentMethodMap như user side).
- Tái dùng `paymentMethodMap` constant từ `profile/orders/[id]/page.tsx` (DRY: extract sang `@/lib/orderLabels.ts`).
- **Lý do**: data đã có sẵn trong response, render miễn phí. Admin cần xem địa chỉ + payment khi xử lý đơn — chứ không phải dấu "—".

### D-05 — Empty items state
- Nếu `order.items` rỗng/null: render `<p>Đơn hàng không có sản phẩm</p>` thay vì table rỗng.
- **Lý do**: edge case hiếm (order legacy trước Phase 8) nhưng cần graceful, không show table header trống.

### D-06 — Image rendering: `next/image` với placeholder
- Dùng `<Image src={thumbnailUrl} width={64} height={64} alt={productName} />` (Next 15 component).
- Nếu `thumbnailUrl` null → render `<div>` 64×64 với background `var(--surface-container-high)` + icon placeholder.
- **Lý do**: consistent với pattern catalog page (Phase 15 PUB-01 dùng `next/image priority`). Avatar 64px đủ nhận biết, không nặng layout.

### Claude's Discretion
- Naming class CSS / inline style keys
- Order của items trong table (theo `id` BE return — KHÔNG re-sort)
- Spacing/gap chính xác giữa thumbnail và text trong table cell
- Có hiển thị `productId` cho admin debugging hay không (lean: KHÔNG, dùng productName là đủ)

### Folded Todos
*Không có todo nào được folded vào phase này.*

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend (đã sẵn sàng — không cần sửa)
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java` — Wire format response, đã có `items + shippingAddress + paymentMethod` (Phase 8 Plan 02 ghi chú).
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemDto.java` — Record với `id, productId, productName, quantity, unitPrice, lineTotal`. KHÔNG có `image/brand` → enrich phía FE.
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/AdminOrderController.java` — Endpoint `GET /admin/orders/{id}` trả `ApiResponse<OrderDto>` qua `orderCrudService.getOrder(id, true)` (includeDeleted=true cho admin).

### Frontend — file cần sửa
- `sources/frontend/src/app/admin/orders/[id]/page.tsx` — Trang admin: xóa interface `AdminOrder` inline (lines 13-22), xóa placeholder line 145, render shipping/payment thật, thêm items table.
- `sources/frontend/src/app/profile/orders/[id]/page.tsx` — Trang user: extend table render image + brand cho mỗi item.

### Frontend — pattern reuse
- `sources/frontend/src/services/orders.ts` — `getOrderById` (line 62), `getAdminOrderById` (line 77) — đã `Promise<Order>`, KHÔNG cần đổi signature.
- `sources/frontend/src/services/products.ts` — `getProductById` (line 55) — dùng để enrich image/brand.
- `sources/frontend/src/services/http.ts` (lines 106-108) — auto-unwrap `ApiResponse` envelope (`.data`) — FE KHÔNG cần handle envelope thủ công.
- `sources/frontend/src/types/index.ts` — `Order` type (đã có `items?: OrderItem[]`, `shippingAddress?`, `paymentMethod`), `Product` type (`thumbnailUrl`, `brand?`).

### Project context
- `.planning/PROJECT.md` — Visible-first priority (line 83 Key Decisions): defer backend hardening invisible.
- `.planning/REQUIREMENTS.md` §ORDER-01 (line 74), §ADMIN-06 (line 56) — REQ scope.
- `.planning/ROADMAP.md` lines 75-86 — Phase 17 success criteria 3 items.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`getProductById(id): Promise<Product>`** — services/products.ts:55. Trả về Product với `thumbnailUrl`, `brand`. Dùng `Promise.allSettled` để fetch parallel cho N items.
- **`Order` + `OrderItem` types** — types/index.ts. `Order.items?: OrderItem[]`, `Order.shippingAddress?`, `Order.paymentMethod`. KHÔNG cần tạo `AdminOrder` riêng.
- **`paymentMethodMap`, `paymentStatusMap`** — profile/orders/[id]/page.tsx:21-32. Extract sang `@/lib/orderLabels.ts` để reuse cho admin.
- **`statusMap`** — đã có ở admin (STATUS_LABELS) và user (statusMap) — opportunity để DRY (optional, không bắt buộc).
- **`<Image>` từ `next/image`** — Phase 15 PUB-01 đã dùng cho hero. Pattern: `<Image src={url} width={W} height={H} alt={...} />`.
- **`RetrySection`, `Button`, `Badge`** — admin page đã import sẵn (lines 6-9 admin) — đủ cho UI states.

### Established Patterns
- **httpGet auto-unwrap envelope** — services/http.ts:108. `Promise<T>` resolve với `.data`, KHÔNG resolve với toàn bộ `{timestamp, status, message, data}`. Test file admin (line 58) hiện cast `as any` → thừa thãi sau khi fix interface.
- **Admin pages dùng inline style** — admin/orders/[id]/page.tsx 100% inline (cardStyle, labelStyle objects). Phase 17 phải theo pattern này, KHÔNG tạo CSS module mới.
- **User pages dùng CSS module** — profile/orders/[id]/page.tsx dùng `styles.itemsTable` từ `page.module.css`. Phase 17 thêm cột image vào table này.
- **Skeleton loading** — cả 2 trang đã có pattern skeleton 3-card hoặc skeleton-row. Phase 17 KHÔNG cần đổi loading.

### Integration Points
- Admin page → `getAdminOrderById(id)` → `httpGet<Order>` → render.
- Sau khi setOrder thành công → fire `enrichItems(order.items)` (parallel `getProductById`) → setEnriched state.
- KHÔNG cần thêm route, middleware, navigation. Đã exist.

### Performance Notes
- Order trung bình 1-5 items → 1-5 parallel HTTP calls cho enrichment. Acceptable cho MVP.
- Có thể optimize sau bằng `GET /api/products?ids=...` (batch endpoint) — defer cho v1.4 nếu có evidence chậm.

</code_context>

<specifics>
## Specific Ideas

- Format thumbnail 64×64 (đủ nhận biết, không quá nặng).
- Admin shipping address render 1 dòng (street, ward, district, city) — KHÔNG nhiều dòng dạng letter.
- Brand hiển thị nhỏ phía dưới productName (subtitle), text-secondary color.
- KHÔNG show productId UUID cho user; admin có thể show 8 ký tự đầu nếu cần (lean: skip).

</specifics>

<deferred>
## Deferred Ideas

- **Batch product fetch endpoint** `GET /api/products?ids=...` — defer v1.4+ nếu N items lớn (hiện 1-5 items đủ parallel).
- **Snapshot product image/brand vào OrderItemEntity** (Flyway V6 thêm `product_image_snapshot`, `product_brand_snapshot`) — defer; cần thiết khi product bị xóa (now soft-delete vẫn fetch được).
- **Click thumbnail → product detail page** — nice-to-have, defer (extra navigation, không trong success criteria).
- **Admin "AI suggest reply" button** — đã thuộc AI-05 Phase 22 (Chatbot MVP).
- **Coupon display row trong price breakdown** — đã thuộc COUP-05 Phase 20.
- **Review CTA bên cạnh từng item** (cho order DELIVERED) — nice-to-have, defer.
- **Reviewed Todos**: không có todos được surface.

</deferred>

---

*Phase: 17-s-a-order-detail-items*
*Context gathered: 2026-05-02*
