# Sequence Diagrams (LLD)

## Tóm tắt
Sequence diagrams cho 7 critical flows: Register, Login, Browse Product, Checkout (VNPay), Payment Callback, Admin Update Stock, Order State Sync. AI đọc trước khi implement mỗi flow.

## Context Links
- Overview: [00-overview.md](./00-overview.md)
- Class diagrams: [03-class-diagrams.md](./03-class-diagrams.md)
- Services: [services/](./services/)

---

## 1. Register (UC-AUTH)

```mermaid
sequenceDiagram
    participant C as Customer
    participant FE as NextJS FE
    participant GW as Gateway
    participant US as User Service
    participant DB as Postgres User
    participant K as Kafka
    participant E as Email Provider

    C->>FE: Fill register form (email, pw, name)
    FE->>FE: Validate Zod schema
    FE->>GW: POST /api/v1/auth/register
    GW->>US: forward
    US->>DB: SELECT user WHERE email=?
    DB-->>US: null (not exist)
    US->>US: bcrypt hash password
    US->>DB: INSERT user (status=ACTIVE, role=CUSTOMER)
    DB-->>US: userId
    US->>K: publish UserRegistered
    US->>US: generate access + refresh token
    US->>DB: INSERT refresh_token
    US-->>GW: 201 { user, accessToken, refreshToken }
    GW-->>FE: response
    FE->>FE: Store accessToken (memory), refresh (httpOnly cookie)
    FE-->>C: Redirect to home
    K-->>E: (async) send welcome email
```

---

## 2. Login (UC-AUTH)

```mermaid
sequenceDiagram
    participant C as Customer
    participant FE as NextJS FE
    participant GW as Gateway
    participant US as User Service
    participant DB as Postgres User
    participant R as Redis

    C->>FE: Fill login form (email, pw)
    FE->>GW: POST /api/v1/auth/login
    GW->>R: INCR rate:login:{ip}, check <= 10/min
    R-->>GW: ok
    GW->>US: forward
    US->>R: GET login-fail:{email}
    R-->>US: count (< 5 OK)
    US->>DB: SELECT user WHERE email=?
    DB-->>US: user or null
    alt User not found or password mismatch
        US->>R: INCR login-fail:{email} TTL 15m
        US-->>GW: 401 INVALID_CREDENTIALS
        GW-->>FE: error
    else Status = BLOCKED
        US-->>GW: 403 USER_BLOCKED
    else Success
        US->>R: DEL login-fail:{email}
        US->>US: generate access + refresh
        US->>DB: INSERT refresh_token
        US-->>GW: 200 { user, tokens }
        GW-->>FE: response
        FE->>FE: Store tokens
    end
```

---

## 3. Browse Product & Detail (UC-PRODUCT-BROWSE)

```mermaid
sequenceDiagram
    participant C as Customer
    participant FE as NextJS FE (SSR)
    participant GW as Gateway
    participant PS as Product Service
    participant R as Redis
    participant DB as Postgres Product

    Note over FE: Server Component fetch
    FE->>GW: GET /api/v1/products?category=phone&page=0&size=20
    GW->>PS: forward
    PS->>R: GET product:list:{queryHash}
    alt Cache hit
        R-->>PS: cached list
    else Cache miss
        PS->>DB: SELECT products WHERE status=ACTIVE, category, paginate
        DB-->>PS: rows
        PS->>R: SET product:list:{queryHash} TTL 5m
    end
    PS-->>GW: { data, meta }
    GW-->>FE: response
    FE-->>C: Render product grid (SSR HTML)

    C->>FE: Click product card
    FE->>GW: GET /api/v1/products/{slug}
    GW->>PS: forward
    PS->>R: GET product:{id}
    alt Cache miss
        PS->>DB: SELECT product + JOIN category + aggregate rating
        DB-->>PS: row
        PS->>R: SET product:{id} TTL 30m
    end
    PS-->>FE: product detail
    FE-->>C: Render detail page
```

---

## 4. Checkout + VNPay (UC-CHECKOUT-PAYMENT)

```mermaid
sequenceDiagram
    participant C as Customer
    participant FE as NextJS FE
    participant GW as Gateway
    participant OS as Order Service
    participant PS as Product Service
    participant V as VNPay
    participant K as Kafka
    participant R as Redis

    C->>FE: Click Checkout (cart, address, VNPAY)
    FE->>GW: POST /api/v1/checkout<br/>Idempotency-Key: uuid
    GW->>OS: forward

    OS->>R: GET idem:{key}
    alt Already processed
        R-->>OS: orderId
        OS-->>FE: return cached response
    else First request
        OS->>PS: POST /api/v1/products/validate (batch)
        PS->>PS: Check status=ACTIVE, stock>=qty, price
        PS-->>OS: validated items + current price
        OS->>OS: Calculate total (subtotal + ship + codFee)
        OS->>OS: Create order PENDING (DB txn)
        OS->>R: SET idem:{key} orderId TTL 24h
        OS->>K: publish OrderPlaced
        K-->>PS: (async) consume
        PS->>PS: Reserve stock (stock--, reservedStock++)
        PS->>K: publish StockReserved
        OS->>OS: Build VNPay URL (params + HMAC-SHA512)
        OS-->>FE: 201 { orderId, paymentUrl }
    end

    FE-->>C: redirect paymentUrl
    C->>V: Pay on VNPay page
```

---

## 5. VNPay IPN Callback (UC-CHECKOUT-PAYMENT)

```mermaid
sequenceDiagram
    participant V as VNPay
    participant GW as Gateway (public route)
    participant OS as Order Service
    participant DB as Postgres Order
    participant K as Kafka
    participant PS as Product Service

    V->>GW: POST /api/v1/payments/vnpay/ipn<br/>(TxnRef, Amount, ResponseCode, SecureHash, ...)
    GW->>OS: forward
    OS->>OS: Verify HMAC-SHA512 signature
    alt Signature invalid
        OS-->>V: { RspCode: "97", Message: "Invalid signature" }
    else Valid
        OS->>DB: SELECT order WHERE id=TxnRef FOR UPDATE
        DB-->>OS: order
        alt Order already PAID (idempotent)
            OS-->>V: { RspCode: "00", Message: "Confirm Success" }
        else ResponseCode = "00" (success)
            OS->>DB: UPDATE order SET state=PAID, paidAt=NOW()
            OS->>DB: INSERT order_state_log
            OS->>K: publish OrderPaid
            K-->>PS: consume
            PS->>PS: Commit stock (reservedStock--)
            OS-->>V: { RspCode: "00", Message: "Confirm Success" }
        else Failed (ResponseCode != 00)
            OS->>DB: UPDATE order SET state=CANCELLED
            OS->>K: publish OrderCancelled
            K-->>PS: consume
            PS->>PS: Release stock (stock++, reservedStock--)
            OS-->>V: { RspCode: "00", Message: "Confirm Received" }
        end
    end
```

---

## 6. Admin Update Stock (UC-ADMIN-PRODUCT)

```mermaid
sequenceDiagram
    participant A as Admin
    participant FE as Admin UI
    participant GW as Gateway
    participant PS as Product Service
    participant DB as Postgres Product
    participant R as Redis
    participant K as Kafka

    A->>FE: Input new stock for SKU
    FE->>GW: PATCH /api/v1/admin/products/{id}/stock<br/>{ delta: +50, reason: "Import batch #123" }
    GW->>GW: Verify JWT role=ADMIN
    GW->>PS: forward (X-User-Id: adminId)
    PS->>DB: BEGIN
    PS->>DB: UPDATE product SET stock=stock+50 WHERE id=?
    PS->>DB: INSERT stock_log (productId, delta, reason, adminId, createdAt)
    PS->>DB: COMMIT
    PS->>R: DEL product:{id}
    PS->>K: publish ProductUpdated (changedFields=[stock])
    PS-->>FE: 200 { product }
    FE-->>A: Show toast "Updated"
```

---

## 7. Order State Sync — Admin Ship Order (UC-ADMIN-ORDER)

```mermaid
sequenceDiagram
    participant A as Admin
    participant FE as Admin UI
    participant GW as Gateway
    participant OS as Order Service
    participant DB as Postgres Order
    participant K as Kafka
    participant NOT as (future) Notification

    A->>FE: Fill tracking code + click "Mark as Shipped"
    FE->>GW: PATCH /api/v1/admin/orders/{id}<br/>{ state: SHIPPED, trackingCode, carrier }
    GW->>OS: forward
    OS->>DB: BEGIN
    OS->>DB: SELECT order FOR UPDATE
    OS->>OS: Validate transition PROCESSING→SHIPPED
    alt Invalid transition
        OS->>DB: ROLLBACK
        OS-->>FE: 400 INVALID_STATE_TRANSITION
    else Valid
        OS->>DB: UPDATE order SET state=SHIPPED, trackingCode=?, shippedAt=NOW()
        OS->>DB: INSERT order_state_log
        OS->>DB: COMMIT
        OS->>K: publish OrderShipped
        OS->>K: publish OrderStateChanged (generic)
        OS-->>FE: 200 { order }
        K-->>NOT: (async) send email + push
    end
```

---

## Common patterns

### Pattern: Optimistic lock cho state transition
```
SELECT ... FOR UPDATE  (pessimistic)
OR
UPDATE ... WHERE state=<expected_from> (optimistic, check rows affected)
```
MVP dùng pessimistic (`FOR UPDATE`) cho order state — đơn giản, đúng.

### Pattern: Idempotent consumer
Mỗi consumer lưu `eventId` đã xử lý vào Redis key `consumed:{service}:{eventId}` TTL 7d.
Khi nhận event: check key tồn tại → skip. Xử lý xong → SET key.

### Pattern: Saga compensation
VNPay fail → không có compensation (order CANCELLED + stock release).
Stock reserve fail → Order service nhận `StockReservationFailed` → auto cancel order PENDING → refund nếu đã PAID (rare case).
