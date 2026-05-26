---
phase: 18-storage-audit-cart-db
plan: 02
subsystem: backend-cart-service
tags: [rest-controller, cart, order-service, spring-mvc, stock-validation]

requires:
  - phase: 18-01
    provides: CartEntity/CartItemEntity/CartRepository/CartItemRepository/CartDto/CartMapper (Plan 01 outputs)
  - phase: 08-cart-order-persistence
    provides: StockShortageException 409 pattern + X-User-Id header pattern + GlobalExceptionHandler

provides:
  - CartCrudService: 6 public @Transactional methods (getOrCreate, add, set, remove, clear, merge)
  - CartController: 6 REST endpoints mount tại /orders/cart (gateway-rewrite-aware)
  - Stock validation reuse StockShortageException 409 STOCK_SHORTAGE (Phase 8 pattern)
  - Merge flow clamp-to-stock (no-throw) cho batch login merge idempotent

affects:
  - 18-03 (FE cart.ts rewrite - BE endpoints san sang, Wave 3 co the bat dau)
  - 20 (Phase 20 Coupon - cart total calculation dependency)

tech-stack:
  added: []
  patterns:
    - "CartCrudService: flush+clear sau upsertAddQuantity native SQL de tranh JPA 1st-level cache stale read"
    - "getOrCreateEntity: catch DataIntegrityViolationException → retry findByUserId (race condition Pattern 3 from research)"
    - "validateStockOrThrow: best-effort (log+continue) khi product-svc unreachable — nhat quan voi Phase 8 OrderCrudService"
    - "clampToStock: no-throw variant cho merge flow (batch operation, 1 item fail khong block toan bo)"
    - "CartController: X-User-Id required=false (Spring 400 ugly) → 401 tu service layer voi message ro rang"
    - "ApiResponse.of(200, msg, data) envelope nhat quan voi OrderController — KHONG dung ApiResponse.ok() (khong ton tai)"

key-files:
  created:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/CartCrudService.java
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CartController.java (replace stub hoan toan)

key-decisions:
  - "ApiResponse.ok() khong ton tai trong codebase — dung ApiResponse.of(200, message, data) thay the (Rule 1 auto-fix)"
  - "CartController import tu com.ptit.htpt.orderservice.api.ApiResponse (khong phai .web.ApiResponse)"
  - "X-User-Id required=false de controller tra 401 voi message ro rang thay vi Spring auto 400"
  - "mergeFromGuest dung clamp (no-throw) vi merge la batch flow login, throw 1 item se block toan bo"
  - "flush+clear BẮT BUỘC sau upsertAddQuantity native SQL de findByUserId tiep theo thay du lieu moi"

metrics:
  duration: ~15min
  completed: 2026-05-02T15:20:00Z
  tasks: 2
  files_modified: 2
---

# Phase 18 Plan 02: CartCrudService + CartController Summary

**CartCrudService 6 public methods (getOrCreate/add/set/remove/clear/merge) + CartController 6 endpoints mount tại /orders/cart theo gateway-rewrite mapping, reuse StockShortageException 409 từ Phase 8**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-05-02T15:20:00Z
- **Tasks:** 2
- **Files modified:** 2 (1 created mới + 1 replace stub)

## Accomplishments

- Implement `CartCrudService.java` với 6 @Transactional public methods:
  - `getOrCreateCart(userId)` — lazy-create empty cart row (D-04)
  - `addItem(userId, req)` — idempotent ADD qua `upsertAddQuantity` native SQL (D-05)
  - `setItemQuantity(userId, productId, req)` — SET absolute; quantity<=0 → DELETE alias (D-06)
  - `removeItem(userId, productId)` — remove single item (D-07)
  - `clearItems(userId)` — xóa toàn bộ items, giữ cart row (D-07)
  - `mergeFromGuest(userId, req)` — idempotent merge guest cart, clamp-to-stock (D-08)
- Implement `CartController.java` với 6 endpoints đúng path gateway-rewrite:
  - `GET /orders/cart` → `getOrCreateCart`
  - `POST /orders/cart/items` → `addItem`
  - `PATCH /orders/cart/items/{productId}` → `setItemQuantity`
  - `DELETE /orders/cart/items/{productId}` → `removeItem`
  - `DELETE /orders/cart` → `clearItems`
  - `POST /orders/cart/merge` → `mergeFromGuest`
- `mvn compile -DskipTests` exits 0 cho cả 2 tasks

## 6 Endpoints + Gateway Path Mapping

| FE gọi | BE controller (gateway rewrite) | Method | Service |
|--------|--------------------------------|--------|---------|
| `GET /api/orders/cart` | `GET /orders/cart` | `getCart` | `getOrCreateCart` |
| `POST /api/orders/cart/items` | `POST /orders/cart/items` | `addItem` | `addItem` |
| `PATCH /api/orders/cart/items/{id}` | `PATCH /orders/cart/items/{id}` | `setItem` | `setItemQuantity` |
| `DELETE /api/orders/cart/items/{id}` | `DELETE /orders/cart/items/{id}` | `removeItem` | `removeItem` |
| `DELETE /api/orders/cart` | `DELETE /orders/cart` | `clearCart` | `clearItems` |
| `POST /api/orders/cart/merge` | `POST /orders/cart/merge` | `merge` | `mergeFromGuest` |

Gateway route `order-service` predicate `Path=/api/orders/**` với filter `RewritePath=/api/orders/(?<seg>.*), /orders/${seg}` đã có sẵn trong `api-gateway/application.yml` — KHÔNG cần thêm route mới.

## Stock Validation Strategy

| Flow | Method | Behavior khi stock fail |
|------|--------|------------------------|
| addItem | `validateStockOrThrow` | 409 STOCK_SHORTAGE (StockShortageException) |
| setItemQuantity | `validateStockOrThrow` | 409 STOCK_SHORTAGE |
| mergeFromGuest | `clampToStock` | Clamp về available (no-throw) — 1 item fail không block merge |
| product-svc unreachable | cả 2 | log.warn + continue (best-effort MVP) |

## Race Condition Handling

`getOrCreateEntity` dùng pattern:
1. `findByUserId` → nếu đã tồn tại, trả ngay
2. `save(CartEntity.create(userId))` → nếu race INSERT cùng lúc → `DataIntegrityViolationException`
3. Catch exception → retry `findByUserId` → return cart đã được create bởi concurrent thread

## flush+clear Pattern Sau Native Upsert

`upsertAddQuantity` là native SQL → bypass JPA 1st-level cache. Nếu sau đó query lại `findByUserId` mà không flush+clear, Hibernate trả entity từ cache (không thấy quantity mới từ native upsert).

**Pattern áp dụng tại:**
- `addItem`: sau `upsertAddQuantity` → `entityManager.flush(); entityManager.clear();`
- `mergeFromGuest`: sau vòng lặp upsert → `entityManager.flush(); entityManager.clear();`

## Task Commits

1. **Task 1: CartCrudService** - `67b083c` (feat) — 226 lines, 6 public methods + helpers + request records
2. **Task 2: CartController** - `5699184` (feat) — 72 lines, 6 endpoints + ApiResponse envelope

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ApiResponse.ok() không tồn tại trong codebase**
- **Found during:** Task 2 (implement CartController)
- **Issue:** Plan skeleton dùng `ApiResponse.ok(cartService.getOrCreateCart(userId))` nhưng `ApiResponse` record trong `com.ptit.htpt.orderservice.api` chỉ có factory method `ApiResponse.of(int status, String message, T data)`. Không có `.ok()` method.
- **Fix:** Thay tất cả `ApiResponse.ok(...)` → `ApiResponse.of(200, "<message>", ...)` nất quán với pattern OrderController hiện tại.
- **Files modified:** `CartController.java`
- **Verification:** mvn compile exits 0
- **Committed in:** `5699184` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 Bug — API mismatch giữa plan skeleton và codebase thực tế)

## Known Stubs

Không có stub. Cả 2 files implement đầy đủ logic thực.

## Threat Surface Scan

Không có surface mới ngoài plan threat_model:
- T-18-05: `requireUserId()` chặn missing X-User-Id → 401 (mitigated)
- T-18-06: `upsertAddQuantity` native ON CONFLICT atomic single-statement (mitigated)
- T-18-07: `validateStockOrThrow` gọi product-svc live stock mỗi mutation (mitigated)
- T-18-08: Accept — best-effort MVP, race window non-locking; checkout re-validate là gate cuối
- T-18-09: UNIQUE(cart_id, product_id) → upsert, không tạo row mới (mitigated)

## Next Phase Readiness

- Plan 03 (FE cart.ts rewrite + CartContext refactor) có thể bắt đầu ngay
- BE endpoints mount đúng path, sẵn sàng nhận request từ FE qua gateway
- Manual smoke test (sau khi docker-compose up):
  - `GET /orders/cart` thiếu X-User-Id → 401 "Missing X-User-Id session header"
  - `GET /orders/cart` với X-User-Id → 200 `{"data":{"items":[]}}` (lazy-create cart)
  - `POST /orders/cart/items` idempotent: gọi 2 lần cùng productId+quantity=1 → DB chỉ có 1 row với quantity=2

---
*Phase: 18-storage-audit-cart-db*
*Completed: 2026-05-02*
