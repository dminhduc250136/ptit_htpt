# Class Diagrams (LLD)

## Tóm tắt
Domain model class diagrams cho 3 services. Chỉ core entities (~30%) — details field phụ, enum values xem trong TS files. AI dùng để hiểu quan hệ entity trước khi code.

## Context Links
- Overview: [00-overview.md](./00-overview.md)
- Sequence diagrams: [02-sequence-diagrams.md](./02-sequence-diagrams.md)
- Services: [services/](./services/)

---

## User Service Domain

```mermaid
classDiagram
    class User {
        +UUID id
        +String email
        +String passwordHash
        +String fullName
        +String phone
        +String avatarUrl
        +UserRole role
        +UserStatus status
        +Instant createdAt
        +Instant updatedAt
        +List~Address~ addresses
        +changePassword(String newPw)
        +block(String reason)
        +unblock()
    }

    class Address {
        +UUID id
        +UUID userId
        +String recipientName
        +String phone
        +String addressLine1
        +String ward
        +String district
        +String city
        +boolean isDefault
        +Instant createdAt
    }

    class RefreshToken {
        +UUID id
        +UUID userId
        +String tokenHash
        +Instant expiresAt
        +Instant createdAt
        +boolean revoked
        +revoke()
        +isValid() boolean
    }

    class PasswordResetToken {
        +UUID id
        +UUID userId
        +String tokenHash
        +Instant expiresAt
        +boolean used
    }

    class UserRole {
        <<enumeration>>
        CUSTOMER
        ADMIN
    }

    class UserStatus {
        <<enumeration>>
        ACTIVE
        BLOCKED
    }

    User "1" --> "*" Address
    User "1" --> "*" RefreshToken
    User "1" --> "*" PasswordResetToken
    User --> UserRole
    User --> UserStatus
```

---

## Product Service Domain

```mermaid
classDiagram
    class Product {
        +UUID id
        +String sku
        +String slug
        +String name
        +String brand
        +UUID categoryId
        +String description
        +Long price
        +Long salePrice
        +Integer stock
        +Integer reservedStock
        +List~String~ images
        +JsonNode specs
        +ProductStatus status
        +BigDecimal rating
        +Integer reviewCount
        +Instant createdAt
        +Instant updatedAt
        +effectivePrice() Long
        +reserveStock(int qty)
        +commitReserved(int qty)
        +releaseReserved(int qty)
        +updateStock(int delta, String reason, UUID adminId)
    }

    class Category {
        +UUID id
        +UUID parentId
        +String name
        +String slug
        +String icon
        +Integer sortOrder
        +Instant createdAt
    }

    class Review {
        +UUID id
        +UUID productId
        +UUID userId
        +UUID orderId
        +Integer rating
        +String comment
        +boolean isHidden
        +Instant createdAt
        +Instant updatedAt
    }

    class StockLog {
        +UUID id
        +UUID productId
        +Integer delta
        +Integer stockAfter
        +String reason
        +StockChangeType type
        +UUID actorId
        +String actorType
        +Instant createdAt
    }

    class ReviewEligibility {
        +UUID userId
        +UUID productId
        +UUID orderId
        +Instant eligibleAt
        +boolean used
    }

    class ProductStatus {
        <<enumeration>>
        DRAFT
        ACTIVE
        INACTIVE
        OUT_OF_STOCK
    }

    class StockChangeType {
        <<enumeration>>
        ADMIN_UPDATE
        ORDER_RESERVE
        ORDER_COMMIT
        ORDER_RELEASE
    }

    Product "*" --> "1" Category
    Product "1" --> "*" Review
    Product "1" --> "*" StockLog
    Review "*" --> "1" ReviewEligibility
    Product --> ProductStatus
    StockLog --> StockChangeType
```

---

## Order Service Domain

```mermaid
classDiagram
    class Cart {
        +UUID id
        +UUID userId
        +Instant createdAt
        +Instant updatedAt
        +List~CartItem~ items
        +addItem(UUID productId, int qty)
        +updateItem(UUID productId, int qty)
        +removeItem(UUID productId)
        +clear()
        +totalQuantity() int
    }

    class CartItem {
        +UUID id
        +UUID cartId
        +UUID productId
        +String productName
        +Integer quantity
        +Long priceSnapshot
        +Long salePriceSnapshot
        +Instant addedAt
        +effectivePrice() Long
        +subtotal() Long
    }

    class Order {
        +UUID id
        +String orderCode
        +UUID userId
        +OrderState state
        +List~OrderItem~ items
        +OrderAddress shippingAddress
        +PaymentMethod paymentMethod
        +Long subtotal
        +Long shippingFee
        +Long codFee
        +Long total
        +String trackingCode
        +String carrier
        +String note
        +Instant createdAt
        +Instant paidAt
        +Instant shippedAt
        +Instant deliveredAt
        +Instant cancelledAt
        +transitionTo(OrderState next, UUID actorId, String actorType, String reason)
        +canCancelByUser() boolean
        +canRefund() boolean
    }

    class OrderItem {
        +UUID id
        +UUID orderId
        +UUID productId
        +String productName
        +String productSku
        +String productImage
        +Integer quantity
        +Long unitPrice
        +Long salePrice
        +Long subtotal
    }

    class OrderAddress {
        +String recipientName
        +String phone
        +String addressLine1
        +String ward
        +String district
        +String city
    }

    class Payment {
        +UUID id
        +UUID orderId
        +PaymentMethod method
        +Long amount
        +PaymentStatus status
        +String vnpTxnRef
        +String vnpTransactionNo
        +String vnpResponseCode
        +String vnpPayDate
        +String rawResponse
        +Instant createdAt
        +Instant paidAt
    }

    class OrderStateLog {
        +UUID id
        +UUID orderId
        +OrderState fromState
        +OrderState toState
        +String actorType
        +UUID actorId
        +String reason
        +Instant createdAt
    }

    class OrderState {
        <<enumeration>>
        PENDING
        PAID
        CONFIRMED
        PROCESSING
        SHIPPED
        DELIVERED
        COMPLETED
        CANCELLED
        REFUNDED
    }

    class PaymentMethod {
        <<enumeration>>
        VNPAY
        COD
    }

    class PaymentStatus {
        <<enumeration>>
        INITIATED
        SUCCESS
        FAILED
        EXPIRED
    }

    Cart "1" --> "*" CartItem
    Order "1" --> "*" OrderItem
    Order "1" --> "*" OrderStateLog
    Order "1" --> "1" OrderAddress
    Order "1" --> "0..1" Payment
    Order --> OrderState
    Order --> PaymentMethod
    Payment --> PaymentStatus
```

---

## Shared patterns

### ID strategy
- UUID v7 (time-ordered) cho tất cả entity — tránh hot partition khi insert nhiều.
- Order có thêm `orderCode` human-readable: `ORD-{YYYYMMDD}-{seq6}` (VD `ORD-20260421-000123`).

### Audit fields
Mọi entity có: `createdAt` (Instant), `updatedAt` (Instant). Auto-populate bằng JPA `@CreationTimestamp` / `@UpdateTimestamp`.

### Soft delete
- `Product`: không xóa, dùng `status=INACTIVE`.
- `User`: không xóa, dùng `status=BLOCKED`.
- `Order`: không xóa, dùng `state=CANCELLED`.
- `Review`: không xóa, dùng `isHidden=true`.

### Money type
- Dùng `Long` cho VND (integer, không có decimal).
- Không dùng `BigDecimal` (overkill cho VND).
- Rating: `BigDecimal(3,1)` — VD 4.3.

### JSON fields
- `Product.specs`: `JsonNode` (Jackson) mapped với JPA `@Type(JsonBinaryType.class)` (Hibernate Types lib).
- Query JSONB: Postgres `jsonb` operators `->>`, `@>`.

### Event payload vs Entity
- Event payload là DTO riêng (record), không serialize entity trực tiếp.
- Giữ ổn định schema event qua versioning (`version: "1.0"` trong envelope).
