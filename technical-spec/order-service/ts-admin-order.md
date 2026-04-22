# TS-ADMIN-ORDER: Admin Order Management

## Tóm tắt
Impl spec cho UC-ADMIN-ORDER. Service: **Order** (core), **User** (lookup customer info via internal API), **Product** (commit stock on COD confirm via event). Endpoints admin: list/detail/transition/refund orders.

## Context Links
- BA Spec: [../ba/uc-admin-order.md](../ba/uc-admin-order.md)
- Services affected: ✅ Order | ✅ User | ✅ Product
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)

## API Contracts

### GET /api/v1/admin/orders
Admin only.

**Query**: page, size, state, paymentMethod, dateFrom, dateTo, q (orderCode/email/phone), userId, sort

**Response 200**
```json
{
  "data": [
    {
      "id": "uuid",
      "orderCode": "ORD-20260421-000123",
      "state": "PENDING",
      "total": 26990000,
      "paymentMethod": "VNPAY",
      "customer": { "email": "...", "fullName": "...", "phone": "..." },
      "itemsCount": 2,
      "createdAt": "..."
    }
  ],
  "meta": {...},
  "counts": { "PENDING": 10, "PAID": 5, "CONFIRMED": 12, ... }
}
```

### GET /api/v1/admin/orders/{id}
**Response 200** — full OrderAdminDetail với customer info + state log

### POST /api/v1/admin/orders/{id}/confirm
For PENDING (COD) or PAID orders → CONFIRMED.
**Request**: `{ "reason"? }`
**Response 200** — order
**Side effect (COD)**: publish `OrderConfirmed` → Product commits stock

### POST /api/v1/admin/orders/{id}/process
CONFIRMED → PROCESSING.

### PATCH /api/v1/admin/orders/{id}
Generic state transition.
**Request**
```json
{ "state": "SHIPPED", "trackingCode": "GHN-123", "carrier": "GHN" }
```
**Validation**: trackingCode + carrier required when state=SHIPPED.

### POST /api/v1/admin/orders/{id}/deliver
SHIPPED → DELIVERED.
**Side effect**: publish `OrderDelivered` → Product enables review eligibility.

### POST /api/v1/admin/orders/{id}/cancel
Admin cancel (wider permission than customer).
**Request**: `{ "reason" required }`
**Allowed states**: PENDING, PAID, CONFIRMED (không sau).

### POST /api/v1/admin/orders/{id}/refund
DELIVERED → REFUNDED, within 30 days.
**Request**: `{ "reason" required, "refundAmount"? }` (default: total - shippingFee)
**Response 200**

### GET /api/v1/admin/orders/export
Export CSV current filter.
**Response 200**: `text/csv` streaming

## Database Changes
(Uses tables from TS-CHECKOUT-PAYMENT)

Add column on `order`:
```sql
ALTER TABLE "order" ADD COLUMN refunded_amount BIGINT;
ALTER TABLE "order" ADD COLUMN refund_reason TEXT;
```

## Event Contracts

### Publish
- `order.order.confirmed`, `order.order.processing`, `order.order.shipped`, `order.order.delivered`, `order.order.cancelled`, `order.order.refunded`
- All with state_log + event envelope

### Internal API call: User Service
```
GET /api/v1/internal/users/{id}
Response: { id, email, fullName, phone, status }
```
(Add to User Service — simple endpoint, service-to-service auth)

## Sequence (Admin Ship Order)
Xem [architecture/02-sequence-diagrams.md section 7](../architecture/02-sequence-diagrams.md#7-order-state-sync-admin-ship-order).

## Class/Component Design

### Backend
```java
@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    @GetMapping public AdminOrderListResponse list(...);
    @GetMapping("/{id}") public OrderAdminDetail get(...);
    @PostMapping("/{id}/confirm") public OrderAdminDetail confirm(...);
    @PostMapping("/{id}/process") public OrderAdminDetail process(...);
    @PatchMapping("/{id}") public OrderAdminDetail update(@Valid @RequestBody AdminUpdateOrderRequest req);
    @PostMapping("/{id}/deliver") public OrderAdminDetail deliver(...);
    @PostMapping("/{id}/cancel") public OrderAdminDetail cancel(...);
    @PostMapping("/{id}/refund") public OrderAdminDetail refund(...);
    @GetMapping(value="/export", produces="text/csv") public void exportCsv(HttpServletResponse response, ...);
}

@Service
public class AdminOrderService {
    public Order confirm(UUID orderId, UUID adminId, String reason);
    public Order process(UUID orderId, UUID adminId);
    public Order markShipped(UUID orderId, UUID adminId, String trackingCode, String carrier);
    public Order markDelivered(UUID orderId, UUID adminId);
    public Order cancel(UUID orderId, UUID adminId, String reason);
    public Order refund(UUID orderId, UUID adminId, Long refundAmount, String reason);
}

@Service
public class OrderCsvExportService {
    public void streamCsv(OrderListQuery query, OutputStream output);
}

@Component
public class UserServiceClient {
    public UserBasicInfo getUser(UUID userId);
    public List<UserBasicInfo> getUsers(List<UUID> userIds); // batch
}
```

OrderStateMachine validation matrix:
```java
private static final Map<OrderState, Set<OrderState>> VALID_TRANSITIONS_ADMIN = Map.of(
    PENDING, Set.of(CONFIRMED, CANCELLED),
    PAID, Set.of(CONFIRMED, CANCELLED),
    CONFIRMED, Set.of(PROCESSING, CANCELLED),
    PROCESSING, Set.of(SHIPPED, CANCELLED),
    SHIPPED, Set.of(DELIVERED),
    DELIVERED, Set.of(REFUNDED)
);
```

### Frontend (Admin)
- Pages:
  - `/admin/orders`
  - `/admin/orders/{id}`
- Components:
  - `AdminOrderTable.tsx`
  - `OrderFilterBar.tsx` (state, date, search)
  - `OrderDetailActions.tsx` (dynamic per state)
  - `MarkShippedModal.tsx` (tracking input)
  - `RefundModal.tsx` (amount + reason)
  - `CustomerInfoCard.tsx`
  - `StateTimelineAdmin.tsx` (richer — shows actors)
- API: `lib/api/admin-order.api.ts`

## Implementation Steps

### Backend — Order Service
1. [ ] Migration add refunded_amount + refund_reason
2. [ ] Extend `OrderStateMachine` with admin matrix
3. [ ] `AdminOrderService` with all transition methods
4. [ ] `UserServiceClient` for customer lookup (batch)
5. [ ] `OrderCsvExportService` — streaming to avoid memory
6. [ ] `AdminOrderController` với PreAuthorize
7. [ ] Ensure all transitions publish event
8. [ ] Counts per state (cache 30s): `SELECT state, COUNT(*) FROM order GROUP BY state`
9. [ ] Unit tests: state machine admin transitions
10. [ ] Integration test: full admin flow from PENDING → DELIVERED + refund
11. [ ] Load test export CSV 10k orders

### Backend — User Service
1. [ ] Add `InternalUserController.getByIds(List<UUID>)` for batch lookup

### Backend — Product Service
1. [ ] Ensure `OrderConfirmedListener` (if COD) handles stock commit
2. [ ] Ensure `OrderCancelledListener` handles stock release when admin cancels PAID order

### Frontend
1. [ ] Admin order types
2. [ ] API client
3. [ ] `AdminOrderTable` với bulk select (backlog phase 2)
4. [ ] `OrderFilterBar`
5. [ ] Admin order list page với tabs + counts badge
6. [ ] Admin order detail với action panel dynamic
7. [ ] Shipped modal (tracking form)
8. [ ] Cancel modal (reason required)
9. [ ] Refund modal
10. [ ] Export button → download CSV
11. [ ] E2E admin order flow

## Test Strategy

### Unit
- Admin transition matrix (all combinations valid/invalid)
- CSV export format (headers, escape special chars)

### Integration
- Admin confirm COD → Product commits stock → verify reservedStock--
- Admin cancel PAID → Product releases stock
- Admin refund DELIVERED within 30d → state REFUNDED
- Admin refund > 30d → 400
- Export 1000 orders — streaming, no OOM

### E2E (Admin)
- Process order full flow: PENDING COD → CONFIRMED → PROCESSING → SHIPPED (with tracking) → DELIVERED
- Cancel at CONFIRMED → status + stock released
- Refund at DELIVERED → status

## Edge Cases

1. **Concurrent admin actions**: 2 admin cùng confirm 1 order → second gets 400. Dùng `FOR UPDATE`.
2. **Customer lookup fail** (User Service down): admin list/detail cached customer info cho graceful degrade, OR fail fast 503.
3. **COD commit stock vs VNPay commit**: COD commits on CONFIRMED (different event than VNPay's OrderPaid). Product Service listen both `OrderPaid` (VNPay path) và `OrderConfirmed` với paymentMethod=COD → commit.
4. **Refund amount > order total**: reject 400.
5. **Refund negative**: reject.
6. **Export huge data set**: stream CSV (don't build full list in memory). Limit 50k rows (error nếu vượt).
7. **Tracking code typo**: admin có thể sửa bằng PATCH shipped order (extra endpoint hoặc PATCH general). MVP: allow update trackingCode khi state=SHIPPED.
8. **Carrier unknown**: enum validate. Reject unknown carriers.
9. **Auto-complete scheduler + admin refund race**: DELIVERED > 7d → scheduler COMPLETED. Admin refund sau → 400 cannot refund COMPLETED. Solution: cho refund trong 30d bất kể state=DELIVERED or COMPLETED.
10. **Kafka publish fail**: DB committed but event lost. Use transactional outbox. MVP: accept risk, log error, manual replay via admin tool.
