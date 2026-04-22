# TS-CHECKOUT-PAYMENT: Checkout & Payment

## Tóm tắt
Impl spec cho UC-CHECKOUT-PAYMENT — flow phức tạp nhất. Services: **Order** (core), **Product** (validate + stock reserve via event), **User** (address via API). External: VNPay. Saga pattern. Idempotency-Key. IPN là source of truth.

## Context Links
- BA Spec: [../ba/uc-checkout-payment.md](../ba/uc-checkout-payment.md)
- Services affected: ✅ Order | ✅ Product | ✅ User
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)
- Sequence: [../architecture/02-sequence-diagrams.md#4-checkout-vnpay-uc-checkout-payment](../architecture/02-sequence-diagrams.md#4-checkout-vnpay-uc-checkout-payment) và [#5](../architecture/02-sequence-diagrams.md#5-vnpay-ipn-callback-uc-checkout-payment)

## API Contracts

### POST /api/v1/checkout
Requires auth. `Idempotency-Key` header required (client-generated UUID).

**Request**
```json
{
  "addressId": "uuid",
  "paymentMethod": "VNPAY",
  "note": "Giao giờ hành chính",
  "items": [{ "productId": "uuid", "quantity": 2 }]
}
```

**Validation**
- `addressId`: required, must belong to user
- `paymentMethod`: VNPAY | COD
- `items`: required, >= 1, max 20
- `items[].quantity`: 1-10

**Response 201 (VNPay)**
```json
{
  "orderId": "uuid",
  "orderCode": "ORD-20260421-000123",
  "state": "PENDING",
  "total": 53980000,
  "paymentUrl": "https://sandbox.vnpayment.vn/...?vnp_SecureHash=..."
}
```

**Response 201 (COD)**
```json
{ "orderId": "uuid", "orderCode": "...", "state": "PENDING", "total": ... }
```
No `paymentUrl`.

**Errors**
- 400 `CART_EMPTY`
- 400 `ADDRESS_NOT_FOUND` — not belong to user
- 400 `PRODUCT_UNAVAILABLE` với `productId`
- 400 `INSUFFICIENT_STOCK` với `{ productId, requested, available }`
- 400 `PRICE_CHANGED` với diff (backlog — MVP dùng current price silently)
- 409 `IDEMPOTENCY_KEY_USED` — nếu key cùng user khác payload → conflict

### GET /api/v1/payments/vnpay/return
Public (browser redirect).
Query: all vnp_* params.
Action: verify signature → redirect FE `/orders/{id}?payment=success|failed`.
Response: 302 redirect.

### GET /api/v1/payments/vnpay/ipn
Public (server-to-server).
Query: all vnp_* params.
Action: verify signature, update order state.
**Response 200** (text/plain hoặc JSON tuỳ spec):
```
{"RspCode":"00","Message":"Confirm Success"}
```

### POST /api/v1/orders/{id}/cancel
Requires auth. Customer cancel.
**Request**: `{ "reason": "..." }` (optional)
**Response 200** — order updated
**Errors**: 400 INVALID_STATE_TRANSITION

## Database Changes

### Migration V2__create_orders.sql (Order DB)
```sql
CREATE SEQUENCE order_code_seq;

CREATE TABLE "order" (
    id UUID PRIMARY KEY,
    order_code VARCHAR(30) NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method VARCHAR(20) NOT NULL,
    subtotal BIGINT NOT NULL,
    shipping_fee BIGINT NOT NULL DEFAULT 0,
    cod_fee BIGINT NOT NULL DEFAULT 0,
    total BIGINT NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    ward VARCHAR(100),
    district VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    note TEXT,
    tracking_code VARCHAR(100),
    carrier VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    paid_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    completed_at TIMESTAMP
);
CREATE INDEX idx_order_user ON "order"(user_id, created_at DESC);
CREATE INDEX idx_order_state ON "order"(state);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES "order"(id),
    product_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_sku VARCHAR(50) NOT NULL,
    product_image VARCHAR(500),
    quantity INT NOT NULL,
    unit_price BIGINT NOT NULL,
    sale_price BIGINT,
    subtotal BIGINT NOT NULL
);

CREATE TABLE order_state_log (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES "order"(id),
    from_state VARCHAR(20),
    to_state VARCHAR(20) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_id UUID,
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE payment (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES "order"(id),
    method VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    vnp_txn_ref VARCHAR(50),
    vnp_transaction_no VARCHAR(50),
    vnp_response_code VARCHAR(10),
    vnp_pay_date VARCHAR(20),
    raw_response JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    paid_at TIMESTAMP
);
CREATE INDEX idx_payment_order ON payment(order_id);
CREATE INDEX idx_payment_vnp_txn ON payment(vnp_txn_ref);
```

## Event Contracts

### Publish: order.order.placed
```json
{
  "eventId": "uuid",
  "eventType": "OrderPlaced",
  "version": "1.0",
  "occurredAt": "...",
  "data": {
    "orderId": "uuid",
    "userId": "uuid",
    "items": [{ "productId": "uuid", "quantity": 2, "unitPrice": 26990000 }],
    "total": 53980000,
    "paymentMethod": "VNPAY",
    "createdAt": "..."
  }
}
```

### Publish: order.order.paid
```json
{ "orderId": "uuid", "paidAt": "...", "paymentMethod": "VNPAY", "amount": ..., "vnpTransactionNo": "..." }
```

### Publish: order.order.cancelled
```json
{ "orderId": "uuid", "reason": "...", "cancelledAt": "...", "cancelledBy": "SYSTEM|USER|ADMIN" }
```

### Publish: order.order.state_changed (generic)
```json
{ "orderId": "uuid", "fromState": "PENDING", "toState": "PAID", "actorType": "SYSTEM", "actorId": null, "at": "..." }
```

### Consume: product.stock.reservation_failed
Handler: auto cancel order PENDING, refund nếu đã PAID.

## Sequence
Xem [architecture/02-sequence-diagrams.md sections 4, 5](../architecture/02-sequence-diagrams.md#4-checkout-vnpay-uc-checkout-payment).

## Class/Component Design

### Backend — Order Service

```java
@RestController
public class CheckoutController {
    @PostMapping("/api/v1/checkout")
    public CheckoutResponse checkout(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CheckoutRequest req,
        @AuthenticationPrincipal UserPrincipal user,
        HttpServletRequest http
    );
}

@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class VNPayPaymentController {
    @GetMapping("/return")
    public RedirectView browserReturn(@RequestParam Map<String,String> allParams);

    @GetMapping("/ipn")
    public VNPayIPNResponse ipn(@RequestParam Map<String,String> allParams);
}

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    @GetMapping public PageResponse<OrderSummary> listMine(...);
    @GetMapping("/{id}") public OrderDetailResponse get(...);
    @PostMapping("/{id}/cancel") public OrderDetailResponse cancel(...);
}
```

```java
@Service
public class CheckoutService {
    private final CartService cartService;
    private final ProductServiceClient productClient;
    private final UserServiceClient userClient;
    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final VNPayService vnpayService;
    private final KafkaProducer kafkaProducer;
    private final IdempotencyService idempotencyService;

    @Transactional
    public CheckoutResponse checkout(UUID userId, String idempotencyKey, CheckoutRequest req, String clientIp);

    private Order createOrder(UUID userId, CheckoutRequest req, List<ProductInfo> products, AddressInfo address);
    private PricingBreakdown calculate(List<CheckoutItem> items, List<ProductInfo> products, String city, String paymentMethod);
}

@Service
public class VNPayService {
    public String buildPaymentUrl(Order order, String clientIp);
    public VNPayVerifyResult verifySignature(Map<String,String> params);
    public void handleIPN(Map<String,String> params); // core IPN logic
    public String buildSecureHash(Map<String,String> params); // HMAC-SHA512
}

@Service
public class OrderStateMachine {
    public Order transition(UUID orderId, OrderState next, UUID actorId, ActorType actorType, String reason);
    private boolean isValidTransition(OrderState from, OrderState to, ActorType actor);
}

@Component
public class OrderTimeoutScheduler {
    @Scheduled(cron = "0 */5 * * * *")
    public void cancelExpiredPendingOrders();

    @Scheduled(cron = "0 0 2 * * *")
    public void autoCompleteDeliveredOrders();
}

@Component
public class StockReservationFailedListener {
    @KafkaListener(topics = "product.stock.reservation_failed")
    public void handle(StockReservationFailedEvent event);
}
```

```java
@Service
public class IdempotencyService {
    private final StringRedisTemplate redis;

    public <T> T executeIdempotent(String key, Class<T> resultClass, Supplier<T> operation);
    // Use Redis SETNX + cached result JSON
}
```

### VNPay signature impl
```java
public String buildSecureHash(Map<String, String> params, String secretKey) {
    // 1. Remove vnp_SecureHash and vnp_SecureHashType
    // 2. Sort keys alphabetically
    // 3. Build query: key=urlencode(value)&...
    // 4. HMAC-SHA512(query, secretKey) → hex lowercase
}
```

### Frontend
- Pages: `/checkout`, `/orders/{id}` (confirmation)
- Components:
  - `CheckoutForm.tsx`
  - `AddressSelector.tsx`
  - `PaymentMethodSelector.tsx`
  - `OrderSummary.tsx` (sticky sidebar)
  - `OrderConfirmation.tsx`
- API: `lib/api/checkout.api.ts`, `lib/api/order.api.ts`
- Utils: `generateIdempotencyKey()` in sessionStorage

## Implementation Steps

### Backend — Order Service
1. [ ] Migrations V2 (order, order_item, order_state_log, payment)
2. [ ] Entities + enums (OrderState, PaymentMethod, PaymentStatus)
3. [ ] `OrderStateMachine` với transition matrix
4. [ ] `VNPayService` — buildPaymentUrl, verifySignature, handleIPN
5. [ ] Write **unit tests cho signature** trước (critical — dùng sample params từ VNPay docs)
6. [ ] `ProductServiceClient` (validate endpoint)
7. [ ] `UserServiceClient` (get address endpoint — add to User Service if not exist: GET /api/v1/internal/users/{id}/addresses/{addressId})
8. [ ] `IdempotencyService` với Redis
9. [ ] `CheckoutService.checkout` — transaction: validate → calculate → insert order → insert payment → publish event → return
10. [ ] `OrderController` (list, get, cancel)
11. [ ] `CheckoutController`
12. [ ] `VNPayPaymentController` (return + ipn)
13. [ ] `OrderTimeoutScheduler`
14. [ ] `StockReservationFailedListener`
15. [ ] Configure Kafka producer transactional cho OrderPlaced
16. [ ] Generate order_code: `ORD-{YYYYMMDD}-{seq6}` từ sequence
17. [ ] Integration tests (critical):
    - checkout VNPay flow end-to-end (mock VNPay return)
    - checkout COD flow
    - IPN success idempotent
    - IPN fail → cancel + release stock (verify Kafka event)
    - Timeout auto-cancel
    - Idempotency key double-request
18. [ ] Load test: 100 checkout/s

### Backend — Product Service
1. [ ] Listener `OrderPlacedListener` → reserve stock, publish StockReserved
2. [ ] Listener `OrderPaidListener` → commit stock
3. [ ] Listener `OrderCancelledListener` → release stock
4. [ ] Ensure idempotent (check consumed eventId in Redis)
5. [ ] Publish `StockReservationFailed` if reserve fail (insufficient)

### Frontend
1. [ ] Types `types/checkout.ts`, `types/order.ts`
2. [ ] API client checkout + order
3. [ ] `AddressSelector.tsx`
4. [ ] `PaymentMethodSelector.tsx` (radio VNPay/COD)
5. [ ] `OrderSummary.tsx` (breakdown)
6. [ ] `CheckoutForm.tsx` — orchestrate
7. [ ] Page `/checkout`
8. [ ] Generate + persist Idempotency-Key in sessionStorage during form
9. [ ] Handle response:
    - VNPay: `window.location.href = paymentUrl`
    - COD: redirect `/orders/{id}?success=true`
10. [ ] Page `/orders/{id}` confirmation với timeline
11. [ ] E2E test (critical):
    - checkout COD → see order PENDING
    - checkout VNPay with sandbox → simulate callback → PAID
    - Stock insufficient → error display
    - Double-submit with same Idempotency-Key → same order

## Test Strategy

### Unit
- `VNPayService.buildSecureHash` — test vectors từ VNPay docs (critical, exact match)
- `VNPayService.verifySignature` — accept valid, reject invalid
- `OrderStateMachine.transition` — matrix test all combinations
- `CheckoutService.calculate` — pricing combinations (free ship, COD fee)
- `IdempotencyService` — same key returns same result

### Integration (Testcontainers)
- Full checkout VNPay:
  1. POST /checkout → order PENDING + paymentUrl
  2. Send Kafka message `OrderPlaced`
  3. Product Service consume → reserve stock (verify DB)
  4. Simulate VNPay IPN with signature → verify order PAID
- COD flow
- IPN idempotency (2x IPN same → single update)
- Stock reservation failed → order cancel

### E2E (Playwright)
- Full checkout flow COD
- VNPay sandbox integration test (manual assist — có sandbox VNPay provide)

## Edge Cases & Gotchas (Critical)

1. **VNPay signature EXACT bytes**: sort keys before encode, URL-encode values (không encode key), HMAC-SHA512 (not SHA256), hex lowercase output. Test với sample từ docs trước khi production.
2. **Amount × 100**: VNPay unit = xu (đồng × 100). Dễ quên → test case bắt buộc.
3. **IPN response format**: VNPay expect JSON `{"RspCode":"00","Message":"..."}` OR specific text — check spec phiên bản đang dùng.
4. **IPN idempotency**: VNPay retry IPN nếu không nhận 00. Order đã PAID → vẫn reply 00. Order state CANCELLED → reply `02`.
5. **Browser return vs IPN race**: user có thể thấy "Thanh toán xong" trên FE trước khi IPN xử lý xong. FE polling hoặc hiển thị "Đang xác nhận...".
6. **Clock skew**: vnp_CreateDate/ExpireDate format `yyyyMMddHHmmss` — dùng UTC+7 (Vietnam time), not UTC. Một lỗi thường gặp.
7. **Idempotency-Key conflict**: cùng key nhưng payload khác → 409 (không silently accept cached result).
8. **Stock race**: user A và B checkout cùng 1 product stock=1. Product Service reserve serialized → 1 success, 1 fail (StockReservationFailed event).
9. **Order timeout edge**: order PENDING 29:59 → user pay thành công → IPN đến phút 30:01 → scheduled job đã CANCELLED. Solution: IPN check nếu state=CANCELLED → reply fail VNPay (VNPay sẽ refund); OR extend grace period IPN.
10. **Transactional outbox pattern**: publish Kafka trong cùng transaction với DB insert (dùng @KafkaTransactional hoặc Debezium CDC outbox). Simpler: publish sau commit, chấp nhận edge case mất event (rare, monitor alert).
11. **IP spoofing vnp_IpAddr**: take `X-Forwarded-For` cẩn thận. Dùng IP thực của client (first hop gateway).
12. **VNPay currency**: chỉ VND. Mọi amount integer.
13. **VNPay expire 15 phút**: nhưng order timeout 30 phút. Gap 15 phút cho IPN latency. OK.
14. **Order PENDING → PAID đã commit stock ở Product**: nếu user cancel sau PAID → cần refund (manual MVP). Không release stock vì đã commit.
15. **Concurrent admin confirm + auto-cancel**: admin CONFIRMED order PENDING vừa khi scheduler CANCEL. Dùng `FOR UPDATE` + state check at transition time.
