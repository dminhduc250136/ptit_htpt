# UC-ADMIN-ORDER: Admin — Order Management

## Tóm tắt
Admin xem orders với filter status/date/customer, update state (PENDING→CONFIRMED, CONFIRMED→PROCESSING, PROCESSING→SHIPPED với tracking code, SHIPPED→DELIVERED), cancel order (đến CONFIRMED), trigger refund (DELIVERED trong 30 ngày).

## Context Links
- Strategy: [../strategy/services/order-business.md](../strategy/services/order-business.md)
- Technical Spec: [../technical-spec/ts-admin-order.md](../technical-spec/ts-admin-order.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Sequence (Ship): [../architecture/02-sequence-diagrams.md#7-order-state-sync-admin-ship-order](../architecture/02-sequence-diagrams.md#7-order-state-sync-admin-ship-order)

## Actors
- **Primary**: Admin
- **Secondary**: Customer (receive notification)

## Preconditions
- User logged in role=ADMIN

---

## Flow A — Order List (Admin)

### Main Flow
1. Admin vào `/admin/orders`
2. FE gọi GET /api/v1/admin/orders?page=0&size=20&state=&dateFrom=&dateTo=&q=
3. BE trả orders (all users, all states)
4. FE render:
   - Filter bar:
     - State dropdown
     - Date range picker (createdAt)
     - Search: order code hoặc customer email/phone
     - Payment method filter
   - Table columns: Order Code, Customer (name + phone), Items count, Total, Payment, State, Created At, Actions
   - Bulk actions: Export CSV, (backlog) batch update state
   - Pagination

### Default View
- Sort: createdAt desc (newest first)
- Filter: tab quick "Chờ xử lý" (PENDING, PAID, CONFIRMED) highlighted

### Acceptance Criteria
- [ ] AC-A1: Filter combine (AND): state + dateRange + search
- [ ] AC-A2: Search work on orderCode, customer email, phone
- [ ] AC-A3: Badge count per state tab (10 PENDING, 5 PAID, ...)
- [ ] AC-A4: Export CSV trong current filter

---

## Flow B — Order Detail (Admin)

### Main Flow
1. Admin click row → `/admin/orders/{id}`
2. FE gọi GET /api/v1/admin/orders/{id}
3. BE trả full order (include customer info từ User Service — cross-service call)
4. FE render:
   - **Header**: orderCode + state badge + date
   - **Customer info**: name, email, phone, link to user profile
   - **Items**: same as customer view
   - **Shipping address**
   - **Payment info**: method, status, VNPay transactionNo + payDate (nếu có)
   - **State timeline**: visual với all past states + timestamps + actor (Customer/Admin/System)
   - **Actions panel** (dynamic per current state):
     - PENDING + VNPAY: "Cancel" button (admin force)
     - PENDING + COD: "Confirm" button (→ CONFIRMED), "Cancel"
     - PAID: "Confirm" (→ CONFIRMED), "Cancel"
     - CONFIRMED: "Start Processing" (→ PROCESSING), "Cancel"
     - PROCESSING: "Mark as Shipped" (require trackingCode + carrier) → SHIPPED
     - SHIPPED: "Mark as Delivered" → DELIVERED
     - DELIVERED: "Refund" (trong 30 ngày)
   - **Internal notes**: textarea cho admin (backlog)

### Acceptance Criteria
- [ ] AC-B1: Timeline show full history với actors
- [ ] AC-B2: Action buttons context-aware theo state
- [ ] AC-B3: Customer info link to user detail (UC-ADMIN-USER)

---

## Flow C — State Transition Actions

### Flow C1 — Confirm Order (COD or Paid)
- Endpoint: POST /api/v1/admin/orders/{id}/confirm
- Precondition: state IN (PENDING & COD, PAID)
- Action: state → CONFIRMED, confirmedAt=now, confirmedBy=adminId
- Publish: `OrderConfirmed`
- COD case: đây là trigger để commit stock (khác VNPay commit khi PAID)
  - Logic: nếu COD, publish `OrderPaid` (equivalent) hoặc `OrderConfirmed` thì Product consume → commit stock

### Flow C2 — Start Processing
- Endpoint: POST /api/v1/admin/orders/{id}/process (hoặc PATCH state)
- Precondition: state=CONFIRMED
- Action: state → PROCESSING
- Publish: `OrderProcessing`

### Flow C3 — Mark as Shipped
- Endpoint: PATCH /api/v1/admin/orders/{id}
- Body: `{ state: "SHIPPED", trackingCode: "...", carrier: "GHN" }`
- Precondition: state=PROCESSING
- Validation: trackingCode required, carrier in [GHN, GHTK, VIETTELPOST]
- Action: state → SHIPPED, shippedAt=now, update trackingCode + carrier
- Publish: `OrderShipped`

### Flow C4 — Mark as Delivered
- Endpoint: POST /api/v1/admin/orders/{id}/deliver
- Precondition: state=SHIPPED
- Action: state → DELIVERED, deliveredAt=now
- Publish: `OrderDelivered` (critical — Product Service build review eligibility)

### Flow C5 — Auto Complete (background)
- Scheduled job daily: find orders DELIVERED > 7 days → state=COMPLETED
- Publish `OrderStateChanged`

### Flow C6 — Admin Cancel
- Endpoint: POST /api/v1/admin/orders/{id}/cancel
- Body: `{ reason }`
- Precondition: state IN (PENDING, PAID, CONFIRMED) — sau CONFIRMED không cancel, chỉ refund
- Action: state → CANCELLED, cancelledAt=now, reason logged
- Publish: `OrderCancelled` → Product Service release stock
- If PAID (VNPay) → refund manual (MVP): admin ghi note + chuyển tiền ngoài hệ thống

### Flow C7 — Refund
- Endpoint: POST /api/v1/admin/orders/{id}/refund
- Body: `{ reason, refundAmount (default = total - shippingFee) }`
- Precondition: state=DELIVERED, deliveredAt > now - 30d
- Action: state → REFUNDED, refundedAt=now, refundedAmount, reason logged
- Publish: `OrderRefunded`
- Manual process: admin chuyển tiền ngoài hệ thống (MVP)
- Stock: không trả lại (BR-REFUND-05)

### Exception Flows (cross-state)
- **EF-C1: Invalid transition** → 400 `INVALID_STATE_TRANSITION` với valid next states
- **EF-C2: Missing required field** (VD: trackingCode khi ship) → 400
- **EF-C3: Refund > 30 days** → 400 `REFUND_WINDOW_EXPIRED`
- **EF-C4: Concurrent update** (race condition) → 409 `ORDER_LOCKED` (dùng `FOR UPDATE` hoặc version column)

### Acceptance Criteria
- [ ] AC-C1: State machine enforce transition matrix
- [ ] AC-C2: Mỗi transition tạo state_log entry
- [ ] AC-C3: Kafka event published cho mỗi transition
- [ ] AC-C4: Concurrent update protected
- [ ] AC-C5: Email notification customer gửi (async) với content tương ứng state

---

## Flow D — Bulk Operations (backlog)

### Export CSV
1. Admin filter + click "Export CSV"
2. FE gửi GET /api/v1/admin/orders/export?...filter
3. BE stream CSV response với full order data
4. FE download file

### Batch State Update (backlog phase 2)
1. Admin select rows + choose action "Mark as Shipped"
2. Prompt batch trackingCode (per row)
3. BE process sequential (transaction per order)

### Acceptance Criteria
- [ ] AC-D1: Export max 10000 rows (pagination or streaming)

---

## Business Rules (references)
- BR-ORDER-01 đến -12
- BR-PAYMENT-04: COD flow
- BR-REFUND-01 đến -05
- BR-INVENTORY-04: Commit stock (khi CONFIRMED hoặc PAID)

## Non-functional Requirements
- **Performance**: Admin order list < 500ms (với 10k orders)
- **Security**: ADMIN role only, audit log mọi state change
- **Consistency**: State transition atomic (DB transaction + Kafka transactional)
- **Notification**: Customer email per state change (async)

## UI Screens
- `/admin/orders` — list với filter
- `/admin/orders/{id}` — detail với actions
- Modals: Confirm cancel, Mark shipped (tracking form), Refund (amount + reason)
