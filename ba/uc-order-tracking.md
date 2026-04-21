# UC-ORDER-TRACKING: Lịch sử / Chi tiết / Tracking / Hủy đơn

## Tóm tắt
Customer xem danh sách đơn hàng với filter theo status, xem chi tiết đơn (items, timeline, tracking code), hủy đơn khi còn PENDING. Không edit items sau khi đặt (chỉ address có thể edit đến CONFIRMED).

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Technical Spec: [../technical-spec/ts-order-tracking.md](../technical-spec/ts-order-tracking.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)

## Actors
- **Primary**: Customer (logged-in)

## Preconditions
- User logged in role=CUSTOMER

---

## Flow A — Order History List

### Main Flow
1. User vào `/account/orders`
2. FE gọi GET /api/v1/orders?page=0&size=10&sort=createdAt,desc
3. BE trả orders của `userId` (JWT) paginated
4. FE render:
   - Filter tabs: Tất cả | Đang xử lý | Đang giao | Hoàn thành | Đã hủy
   - List cards với: orderCode, createdAt, status badge, items preview (3 items + "…và N sản phẩm khác"), total, actions
   - Pagination

### Filter Mapping
- **Tất cả**: no filter
- **Đang xử lý**: state IN (PENDING, PAID, CONFIRMED, PROCESSING)
- **Đang giao**: state = SHIPPED
- **Hoàn thành**: state IN (DELIVERED, COMPLETED)
- **Đã hủy**: state IN (CANCELLED, REFUNDED)

### Status Badge Color
- PENDING: gray "Chờ thanh toán"
- PAID: blue "Đã thanh toán"
- CONFIRMED: blue "Đã xác nhận"
- PROCESSING: blue "Đang xử lý"
- SHIPPED: orange "Đang giao"
- DELIVERED: green "Đã giao"
- COMPLETED: green "Hoàn thành"
- CANCELLED: red "Đã hủy"
- REFUNDED: purple "Đã hoàn tiền"

### Acceptance Criteria
- [ ] AC-A1: List hiển thị 10 đơn/page, sort newest first
- [ ] AC-A2: Filter tabs sync URL query `?tab=pending`
- [ ] AC-A3: Click card → detail page
- [ ] AC-A4: Empty state per tab: "Không có đơn trong trạng thái này"

---

## Flow B — Order Detail

### Main Flow
1. User click order card → `/account/orders/{orderId}`
2. FE gọi GET /api/v1/orders/{orderId}
3. BE verify order.userId == JWT.userId (else 403 hoặc 404)
4. BE trả full order info
5. FE render:
   - **Header**: order code, created date, current status badge
   - **Timeline**: visual steps showing progression (PENDING → PAID/CONFIRMED → PROCESSING → SHIPPED → DELIVERED) với timestamps
   - **Items**: list với image + name + qty + unit price + subtotal + button "Viết đánh giá" (nếu DELIVERED và chưa review)
   - **Shipping Address**: recipient + phone + full address
   - **Payment**: method (VNPay/COD), amount, transactionNo (nếu VNPay)
   - **Summary**: subtotal, shipping, codFee, total
   - **Tracking** (nếu có trackingCode): carrier + code + link "Track shipping" (external)
   - **Actions**:
     - Nếu PENDING: "Hủy đơn"
     - Nếu DELIVERED: "Viết đánh giá" per item + "Yêu cầu hoàn trả" (contact support MVP)
     - "Mua lại" (re-add items to cart) — phase 2

### Data Outputs (GET order detail)
```json
{
  "id": "uuid",
  "orderCode": "ORD-20260421-000123",
  "state": "SHIPPED",
  "stateLabel": "Đang giao",
  "createdAt": "2026-04-20T10:00:00Z",
  "paidAt": "2026-04-20T10:05:00Z",
  "confirmedAt": "2026-04-20T11:00:00Z",
  "shippedAt": "2026-04-21T09:00:00Z",
  "items": [
    {
      "productId": "uuid",
      "productName": "iPhone 15 Pro 256GB",
      "productImage": "...",
      "quantity": 1,
      "unitPrice": 28990000,
      "salePrice": 26990000,
      "subtotal": 26990000,
      "canReview": false
    }
  ],
  "subtotal": 26990000,
  "shippingFee": 0,
  "codFee": 0,
  "total": 26990000,
  "shippingAddress": { "recipientName": "...", "phone": "...", "addressLine1": "...", "ward": "...", "district": "...", "city": "..." },
  "paymentMethod": "VNPAY",
  "payment": { "vnpTransactionNo": "...", "vnpPayDate": "..." },
  "trackingCode": "GHN-123456789",
  "carrier": "GHN",
  "trackingUrl": "https://...",
  "timeline": [
    { "state": "PENDING", "at": "2026-04-20T10:00:00Z" },
    { "state": "PAID", "at": "2026-04-20T10:05:00Z" },
    { "state": "CONFIRMED", "at": "2026-04-20T11:00:00Z" },
    { "state": "PROCESSING", "at": "2026-04-21T08:30:00Z" },
    { "state": "SHIPPED", "at": "2026-04-21T09:00:00Z" }
  ]
}
```

### Exception Flows
- **EF-B1: Order không thuộc user** → 404 (tránh leak)
- **EF-B2: Order không tồn tại** → 404

### Acceptance Criteria
- [ ] AC-B1: Load detail < 500ms
- [ ] AC-B2: Timeline render với visual progression
- [ ] AC-B3: Review button chỉ show khi DELIVERED + chưa review (per item)
- [ ] AC-B4: Tracking link chỉ khi SHIPPED với code

---

## Flow C — Cancel Order

### Preconditions
- Order state = PENDING
- User là owner

### Main Flow
1. User vào detail order PENDING → click "Hủy đơn"
2. Confirm dialog: "Bạn có chắc hủy đơn này? Hành động không thể hoàn tác."
3. User confirm
4. FE gửi POST /api/v1/orders/{id}/cancel { reason: "optional" }
5. BE:
   - Load order FOR UPDATE
   - Check state = PENDING (else 400 `INVALID_STATE_TRANSITION`)
   - Check ownership
   - Transition state → CANCELLED
   - Insert order_state_log
   - Publish `OrderCancelled` event
6. Product Service consume → release reserved stock
7. BE trả 200 { order }
8. FE refresh, hiển thị "Đơn đã được hủy, stock trả về"

### Exception Flows
- **EF-C1: Order state != PENDING** → 400 `CANNOT_CANCEL_NOW` với message "Đơn đã xử lý, liên hệ hotline để hủy"
- **EF-C2: Order đã được cancel** → 400 `ALREADY_CANCELLED`

### Admin Cancel Extended
- Admin có thể cancel đến state CONFIRMED (BR-ORDER-09)
- Sau CONFIRMED → chỉ refund flow (không cancel đơn thuần)

### Acceptance Criteria
- [ ] AC-C1: Customer chỉ cancel PENDING
- [ ] AC-C2: Stock release trong 5s sau cancel
- [ ] AC-C3: Cancel confirm dialog required
- [ ] AC-C4: Sau cancel → order hiển thị trong tab "Đã hủy"

---

## Flow D — Refund Request (MVP: manual via support)

### Main Flow
1. User vào order DELIVERED trong 30 ngày
2. Click "Yêu cầu hoàn trả"
3. Show modal với reason + contact info
4. MVP: chỉ gửi email support (không in-app flow)
5. Admin xử lý manual → nếu duyệt → update state=REFUNDED qua admin UI

### Phase 2 (in-app refund)
1. In-app form với reason dropdown (defect, wrong item, changed mind)
2. Upload evidence photos
3. Submit → tạo refund request row
4. Admin duyệt/reject qua admin UI
5. Auto refund amount + state update

### Acceptance Criteria (MVP)
- [ ] AC-D1: Button "Yêu cầu hoàn trả" chỉ show khi DELIVERED + < 30 ngày
- [ ] AC-D2: Click → hiển thị instruction liên hệ support

---

## Flow E — Track Shipping (external)

### Main Flow
1. User ở order detail state=SHIPPED
2. Click "Theo dõi vận chuyển"
3. Open tab mới với URL carrier:
   - GHN: `https://donhang.ghn.vn/?order_code={trackingCode}`
   - GHTK: `https://i.ghtk.vn/{trackingCode}`
   - ViettelPost: `https://viettelpost.com.vn/tra-cuu-hanh-trinh-don-hang?code={trackingCode}`

### Acceptance Criteria
- [ ] AC-E1: Tracking URL tự gen từ carrier + trackingCode
- [ ] AC-E2: Click open tab mới (không navigate away)

---

## Business Rules (references)
- BR-ORDER-09: Cancel rules
- BR-REFUND-01, -02: Refund window & conditions
- BR-ORDER-12: No edit items post-PENDING

## Non-functional Requirements
- **Performance**: GET /orders p95 < 500ms, GET /orders/{id} p95 < 400ms
- **Security**: User chỉ xem order của mình (JWT userId check BE)
- **Data retention**: giữ order history vô thời hạn (không xóa)

## UI Screens
- `/account/orders` — order list
- `/account/orders/{id}` — order detail
- Confirm cancel dialog (modal)
- Refund request modal (MVP: simple contact form)
