---
phase: 18-storage-audit-cart-db
plan: 01
subsystem: database
tags: [jpa, flyway, postgres, cart, order-service, spring-data]

requires:
  - phase: 08-cart-order-persistence
    provides: OrderEntity/OrderItemEntity JPA pattern + @OneToMany cascade=ALL orphanRemoval=true

provides:
  - Flyway V4 migration: order_svc.carts (UNIQUE user_id) + order_svc.cart_items (UNIQUE(cart_id,product_id) + CHECK qty>0 + FK CASCADE)
  - CartEntity JPA class (replaces record stub) voi @OneToMany items, addItem/removeItem helpers
  - CartItemEntity JPA class voi @ManyToOne cart, equals/hashCode by id
  - CartDto + CartItemDto records (wire format)
  - CartMapper.toDto (entity -> DTO)
  - CartRepository (findByUserId + @EntityGraph items)
  - CartItemRepository (findByCartIdAndProductId + upsertAddQuantity native SQL)
  - Codebase compile-clean: 0 reference InMemoryCartRepository + 0 reference CartUpsertRequest

affects:
  - 18-02 (CartCrudService + CartController implementation - depends on repos + entities)
  - 18-03 (FE cart.ts rewrite - depends on BE endpoints that Plan 02 will build)
  - 20 (Phase 20 Coupon - depends on cart total calculation)

tech-stack:
  added: []
  patterns:
    - "CartEntity/CartItemEntity follow OrderEntity/OrderItemEntity pattern from Phase 8 (cascade=ALL, orphanRemoval=true, LAZY fetch)"
    - "CartItemEntity equals/hashCode by id only -- tranh LazyInitializationException trong HashSet/HashMap"
    - "CartItemRepository.upsertAddQuantity: native SQL ON CONFLICT DO UPDATE -- idempotent ADD semantics"
    - "@EntityGraph(attributePaths='items') tren findByUserId -- fetch-join tranh LazyInit ngoai transaction"

key-files:
  created:
    - sources/backend/order-service/src/main/resources/db/migration/V4__add_cart_tables.sql
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartItemEntity.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartDto.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartItemDto.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartMapper.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/CartRepository.java
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/CartItemRepository.java
  modified:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/CartEntity.java (record stub -> JPA class)
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java (xoa cart legacy)
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/CartController.java (reset stub cho Plan 02)
  deleted:
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/InMemoryCartRepository.java

key-decisions:
  - "CartEntity la JPA @Entity class (khong phai record) de Hibernate proxy hoat dong dung"
  - "CartItemEntity.equals/hashCode dua tren id only (khong lazy traverse items) -- tranh infinite loop"
  - "upsertAddQuantity dung native SQL ON CONFLICT DO UPDATE de atomic idempotent ADD (D-05)"
  - "CartController reset thanh stub (Rule 3 blocking): CartController cu phu thuoc CartUpsertRequest da bi xoa -- Plan 02 se implement lai hoan chinh"
  - "KHONG them cart_items.unit_price_at_add -- cart hien thi live price tu product-svc, snapshot chi khi tao order"

patterns-established:
  - "Native upsert pattern: @Modifying @Query nativeQuery=true + ON CONFLICT DO UPDATE voi schema-qualified table"
  - "Entity factory method: CartEntity.create(userId), CartItemEntity.create(cart, productId, quantity)"
  - "@EntityGraph tren repository method de fetch eager khi can thiet ma khong doi default fetch type"

requirements-completed:
  - STORE-02

duration: 25min
completed: 2026-05-02
---

# Phase 18 Plan 01: Cart DB Foundation Summary

**Flyway V4 schema (carts + cart_items voi UNIQUE/CHECK/FK constraints) + JPA entities CartEntity/CartItemEntity theo Phase 8 pattern + CartRepository/CartItemRepository voi native ON CONFLICT upsert + xoa hoan toan InMemoryCartRepository stub**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-02T15:10:00Z
- **Completed:** 2026-05-02T15:35:00Z
- **Tasks:** 3
- **Files modified:** 10 (7 created/modified + 1 deleted + 2 reset/stub)

## Accomplishments

- Tao Flyway V4 migration voi DDL spec chinh xac (UNIQUE user_id, UNIQUE(cart_id,product_id), CHECK qty>0, FK ON DELETE CASCADE)
- Replace CartEntity record stub bang JPA @Entity class voi @OneToMany(cascade=ALL, orphanRemoval=true) LAZY items
- Tao CartItemEntity moi (NEW) voi @ManyToOne, equals/hashCode by id, setQuantity helper
- Tao CartDto + CartItemDto records + CartMapper.toDto cho controller layer
- Tao CartRepository (findByUserId + @EntityGraph) + CartItemRepository (upsertAddQuantity native SQL)
- Xoa hoan toan InMemoryCartRepository.java + 0 reference con lai trong codebase
- OrderCrudService compile clean: xoa import/field/constructor/5 methods/cartComparator/CartUpsertRequest record
- mvn compile exits 0

## Task Commits

1. **Task 1: Flyway V4 + JPA entities + DTOs + Mapper** - `56cf03a` (feat)
2. **Task 2: CartRepository + CartItemRepository** - `504d689` (feat)
3. **Task 3: Xoa InMemoryCartRepository + cleanup OrderCrudService** - `510c66a` (feat)

## Files Created/Modified

- `V4__add_cart_tables.sql` - DDL tao order_svc.carts + order_svc.cart_items voi UNIQUE/CHECK/FK
- `CartEntity.java` - JPA @Entity class (replace record stub): @OneToMany cascade=ALL orphanRemoval=true
- `CartItemEntity.java` (NEW) - @ManyToOne LAZY, equals/hashCode by id
- `CartDto.java` (NEW) - record wire format: id + userId + List<CartItemDto>
- `CartItemDto.java` (NEW) - record wire format: id + productId + quantity
- `CartMapper.java` (NEW) - CartMapper.toDto(CartEntity) -> CartDto stream mapping
- `CartRepository.java` (NEW) - findByUserId(@EntityGraph items)
- `CartItemRepository.java` (NEW) - findByCartIdAndProductId + upsertAddQuantity native SQL
- `OrderCrudService.java` - xoa cart legacy (import/field/5 methods/cartComparator/CartUpsertRequest)
- `CartController.java` - reset thanh stub (Plan 02 se implement lai)
- `InMemoryCartRepository.java` - DA XOA

## Decisions Made

- CartController cu (CRUD cart qua OrderCrudService.CartUpsertRequest) bi reset thanh stub vi CartUpsertRequest da bi xoa trong Task 3. Khong the giu file cu vi compile se fail. Plan 02 se implement lai CartController hoan chinh voi CartCrudService moi.
- KHONG them unit_price_at_add snapshot vao cart_items -- cart doc live price tu product-svc, chi snapshot khi checkout tao order (nhat quan voi Phase 8 OrderItemEntity.unitPrice).
- CartItemEntity.equals/hashCode dua tren id field only (khong traverse cart/items collection) -- tranh LazyInitializationException khi entity nam trong Set.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] CartController phu thuoc CartUpsertRequest da bi xoa**
- **Found during:** Task 3 (xoa cart legacy) -- phat hien khi compile sau Task 1
- **Issue:** CartController.java import `OrderCrudService.CartUpsertRequest` va goi 5 cart legacy methods (listCarts, getCart, createCart, updateCart, deleteCart) -- tat ca da bi xoa khoi OrderCrudService trong Task 3. compile → 5 errors.
- **Fix:** Reset CartController.java thanh stub class rong voi comment giai thich. Plan 02 se replace hoan toan.
- **Files modified:** `CartController.java`
- **Verification:** mvn compile exits 0 sau khi stub
- **Committed in:** `510c66a` (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (Rule 3 Blocking)
**Impact on plan:** CartController stub la can thiet -- file cu khong the compile khi CartUpsertRequest bi xoa. Plan 02 da du du de implement lai endpoint dung cach voi CartCrudService.

## Issues Encountered

- Maven khong co trong PATH default -- su dung binary tai `~/.m2/wrapper/dists/apache-maven-3.9.12/6068d197/bin/mvn` (da tim duoc tu m2 wrapper cache).
- Compile sau Task 1 fail vi CartController con reference CartUpsertRequest -- phai xu ly cung voi Task 3 thay vi sau.

## Known Stubs

- `CartController.java` -- intentionally empty stub. Plan 02 se implement lai voi CartCrudService + CartRepository JPA (GET/POST/PATCH/DELETE cart endpoints D-04..D-08).

## Threat Surface Scan

Khong co surface moi ngoai plan threat_model:
- V4 DDL dung IF NOT EXISTS (T-18-04 mitigated)
- CHECK (quantity > 0) enforce DB layer (T-18-01 mitigated)
- UNIQUE (cart_id, product_id) enforce race-safe insert (T-18-02 mitigated)

## Next Phase Readiness

- Plan 02 (CartCrudService + CartController) co the bat dau ngay: CartEntity, CartItemEntity, CartRepository, CartItemRepository da san sang
- Caller cua upsertAddQuantity phai flush + clear EntityManager sau native SQL (doc trong CartItemRepository Javadoc)
- V4 migration se chay auto khi order-service khoi dong lan dau voi Postgres
- CartController.java la stub -- Plan 02 phai replace hoan toan (khong merge, replace)

---
*Phase: 18-storage-audit-cart-db*
*Completed: 2026-05-02*
