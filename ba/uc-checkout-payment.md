# UC-CHECKOUT-PAYMENT: Đặt hàng & Thanh toán

## Tóm tắt
Customer tiến hành checkout từ cart: chọn địa chỉ, payment method (VNPay/COD), xác nhận → order tạo PENDING + reserve stock. VNPay redirect → callback IPN → PAID. COD → chờ admin CONFIRMED. Saga pattern với Product service (stock reserve/commit/release).

## Context Links
- Strategy: [../strategy/services/order-business.md#checkout-flow](../strategy/services/order-business.md#checkout-flow)
- Technical Spec: [../technical-spec/ts-checkout-payment.md](../technical-spec/ts-checkout-payment.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Sequence: [../architecture/02-sequence-diagrams.md#4-checkout-vnpay-uc-checkout-payment](../architecture/02-sequence-diagrams.md#4-checkout-vnpay-uc-checkout-payment)

## Actors
- **Primary**: Customer (logged-in)
- **Secondary**: VNPay Gateway, Product Service, User Service (address)

## Preconditions
- User logged in
- Cart không rỗng
- User có ít nhất 1 address (hoặc nhập mới)

---

## Flow A — Pre-checkout page

### Main Flow
1. User ở /cart → click "Tiến hành đặt hàng"
2. FE check auth (redirect login nếu cần)
3. FE navigate `/checkout`
4. FE gọi song song:
   - GET /api/v1/cart (latest)
   - GET /api/v1/users/me/addresses
5. FE render:
   - **Section 1: Địa chỉ giao hàng** — dropdown addresses, default selected, link "+ Thêm địa chỉ mới"
   - **Section 2: Sản phẩm** — list items readonly (image + name + qty + price)
   - **Section 3: Ghi chú đơn hàng** — textarea optional
   - **Section 4: Phương thức thanh toán** — radio [VNPay | COD]
   - **Section 5: Tóm tắt** (sticky sidebar/bottom mobile):
     - Subtotal: sum(items)
     - Shipping fee: tính theo city + total (free-ship rule)
     - COD fee: 20k nếu < 1M và COD, 0 otherwise
     - Total
     - Button "Đặt hàng"

### Pricing Calculation Rules
- Subtotal = sum(item.quantity × item.effectivePrice) — dùng **current price** không snapshot
- Shipping fee:
  - HCM/HN: 30,000
  - Tỉnh khác: 50,000
  - Subtotal >= 3,000,000 → free
- COD fee:
  - COD + Subtotal < 1,000,000 → 20,000
  - Otherwise → 0
- Total = subtotal + shipping + codFee

### Exception Flows
- **EF-A1: Cart empty** → redirect /cart
- **EF-A2: User không có address** → show inline add-address form, không cho submit
- **EF-A3: Product trong cart INACTIVE** → highlight, yêu cầu remove
- **EF-A4: Stock không đủ** → highlight, suggest reduce quantity

### Acceptance Criteria
- [ ] AC-A1: Hiển thị breakdown: subtotal, shipping, codFee, total
- [ ] AC-A2: Change payment method → update COD fee realtime
- [ ] AC-A3: Change address (khác city) → update shipping fee
- [ ] AC-A4: Button "Đặt hàng" disable khi cart invalid

---

## Flow B — Submit Checkout (VNPay path)

### Main Flow
1. User click "Đặt hàng" với paymentMethod=VNPAY
2. FE gen `Idempotency-Key = UUID` (lưu in-memory/sessionStorage)
3. FE gửi POST /api/v1/checkout
   ```json
   {
     "addressId": "uuid",
     "paymentMethod": "VNPAY",
     "note": "Giao giờ hành chính",
     "items": [{ "productId": "uuid", "quantity": 2 }]
   }
   ```
   Header: `Idempotency-Key: uuid`
4. BE Order Service:
   - Check Idempotency-Key trong Redis → nếu có → return cached response
   - Gọi Product Service: POST /internal/products/validate { ids[] } → nhận products với current price + stock
   - Validate: tất cả ACTIVE, stock >= cart qty
   - Load address từ User Service (hoặc cache)
   - Calculate total
   - Begin transaction:
     - Insert order state=PENDING với snapshot items (name, sku, image, price, salePrice), shipping address, total
     - Insert payment row (method=VNPAY, status=INITIATED, amount=total)
     - Clear cart
   - Commit
   - Publish Kafka `OrderPlaced`
   - Save Idempotency-Key → orderId vào Redis TTL 24h
5. Product Service consume OrderPlaced → reserve stock (stock--, reservedStock++) → publish `StockReserved`
6. BE build VNPay redirect URL (HMAC-SHA512 signature)
7. BE trả 201:
   ```json
   {
     "orderId": "uuid",
     "orderCode": "ORD-20260421-000123",
     "paymentUrl": "https://sandbox.vnpayment.vn/...?vnp_SecureHash=..."
   }
   ```
8. FE redirect tới paymentUrl (window.location.href)
9. User thanh toán trên VNPay page

### VNPay Return Flow (browser)
1. VNPay redirect về `/api/v1/payments/vnpay/return?vnp_TxnRef=...&vnp_ResponseCode=...&vnp_SecureHash=...`
2. BE verify signature (optional — source of truth là IPN)
3. BE redirect về `/orders/{orderId}?payment=success` hoặc `?payment=failed`
4. FE Order detail page hiển thị status (có thể chưa update từ IPN nếu chậm → poll hoặc WebSocket)

### VNPay IPN Flow (server-to-server)
1. VNPay gọi GET `/api/v1/payments/vnpay/ipn?...`
2. BE verify signature HMAC-SHA512
3. BE load order by `vnp_TxnRef`
4. Check order.state:
   - Nếu PAID rồi → return `{ RspCode: "00", Message: "Confirm Success" }` (idempotent)
   - Nếu CANCELLED → return `{ RspCode: "02", Message: "Order already cancelled" }`
   - Nếu PENDING:
     - vnp_ResponseCode="00" → update state=PAID, paidAt=now, update payment row → publish `OrderPaid` → return `{ RspCode: "00" }`
     - vnp_ResponseCode khác → update state=CANCELLED, publish `OrderCancelled` → return `{ RspCode: "00", Message: "Confirm Received" }`
5. Product Service consume OrderPaid → commit stock (reservedStock--)

### Exception Flows
- **EF-B1: Stock insufficient** → BE trả 400 `INSUFFICIENT_STOCK` với `{ productId, requestedQty, availableQty }`
- **EF-B2: Product inactive** → 400 `PRODUCT_UNAVAILABLE`
- **EF-B3: Address không thuộc user** → 403
- **EF-B4: Idempotency-Key trùng** → trả order đã tạo (không tạo mới)
- **EF-B5: VNPay signature invalid** → IPN trả `{ RspCode: "97" }`
- **EF-B6: VNPay timeout/user không pay 15 phút** → VNPay cancel, redirect return với ResponseCode != 00 → order CANCELLED
- **EF-B7: Order PENDING > 30 phút** → scheduled job CANCELLED + release stock
- **EF-B8: StockReservationFailed event** (Product báo không đủ stock) → Order service auto CANCELLED order

### Acceptance Criteria
- [ ] AC-B1: Idempotency-Key prevent double order khi user refresh/retry
- [ ] AC-B2: Order PENDING tạo trước VNPay redirect
- [ ] AC-B3: IPN là source of truth (không phụ thuộc browser return)
- [ ] AC-B4: Signature verify đúng spec VNPay (HMAC-SHA512, sort params)
- [ ] AC-B5: IPN reply đúng format `{ RspCode, Message }` JSON
- [ ] AC-B6: Stock reserve trong 5s sau order created
- [ ] AC-B7: Timeout 30 phút tự cancel

### Data Inputs
- addressId (UUID, required)
- paymentMethod (VNPAY | COD, required)
- note (string, optional, max 500)
- items (array of `{ productId, quantity }`, required, >= 1 item)

### Data Outputs
- `{ orderId, orderCode, paymentUrl }` (VNPay) hoặc `{ orderId, orderCode }` (COD)

---

## Flow C — Submit Checkout (COD path)

### Main Flow
1. User click "Đặt hàng" với paymentMethod=COD
2. FE gửi POST /api/v1/checkout (giống VNPay nhưng paymentMethod=COD)
3. BE:
   - Tương tự Flow B steps 1-5 nhưng:
     - Order state=PENDING
     - Payment row method=COD, status=N/A (COD không có payment process)
   - Publish `OrderPlaced`
4. Product reserves stock
5. BE trả 201 `{ orderId, orderCode }` (không có paymentUrl)
6. FE redirect `/orders/{orderId}` với message "Đặt hàng thành công. Đang chờ xác nhận."
7. Admin confirm COD order → state=CONFIRMED → publish `OrderConfirmed`

### COD Special Rules
- Order COD không auto-cancel sau 30 phút (khác VNPay)
- Admin phải liên hệ customer để confirm (phone)
- Nếu customer không phản hồi → admin CANCELLED manual

### Acceptance Criteria
- [ ] AC-C1: COD order state PENDING → chờ admin
- [ ] AC-C2: COD fee tính đúng (20k nếu < 1M)
- [ ] AC-C3: Stock reserve immediate

---

## Flow D — Order Confirmation Page (Post-checkout)

### Main Flow
1. Sau checkout success → FE redirect `/orders/{orderId}`
2. FE gọi GET /api/v1/orders/{orderId}
3. FE render:
   - Header: "Đặt hàng thành công!" + order code
   - Status timeline (PENDING → PAID/CONFIRMED → ...)
   - Order details: items, address, payment method, total
   - CTAs: "Xem lịch sử đơn", "Tiếp tục mua sắm"
4. Nếu state=PENDING + VNPay → polling mỗi 3s kiểm tra state update (max 60s) hoặc gợi ý "Refresh nếu thanh toán xong"

### Acceptance Criteria
- [ ] AC-D1: Page render < 1s
- [ ] AC-D2: Email confirmation gửi trong 2 phút (async event listener)

---

## Business Rules (references)
- BR-PRICING-05: Shipping fee rules
- BR-PAYMENT-01, -02, -03, -04: Payment method rules
- BR-PAYMENT-05: Idempotency
- BR-PAYMENT-07: Amount × 100 cho VNPay
- BR-PAYMENT-08: Signature verification
- BR-INVENTORY-03, -05: Stock reserve/release
- BR-ORDER-02, -03: Order state rules

## Non-functional Requirements
- **Performance**:
  - POST /checkout p95 < 1s (có gọi Product + DB insert + Kafka)
  - VNPay IPN response < 500ms (VNPay có timeout)
- **Reliability**:
  - Idempotent checkout (prevent double order)
  - Idempotent IPN (prevent double-credit)
  - Saga rollback: stock release on failure
- **Security**:
  - HTTPS only
  - Signature verify bắt buộc trên IPN
  - Không log sensitive VNPay data (card info — VNPay không trả nhưng đề phòng)
- **Availability**: 99.7% (critical revenue path)

## UI Screens
- `/checkout` — checkout form
- VNPay page (external)
- `/orders/{id}?payment=success|failed` — confirmation/result
