# Sequence Diagrams (v2)

## Tóm tắt
Các luồng chính đã được cập nhật theo kiến trúc tách payment/inventory/notification.

## Context Links
- Overview: [00-overview.md](./00-overview.md)
- Service docs: [services/](./services/)

## 1) Register + Welcome Email
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant GW as API Gateway
    participant US as User Service
    participant K as Kafka
    participant N as Notification Service

    FE->>GW: POST /api/v1/auth/register
    GW->>US: forward
    US->>US: create user + token
    US->>K: UserRegistered
    US-->>FE: 201
    K-->>N: consume UserRegistered
    N->>N: send welcome email
```

## 2) Checkout Create Order + Reserve Stock
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant GW as API Gateway
    participant OS as Order Service
    participant K as Kafka
    participant INV as Inventory Service

    FE->>GW: POST /api/v1/checkout (Idempotency-Key)
    GW->>OS: forward
    OS->>OS: validate cart + create PENDING
    OS->>K: OrderPlaced
    K-->>INV: consume OrderPlaced
    INV->>INV: reserve stock
    INV->>K: StockReserved / StockReservationFailed
```

## 3) VNPay Callback Processing
```mermaid
sequenceDiagram
    participant V as VNPay
    participant GW as API Gateway
    participant PAY as Payment Service
    participant K as Kafka
    participant OS as Order Service
    participant INV as Inventory Service

    V->>GW: /api/payments/vnpay/ipn
    GW->>PAY: forward
    PAY->>PAY: verify signature + idempotency
    PAY->>K: PaymentSucceeded / PaymentFailed
    K-->>OS: consume payment result
    OS->>OS: update order state
    K-->>INV: consume payment result
    INV->>INV: commit/release stock
```

## 4) Customer Cancel Order
```mermaid
sequenceDiagram
    participant FE as Frontend
    participant GW as API Gateway
    participant OS as Order Service
    participant K as Kafka
    participant INV as Inventory Service
    participant N as Notification Service

    FE->>GW: POST /api/v1/orders/{id}/cancel
    GW->>OS: forward
    OS->>OS: validate + set CANCELLED
    OS->>K: OrderCancelled
    K-->>INV: release stock
    K-->>N: send cancel notification
```

## 5) Admin Ship Order
```mermaid
sequenceDiagram
    participant FE as Admin Frontend
    participant GW as API Gateway
    participant OS as Order Service
    participant K as Kafka
    participant N as Notification Service

    FE->>GW: PATCH /api/v1/admin/orders/{id}/state=SHIPPED
    GW->>OS: forward
    OS->>OS: validate transition + save log
    OS->>K: OrderShipped
    K-->>N: send shipped email
```
