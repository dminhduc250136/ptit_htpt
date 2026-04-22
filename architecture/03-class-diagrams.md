# Class Diagrams (v2)

## Tóm tắt
Domain model rút gọn theo 6 backend services. Dùng cho định hướng ownership và mapping entity khi implement.

## User Service
```mermaid
classDiagram
    class User {
      +UUID id
      +String email
      +String passwordHash
      +String role
      +String status
    }
    class Address {
      +UUID id
      +UUID userId
      +String city
      +boolean isDefault
    }
    class RefreshToken {
      +UUID id
      +UUID userId
      +String tokenHash
      +Instant expiresAt
    }
    User "1" --> "*" Address
    User "1" --> "*" RefreshToken
```

## Product Service
```mermaid
classDiagram
    class Product {
      +UUID id
      +String sku
      +String slug
      +Long price
      +Long salePrice
      +String status
    }
    class Category {
      +UUID id
      +UUID parentId
      +String name
    }
    class Review {
      +UUID id
      +UUID productId
      +UUID userId
      +int rating
      +boolean isHidden
    }
    Product "*" --> "1" Category
    Product "1" --> "*" Review
```

## Order Service
```mermaid
classDiagram
    class Cart {
      +UUID id
      +UUID userId
    }
    class CartItem {
      +UUID id
      +UUID cartId
      +UUID productId
      +int quantity
    }
    class Order {
      +UUID id
      +String orderCode
      +UUID userId
      +String state
      +Long total
    }
    class OrderStateLog {
      +UUID id
      +UUID orderId
      +String fromState
      +String toState
      +String actorType
    }
    Cart "1" --> "*" CartItem
    Order "1" --> "*" OrderStateLog
```

## Payment Service
```mermaid
classDiagram
    class PaymentTransaction {
      +UUID id
      +UUID orderId
      +String provider
      +String status
      +String txnRef
      +Long amount
    }
    class PaymentCallbackLog {
      +UUID id
      +String txnRef
      +String responseCode
      +boolean processed
    }
    PaymentTransaction "1" --> "*" PaymentCallbackLog
```

## Inventory Service
```mermaid
classDiagram
    class InventoryItem {
      +UUID productId
      +int available
      +int reserved
    }
    class StockMovement {
      +UUID id
      +UUID productId
      +String movementType
      +int quantity
      +String reason
    }
    class Reservation {
      +UUID id
      +UUID orderId
      +UUID productId
      +int quantity
      +Instant expiresAt
    }
    InventoryItem "1" --> "*" StockMovement
    InventoryItem "1" --> "*" Reservation
```

## Notification Service
```mermaid
classDiagram
    class NotificationTemplate {
      +String code
      +String channel
      +String locale
    }
    class NotificationMessage {
      +UUID id
      +String eventType
      +String recipient
      +String status
      +int retryCount
    }
    NotificationTemplate "1" --> "*" NotificationMessage
```
