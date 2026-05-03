# Phase 18: Kiểm Toán Storage + Cart→DB - Research

**Researched:** 2026-05-02
**Domain:** Spring Boot JPA cart persistence + React Query write-through + FE storage audit
**Confidence:** HIGH (codebase đã verify trực tiếp, pattern reuse từ Phase 5/6/8)
**Language:** Vietnamese (output, comments, error messages — code identifiers giữ EN)

---

## Summary

Phase 18 thực hiện 3 việc song song: (1) audit toàn bộ FE storage và phân loại 3 nhóm, (2) migrate cart từ localStorage sang DB persist server-side với schema `carts` + `cart_items` (UNIQUE per `user_id`, UNIQUE per `(cart_id, product_id)`), (3) implement guest→user merge idempotent qua endpoint `POST /api/orders/cart/merge` với `INSERT ... ON CONFLICT DO UPDATE`.

CONTEXT.md đã lock toàn bộ technical decisions: schema (D-01), JPA mapping (D-02), endpoint shape (D-04..D-08), routing (D-09..D-12), merge flow (D-13..D-15), stock validation (D-16..D-17), audit policy (D-18..D-21). Research này không re-decide — chỉ document chi tiết implementation cho planner: JPA pitfalls (LazyInitializationException, equals/hashCode), idempotent upsert với native SQL (PostgreSQL `ON CONFLICT` đã verify hỗ trợ trên schema hiện tại), React Query patterns (`staleTime`, `invalidateQueries`), AuthProvider injection points, gateway routing (đã có wildcard `order-service` cover `/api/orders/cart/**`).

**Primary recommendation:** Reuse y hệt `OrderEntity ↔ OrderItemEntity` pattern (Phase 8 D-07) cho `CartEntity ↔ CartItemEntity`; dùng native SQL upsert qua `@Modifying @Query` cho `POST /cart/items` và `/cart/merge` (idempotent + race-safe in single statement); FE viết wrapper `services/cart.ts` route theo `getAccessToken()` → guest dùng localStorage namespace `_localCart()`, user dùng API + React Query `['cart']` key với `invalidateQueries` sau mỗi mutation.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Schema (D-01..D-03):**
- Flyway `V4__add_cart_tables.sql` order_svc: `carts (id PK, user_id UNIQUE)` + `cart_items (id PK, cart_id FK ON DELETE CASCADE, product_id, quantity CHECK > 0, UNIQUE(cart_id, product_id))`.
- Replace `CartEntity` stub cũ + `InMemoryCartRepository` hoàn toàn — KHÔNG backward-compatible.
- `user_id` lookup từ JWT `sub` claim qua gateway header forwarding (cùng pattern `OrderCrudService` Phase 8). KHÔNG có cart guest server-side.

**API endpoints (D-04..D-08):**
- `GET /api/orders/cart` → `CartDto { items: CartItemDto[] }`. Lazy-create empty cart row nếu chưa có.
- `POST /api/orders/cart/items` body `{productId, quantity}` → idempotent ADD: `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity`. Validate stock qua product-svc → 409 STOCK_SHORTAGE nếu fail.
- `PATCH /api/orders/cart/items/{productId}` body `{quantity}` → SET absolute. Validate stock. `quantity <= 0` → DELETE (alias).
- `DELETE /api/orders/cart/items/{productId}` → remove single. `DELETE /api/orders/cart` → clear items giữ cart row.
- `POST /api/orders/cart/merge` body `{items: [{productId, quantity}]}` → idempotent merge cho login flow. Trả `CartDto` mới.

**Frontend (D-09..D-12):**
- `services/cart.ts` chia 2 backend: guest (no token) giữ localStorage logic hiện tại (namespace `_localCart()` internal), user (có token) gọi API.
- Wrapper kiểm `getAccessToken()` route — caller không biết.
- Write-through mỗi mutation, KHÔNG debounce, KHÔNG optimistic UI cho MVP. UX trade-off acceptable (~100-300ms/click).
- Cart page + header badge dùng React Query `useQuery(['cart'])` + `useMutation` + `invalidateQueries(['cart'])`. Existing `cart:change` event giữ fire cho guest path để giữ compat.

**Merge flow (D-13..D-15):**
- `AuthProvider.login(user)` sau set token, trước `router.push`: đọc `readCart()` localStorage → nếu non-empty `POST /merge` → success: `clearCart()` localStorage.
- Merge fail: log error, KHÔNG block login, KHÔNG clear localStorage, toast warning "Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại".
- `AuthProvider.logout()`: `clearCart()` localStorage tránh leak sang guest session tiếp theo cùng browser.

**Stock validation (D-16..D-17):**
- Validate cả mutation cart (POST/PATCH cart items → 409 STOCK_SHORTAGE) lẫn checkout (đã có Phase 8 D-04, giữ nguyên).
- Cart KHÔNG lưu `stock` snapshot — fetch live mỗi mutation. Trade-off: +1 service call/mutation, đổi không có stale stock.

**Storage audit (D-18..D-21):**
- Grep toàn `sources/frontend/src` cho `localStorage|sessionStorage`. Verified preview: `cart`, `userProfile`, `accessToken`, `refreshToken`, `auth_present` cookie.
- Viết `18-SUMMARY.md` table classify: source, purpose, classification (DB-migrated / UI-kept / auth-deferred), reason.
- STORE-03 fold-in: nếu phát hiện wishlist/recently-viewed/search-history → migrate trong phase này. Nếu không có → đóng STORE-03 với note "no additional leaks found beyond cart".
- Auth tokens (`accessToken`, `refreshToken`): ghi report "deferred to STORE-04 (visible-first defer)". KHÔNG migrate, KHÔNG sửa logic.
- `userProfile` localStorage giữ với note "UI session cache — DB là source of truth via user-svc Phase 10".

### Claude's Discretion

- JPA cascade/fetch type chi tiết cho `CartEntity ↔ CartItemEntity` — planner chọn (LAZY default).
- React Query `staleTime` cho cart query — planner chọn (suggest 0 hoặc Infinity với manual invalidate).
- Error message vi text cho 409 STOCK_SHORTAGE từ cart endpoint — planner soạn.
- Có cần `cart_items.unit_price_at_add` snapshot hay không — default KHÔNG (cart hiển thị live price từ product-svc, chỉ snapshot khi tạo order).
- Endpoint URL chính xác (`/api/orders/cart` vs `/api/cart`) — planner align với gateway routes hiện có.

### Deferred Ideas (OUT OF SCOPE)

- STORE-04 — Auth-token migration localStorage → httpOnly cookie (visible-first defer).
- Anonymous server-side cart với session_id cookie (guest localStorage đủ MVP).
- Optimistic UI + debounced sync cho cart mutations.
- Multi-device cart conflict resolution (last-write-wins acceptable).
- Offline cart support (PWA scope).
- Cart cleanup job cho stale cart_items (product bị soft-delete) — planner có thể đề xuất `JOIN` filter ở GET endpoint.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| STORE-01 | Audit toàn FE codebase grep `localStorage`/`sessionStorage`, classify 3 nhóm, output báo cáo SUMMARY.md | §Storage Audit Methodology, §Code Examples (audit table format), §Don't Hand-Roll (no scanner needed — grep + manual classification) |
| STORE-02 | Migrate cart `localStorage['cart']` → DB. Order-svc V4 `carts` + `cart_items`. FE `services/cart.ts` API calls. Idempotent merge endpoint `ON CONFLICT DO UPDATE` khi guest login | §Standard Stack (JPA + Flyway + React Query), §Architecture Patterns 1-5, §Common Pitfalls 1-7, §Code Examples |
| STORE-03 | Migrate user-data leak khác phát hiện trong STORE-01 (recently viewed / search history / wishlist nếu có) sang DB hoặc giải thích lý do giữ | §Storage Audit Methodology (fold-in policy D-19); preview cho thấy chỉ có `cart`+`userProfile`+token keys → expected: STORE-03 đóng với note "no additional leaks found" |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Cart persistence (user logged-in) | API/Backend (order-svc) | Database (PostgreSQL `order_svc.carts/cart_items`) | DB là source of truth khi đã có user_id; cross-device sync |
| Cart persistence (guest) | Browser (localStorage) | — | KHÔNG cần server-side guest cart cho MVP (D-15 lock); preserve current behavior |
| Cart routing logic (guest vs user) | Frontend Server (Next.js client component) | — | `services/cart.ts` wrapper kiểm `getAccessToken()` → branch behavior |
| Cart UI state sync | Browser (React Query cache) | API (re-fetch on invalidate) | React Query đã có sẵn từ Phase 7+; cache key `['cart']` |
| Stock validation (cart mutation) | API/Backend (order-svc → product-svc qua gateway) | — | Cùng pattern Phase 8 D-04: order-svc gọi `GET /api/products/{id}` lấy stock |
| Idempotent merge guest→user | API/Backend (PostgreSQL native upsert) | — | `INSERT ... ON CONFLICT DO UPDATE` atomic single-statement, race-safe |
| Auth state hydration (`userProfile` cache) | Browser (localStorage) | API (user-svc Phase 10 source of truth) | UI session cache — KHÔNG phải data leak |
| Auth tokens storage | Browser (localStorage) | — | Phase 6 D-11 tradeoff accepted; STORE-04 deferred |
| Storage audit reporting | Manual (developer + grep) | — | One-shot deliverable trong SUMMARY.md, KHÔNG cần tooling |

## Standard Stack

### Core (đã có sẵn — KHÔNG install gì mới)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.3.x | order-service runtime | Đã dùng từ v1.0 [VERIFIED: pom.xml] |
| Spring Data JPA + Hibernate | 6.x (BOM Boot 3.3) | `CartEntity`, `CartItemEntity` mapping + repo | Phase 5/8 đã set pattern [VERIFIED: codebase] |
| Flyway | 9.x (BOM Boot 3.3) | `V4__add_cart_tables.sql` migration | Per-schema pattern đã chuẩn [VERIFIED: V1/V2 order_svc] |
| PostgreSQL | 15+ | `order_svc` schema | `ON CONFLICT (col, col) DO UPDATE` syntax HIGH support [CITED: postgresql.org/docs/15/sql-insert.html#SQL-ON-CONFLICT] |
| React Query (`@tanstack/react-query`) | 5.x | FE cart query/mutation, cache invalidation | Đã có từ Phase 7+ [VERIFIED: package.json — note: planner verify exact version trong package.json] |
| Next.js | 14 App Router | FE shell | [VERIFIED: codebase] |

### Supporting (đã có sẵn)

| Library | Purpose | When to Use |
|---------|---------|-------------|
| `httpGet`/`httpPost`/`httpPatch`/`httpDelete` từ `services/http.ts` | Auth header tự attach Bearer token, ApiResponse envelope unwrap, ApiError throw | Mọi cart API call FE |
| `RestTemplate` (Spring) | order-svc → product-svc stock fetch qua gateway | Reuse y hệt `OrderCrudService.validateStockOrThrow` Phase 8 |
| `StockShortageException` + `GlobalExceptionHandler` | 409 STOCK_SHORTAGE response shape | Reuse — KHÔNG tạo error code mới [VERIFIED: exception/StockShortageException.java] |
| Jackson ObjectMapper | DTO serialization | Default Spring auto-config |
| `userProfile` localStorage hydration pattern | AuthProvider lazy initializer | KHÔNG sửa pattern hiện tại [VERIFIED: AuthProvider.tsx] |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `INSERT ... ON CONFLICT DO UPDATE` (native SQL) | JPA-managed find→update/create | Native SQL atomic + race-safe single statement; JPA pattern cần `@Lock` hoặc transaction retry để tránh race. Decision: dùng native SQL cho `POST /items` + `/merge`, JPA standard cho `GET`/`PATCH`/`DELETE`. |
| React Query cache | Redux/Zustand | React Query đã có; redundant store cho cart không cần thiết [ASSUMED — planner xác minh Redux/Zustand chưa được dùng trong project] |
| Optimistic UI với rollback | Write-through (chosen) | User locked write-through MVP — UX trade ~200ms latency để code đơn giản hơn |
| Server-side guest cart với session cookie | localStorage guest path (chosen) | User locked guest = localStorage; thêm session cookie + anonymous cart = scope creep |

**Installation:** Không cần `npm install` hay `pom.xml` thay đổi cho phase này — toàn bộ stack đã có.

**Version verification:** Planner kiểm `sources/frontend/package.json` xác nhận `@tanstack/react-query` version (suggest >=5.x cho `useMutation` + `invalidateQueries` API hiện đại); `sources/backend/order-service/pom.xml` xác nhận Spring Boot version. KHÔNG cần `npm view` vì không thêm dependency.

## Architecture Patterns

### System Architecture Diagram

```
[Guest Browser]
   │
   │ readCart/writeCart (localStorage['cart'])
   ▼
┌──────────────────────┐
│ services/cart.ts     │── getAccessToken() ──┐
│ (FE wrapper)         │                      │
└──────────────────────┘                      │
   │ guest path                               │ user path
   │                                          ▼
   │                                ┌─────────────────────┐
   │                                │ React Query         │
   │                                │ ['cart'] cache      │
   │                                └─────────────────────┘
   │                                          │
   │                                          ▼
   │                                ┌─────────────────────┐
   │                                │ httpGet/Post/...    │── Authorization: Bearer ──▶ [api-gateway:8080]
   │                                └─────────────────────┘                                    │
   │                                                                                            ▼
   │                                                                          ┌─────────────────────────┐
   │                                                                          │ order-service           │
   │                                                                          │ CartController          │
   │                                                                          │   (JWT sub → user_id)   │
   │                                                                          └─────────────────────────┘
   │                                                                                    │
   │                                                                          ┌─────────┴─────────┐
   │                                                                          ▼                   ▼
   │                                                                   CartCrudService    Stock validate ──▶ product-svc
   │                                                                          │                    (qua gateway)
   │                                                                          ▼
   │                                                                   CartRepository (JPA)
   │                                                                          │
   │                                                                          ▼
   │                                                                   PostgreSQL
   │                                                                   order_svc.carts
   │                                                                   order_svc.cart_items
   │                                                                   (UNIQUE cart_id+product_id)
   │
   │ AuthProvider.login()
   ▼
   readCart() → POST /api/orders/cart/merge → success: clearCart() localStorage
                                            → fail: log + toast (KHÔNG block login)

   AuthProvider.logout()
   ▼
   clearTokens() → clearCart() localStorage
```

### Component Responsibilities

| File | Responsibility | Action |
|------|---------------|--------|
| `sources/backend/order-service/src/main/resources/db/migration/V4__add_cart_tables.sql` | Tạo `carts` + `cart_items` schema | NEW |
| `domain/CartEntity.java` | JPA entity: id, user_id UNIQUE, @OneToMany items | REPLACE (xóa stub cũ record) |
| `domain/CartItemEntity.java` | JPA entity: id, cart_id @ManyToOne, product_id, quantity | NEW |
| `domain/CartDto.java` + `CartItemDto.java` | Wire format trả FE | NEW |
| `domain/CartMapper.java` | Entity → DTO | NEW |
| `repository/CartRepository.java` | `JpaRepository<CartEntity, String>` + `findByUserId` + native upsert | NEW |
| `repository/CartItemRepository.java` | `JpaRepository<CartItemEntity, String>` + native upsert query | NEW |
| `repository/InMemoryCartRepository.java` | Stub cũ | DELETE |
| `service/CartCrudService.java` | Business logic: getOrCreate, addItem (upsert), setItem, removeItem, clear, merge, validateStock | NEW |
| `web/CartController.java` | REST endpoints `/cart` + `/cart/items/**` + `/cart/merge` | NEW |
| `service/OrderCrudService.java` | Hiện reference `InMemoryCartRepository` + `CartUpsertRequest` — phải dọn các method `listCarts`/`getCart`/`createCart`/`updateCart`/`deleteCart` không còn dùng | EDIT (xóa cart-related methods cũ — không có endpoint nào gọi chúng theo audit) |
| `sources/frontend/src/services/cart.ts` | Wrapper guest vs user routing | REWRITE (giữ guest logic là `_localCart()` namespace) |
| `sources/frontend/src/hooks/useCart.ts` (NEW hoặc inline) | React Query `useQuery(['cart'])` + mutations | NEW (planner quyết — có thể inline trong page) |
| `sources/frontend/src/providers/AuthProvider.tsx` | Inject merge call vào login(), clearCart() vào logout() | EDIT |
| `sources/frontend/src/app/cart/page.tsx` | Switch sync read → React Query | EDIT |
| `sources/frontend/src/app/checkout/page.tsx` | Cart fetch async khi user logged-in | EDIT |
| `sources/frontend/src/components/layout/Header.tsx` (badge) | Subscribe `['cart']` query | EDIT |

### Recommended Project Structure

```
sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/
├── domain/
│   ├── CartEntity.java          (REPLACE)
│   ├── CartItemEntity.java      (NEW)
│   ├── CartDto.java             (NEW)
│   ├── CartItemDto.java         (NEW)
│   └── CartMapper.java          (NEW)
├── repository/
│   ├── CartRepository.java      (NEW)
│   ├── CartItemRepository.java  (NEW)
│   └── InMemoryCartRepository.java  (DELETE)
├── service/
│   ├── CartCrudService.java     (NEW)
│   └── OrderCrudService.java    (EDIT — remove cart legacy methods)
└── web/
    └── CartController.java      (NEW)

sources/backend/order-service/src/main/resources/db/migration/
└── V4__add_cart_tables.sql      (NEW)

sources/frontend/src/
├── services/cart.ts             (REWRITE — guest+user wrapper)
├── providers/AuthProvider.tsx   (EDIT — login merge, logout clear)
├── app/cart/page.tsx            (EDIT — React Query)
├── app/checkout/page.tsx        (EDIT — async cart fetch)
└── components/layout/Header.tsx (EDIT — query subscribe)
```

### Pattern 1: JPA `@OneToMany` Cart ↔ CartItem (reuse Phase 8 OrderItem pattern)

**What:** Aggregate root `CartEntity` owns `CartItemEntity` collection. `cascade=ALL` + `orphanRemoval=true` để xóa cart row → cascade xóa items, và remove item khỏi list → DELETE row.
**When to use:** Mọi entity parent-child trong codebase này [VERIFIED: OrderEntity ↔ OrderItemEntity Phase 8 D-07].

```java
// Source: codebase pattern Phase 8 + Hibernate 6 docs
@Entity
@Table(name = "carts", schema = "order_svc")
public class CartEntity {
  @Id @Column(length = 36) String id;
  @Column(name = "user_id", length = 36, nullable = false, unique = true) String userId;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true,
             fetch = FetchType.LAZY)
  private List<CartItemEntity> items = new ArrayList<>();

  Instant createdAt;
  Instant updatedAt;

  // Helper to maintain bidirectional integrity (CRITICAL — see Pitfall 2)
  public void addItem(CartItemEntity item) {
    items.add(item);
    item.setCart(this);
  }

  public void removeItem(CartItemEntity item) {
    items.remove(item);
    item.setCart(null);
  }
}

@Entity
@Table(name = "cart_items", schema = "order_svc",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "product_id"}))
public class CartItemEntity {
  @Id @Column(length = 36) String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cart_id", nullable = false)
  private CartEntity cart;

  @Column(name = "product_id", length = 36, nullable = false) String productId;
  @Column(nullable = false) int quantity;

  // equals/hashCode based on `id` only — NOT on collection-fetched fields (Pitfall 2)
}
```

### Pattern 2: Native SQL Idempotent Upsert (PostgreSQL `ON CONFLICT`)

**What:** Single-statement atomic upsert — PostgreSQL detect `(cart_id, product_id)` unique constraint conflict và update thay vì throw.
**When to use:** `POST /cart/items` (add quantity) và `POST /cart/merge` (per-item bulk add). KHÔNG dùng cho `PATCH` (set absolute) — `PATCH` dùng JPA standard find+update.

```java
// Source: PostgreSQL 15 docs + Spring Data JPA @Modifying patterns [CITED: postgresql.org/docs/15/sql-insert.html]
public interface CartItemRepository extends JpaRepository<CartItemEntity, String> {

  @Modifying
  @Query(value = """
      INSERT INTO order_svc.cart_items (id, cart_id, product_id, quantity, created_at, updated_at)
      VALUES (:id, :cartId, :productId, :quantity, now(), now())
      ON CONFLICT (cart_id, product_id)
      DO UPDATE SET
        quantity = order_svc.cart_items.quantity + EXCLUDED.quantity,
        updated_at = now()
      """, nativeQuery = true)
  int upsertAddQuantity(@Param("id") String id,
                        @Param("cartId") String cartId,
                        @Param("productId") String productId,
                        @Param("quantity") int quantity);
}
```

**Lưu ý quan trọng:**
- Native query bypass JPA cache → sau khi upsert, planner phải `entityManager.flush()` + `clear()` trước khi `findByUserId()` lại để FE thấy giá trị mới (hoặc service layer return DTO sau `SELECT` raw).
- `EXCLUDED.quantity` là alias cho row đang INSERT (PostgreSQL convention).
- Schema-qualified table name (`order_svc.cart_items`) BẮT BUỘC vì self-reference trong DO UPDATE clause [VERIFIED: PostgreSQL syntax].

### Pattern 3: Cart Service `getOrCreate` (lazy cart row)

**What:** Nếu user lần đầu gọi `GET /cart` → tạo empty cart row trên-the-fly và trả empty items[].

```java
@Transactional
public CartEntity getOrCreateByUserId(String userId) {
  return cartRepository.findByUserId(userId).orElseGet(() -> {
    CartEntity cart = CartEntity.create(userId);
    return cartRepository.save(cart);
  });
}
```

**Race condition:** Nếu 2 request `GET /cart` cùng user_id race → cùng INSERT → unique constraint violation. Mitigation: catch `DataIntegrityViolationException` và retry `findByUserId` (rare case, acceptable for MVP). Hoặc dùng PostgreSQL upsert tương tự Pattern 2.

### Pattern 4: React Query Write-Through

**What:** Mutation gọi API → success → `invalidateQueries(['cart'])` để re-fetch fresh data.

```typescript
// Source: TanStack Query v5 docs [CITED: tanstack.com/query/latest/docs/react/guides/invalidations-from-mutations]
const queryClient = useQueryClient();

const cartQuery = useQuery({
  queryKey: ['cart'],
  queryFn: getCart,
  staleTime: Infinity,  // chỉ refetch khi explicit invalidate (write-through pattern)
  enabled: isAuthenticated,
});

const addItemMutation = useMutation({
  mutationFn: ({ productId, quantity }: AddItemArgs) => addCartItem(productId, quantity),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
  onError: (err: ApiError) => {
    if (err.code === 'STOCK_SHORTAGE') {
      toast.error('Số lượng vượt quá tồn kho');
    } else {
      toast.error('Không thêm được vào giỏ hàng');
    }
  },
});
```

**KHÔNG dùng `onMutate` optimistic update** vì user locked write-through MVP (D-11). Latency ~200ms acceptable.

### Pattern 5: AuthProvider Merge Injection

**What:** Inject merge call vào `login()` after token set, before `router.push`.

```typescript
// services/cart.ts
export async function mergeGuestCartToServer(): Promise<void> {
  const guestItems = readLocalCart();  // _localCart namespace
  if (guestItems.length === 0) return;

  try {
    await httpPost('/api/orders/cart/merge', {
      items: guestItems.map(i => ({ productId: i.productId, quantity: i.quantity })),
    });
    clearLocalCart();  // chỉ clear khi merge OK
  } catch (err) {
    // log + toast — KHÔNG throw, KHÔNG block login (D-14)
    console.error('[cart-merge] failed', err);
    toast.warning('Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại');
  }
}

// AuthProvider.login() updated:
const login = useCallback(async (u: AuthState['user']) => {
  setUser(u);
  setIsAuthenticated(true);
  if (u && typeof window !== 'undefined') {
    window.localStorage.setItem('userProfile', JSON.stringify(u));
  }
  // NEW: merge guest cart sau khi token đã được setTokens() ngoài scope này
  await mergeGuestCartToServer();
  // Caller (login page) sẽ router.push sau khi await login() done
}, []);

const logout = useCallback(() => {
  clearTokensHelper();
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('userProfile');
  }
  clearLocalCart();  // NEW: clear guest cart key tránh leak
  // CŨNG: invalidate React Query cache để header badge reset (planner inject queryClient.clear() hoặc removeQueries)
  setUser(null);
  setIsAuthenticated(false);
}, []);
```

**Critical ordering:** Trong login page submit handler hiện tại:
```typescript
await login(email, password);   // services/auth.ts → setTokens
useAuth().login(user);          // AuthProvider state + merge
router.push(returnTo || '/');   // Sau khi merge xong (await đã đảm bảo)
```

Planner verify: `useAuth().login` có thể là sync hiện tại — phải đổi signature thành `async` để await merge. Nếu sợ block login UX, có thể không-await và để merge fire-and-forget (acceptable per D-14 "không block").

### Anti-Patterns to Avoid

- **`@Data` Lombok trên JPA entity với `@OneToMany`:** Generate `equals/hashCode` trên collection → infinite recursion + lazy init bug. Dùng manual equals/hashCode based on `id` only. (Codebase này dùng entity record-style, không Lombok — verified.)
- **Optimistic UI cho cart mutation:** User locked write-through. Don't add — scope creep.
- **Stock snapshot trong `cart_items.stock`:** User locked KHÔNG snapshot (D-17). Live fetch mỗi mutation.
- **Cart guest server-side với session cookie:** Out of scope (D-deferred).
- **Modify token storage trong phase này:** STORE-04 deferred. KHÔNG sửa `services/token.ts`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Idempotent upsert (race-safe) | Custom find→insert/update với `synchronized` hoặc `@Lock(PESSIMISTIC_WRITE)` | PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` native query | Single-statement atomic; no lock contention; race-safe by DB engine |
| FE async cart cache | Manual `useState` + `useEffect` re-fetch | React Query `useQuery` + `invalidateQueries` | Battle-tested cache, dedup, retry, cross-component subscribe |
| 409 STOCK_SHORTAGE error code | Tạo error code mới `CART_STOCK_SHORTAGE` | Reuse `STOCK_SHORTAGE` từ Phase 8 | Frontend dispatcher đã handle code này (toast + clamp UI) |
| FE storage scanner tool | Build custom AST walker | Manual `grep -rn "localStorage\|sessionStorage" src/` + classify table | One-shot deliverable; codebase < 50 hits theo preview; tooling overhead > value |
| Cross-tab cart sync | Custom `BroadcastChannel` infrastructure | Existing `cart:change` CustomEvent (guest path) + React Query (user path auto-shares cache trong cùng tab) | Multi-tab same-user cùng login = mỗi tab có own React Query cache; chấp nhận eventual consistency. Nếu user mở 2 tab và mutate cùng cart row → ON CONFLICT DO UPDATE đảm bảo DB consistent. |
| Cart cleanup cho stale product (product bị soft-delete) | Build cleanup job | Filter trong `GET /cart` query: JOIN products + skip rows nơi `product.deleted=true`, log warning | Defer per CONTEXT.md; planner đề xuất nếu thấy cần |
| Bidirectional collection management | Manual `cart.items.add(item)` everywhere | Helper method `cart.addItem(item)` + `removeItem(item)` set both sides | Hibernate cần cả 2 sides để cascade work; helper centralize logic [CITED: hibernate.org/orm/documentation 6.x §Bidirectional Associations] |

**Key insight:** Mọi thứ phase này cần đều đã có precedent trong codebase từ Phase 5/6/8 (JPA, Flyway, RestTemplate stock fetch, ApiErrorResponse, React Query, http.ts wrappers). Phase này là RE-APPLY pattern, không invention.

## Runtime State Inventory

> Phase 18 có rename/replace `CartEntity` cũ + delete `InMemoryCartRepository`. Audit:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `localStorage['cart']` per browser (guest carts cũ) — KHÔNG migrate sang DB tự động vì không biết user_id của guest. Chấp nhận: guest data vẫn ở localStorage; khi user login + có items localStorage → merge endpoint xử lý. KHÔNG có DB rows cũ vì `InMemoryCartRepository` là in-memory volatile. | Nothing — guest path preserved; merge handles login transition |
| Live service config | `OrderCrudService` hiện tại có method `listCarts/getCart/createCart/updateCart/deleteCart` reference `InMemoryCartRepository` + `CartUpsertRequest` record. Endpoints nào đang gọi? Verified: KHÔNG có `CartController` nào trong codebase order-service (verified qua codebase scan trong CONTEXT.md "InMemoryCartRepository unused"). Vậy các method này dead code. | Code edit: xóa methods + record `CartUpsertRequest` từ `OrderCrudService.java` (cleanup) |
| OS-registered state | None — không có OS task / cron reference cart. | None — verified by phase scope (no OS scheduling). |
| Secrets/env vars | None — không có env var name reference cart. JWT_SECRET dùng cho user_id extraction (existing, no change). | None |
| Build artifacts | Spring Boot fat-jar order-service: rebuild standard. KHÔNG có .egg-info hay binary cache. | None — standard `mvn clean package` đủ |
| FE localStorage keys | `cart` (migrated với fold-in path), `userProfile` (UI cache, kept), `accessToken`/`refreshToken` (kept, deferred STORE-04), `auth_present` cookie (kept). | Audit table trong SUMMARY.md classify từng key |

**Nothing found in OS-registered state, secrets, build artifacts categories** — verified by phase scope.

**Critical cleanup:** `OrderCrudService.java` dòng 50-93 (`InMemoryCartRepository cartRepository`, `listCarts`, `getCart`, `createCart`, `updateCart`, `deleteCart`, `cartComparator`, record `CartUpsertRequest`) là dead code phải xóa khi delete `InMemoryCartRepository`. Verify: không bài test hay controller nào reference các method này — nếu có, planner đưa thành sub-task.

## Common Pitfalls

### Pitfall 1: LazyInitializationException khi map Cart → DTO ngoài transaction

**What goes wrong:** `CartCrudService.getCart()` return entity, controller serialize → Hibernate session đã close → `entity.getItems()` throw `LazyInitializationException` → 500.
**Why it happens:** `@OneToMany(fetch=LAZY)` mặc định lazy; mapping ra DTO chạy ngoài transaction nếu method KHÔNG có `@Transactional`.
**How to avoid:** Pattern Phase 8 D-bug-fix: `@Transactional(readOnly=true)` trên method service trả DTO; HOẶC dùng fetch-join query `findByUserIdWithItems` (LEFT JOIN FETCH items). Mapping toDto() trong cùng tx scope.
**Warning signs:** Log `LazyInitializationException: could not initialize proxy - no Session` — Phase 8 đã đụng (`OrderCrudService.findAllWithItems` workaround).

### Pitfall 2: equals/hashCode trên JPA entity với collection

**What goes wrong:** Auto-generated equals/hashCode dùng `items` collection → trigger lazy load + infinite recursion (CartEntity.hashCode → items → CartItemEntity.hashCode → cart → ...).
**Why it happens:** Lombok `@Data` hoặc IDE-generated equals dùng all fields.
**How to avoid:** equals/hashCode chỉ dựa trên `id` (entity record-style codebase này — verified, không Lombok). Nếu dùng class-style: manual override `equals` so sánh `id`, `hashCode` trả `Objects.hash(id)` (hoặc constant cho transient entity chưa persist).

### Pitfall 3: SSR-safe localStorage access

**What goes wrong:** Next.js App Router pre-render trên server → `window.localStorage` undefined → ReferenceError.
**How to avoid:** Hiện cart.ts đã có guard `if (typeof window === 'undefined') return [];` — preserve pattern khi rewrite. AuthProvider lazy initializer đã đúng pattern (verified).

### Pitfall 4: Race condition khi user mở 2 tab cùng login

**What goes wrong:** Tab A `addItem(P1, 2)`, Tab B `addItem(P1, 3)` đồng thời → 2 INSERT cùng `(cart_id, P1)` → 1 fail unique constraint.
**How to avoid:** `INSERT ... ON CONFLICT DO UPDATE SET quantity = existing + EXCLUDED.quantity` atomic — DB nối tiếp 2 statement, tổng đúng = 5. Race-safe by DB engine. (KHÔNG cần app-level lock.)
**Warning signs:** PostgreSQL log "duplicate key value violates unique constraint" — nếu thấy → có code đang dùng plain INSERT thay vì upsert. Fix: dùng Pattern 2.

### Pitfall 5: Merge endpoint race với cart mutation đồng thời

**What goes wrong:** User login → merge fire → đồng thời tab khác đang `addItem`. Nếu merge dùng plain INSERT → conflict.
**How to avoid:** Merge endpoint loop qua items[] và mỗi item gọi cùng upsert query Pattern 2. Atomic per-item, race-safe. Acceptable: nếu race tinh vi → final state = sum tất cả mutations (idempotent semantic).

### Pitfall 6: Stock check live latency làm chậm UX

**What goes wrong:** Mỗi POST cart/items → order-svc gọi product-svc qua gateway → 50-200ms thêm. Slow UX click +/-.
**How to avoid:** Acceptable per D-11 (write-through tradeoff locked). Nếu UX feedback xấu → defer optimization (cache stock per-request, batch validate). Planner KHÔNG implement caching trong phase này.
**Warning signs:** User complaint slow cart; FE timeout. Mitigation in code: `RestTemplate` timeout 3-5s (default OK).

### Pitfall 7: Cascade orphan khi xóa cart row hoặc item

**What goes wrong:** `cartRepository.delete(cart)` mong cascade items, nhưng quên `orphanRemoval=true` → `cart_items` còn rows orphan với cart_id NULL hoặc FK violation.
**How to avoid:** `cascade=ALL, orphanRemoval=true` trên `@OneToMany` + `ON DELETE CASCADE` trong SQL FK (D-01 đã có). Cả 2 layer đảm bảo. Verified pattern Phase 8.

### Pitfall 8: localStorage `cart` leak sau logout

**What goes wrong:** User A logout không clear `cart` localStorage → guest browser sau đó (User B) thấy cart cũ của A.
**How to avoid:** D-15 explicit: `AuthProvider.logout()` gọi `clearCart()` localStorage. Implementation đơn giản — đừng quên test case này trong manual UAT.
**Warning signs:** Bug report "tôi logout xong vẫn thấy đồ trong giỏ" → cart leak.

### Pitfall 9: AuthProvider login signature thay đổi từ sync sang async

**What goes wrong:** Hiện `useAuth().login` là sync `useCallback`. Sau khi inject `await mergeGuestCartToServer()` → caller cần await. Login page submit handler có thể chưa await → race với `router.push` chạy trước merge.
**How to avoid:** Đổi signature thành `async (u) => Promise<void>`. Login page submit handler đã `await login(...)` cho `services/auth.ts` → thêm `await useAuth().login(user)` rồi mới `router.push`. Verify mọi caller `useAuth().login(...)` (grep phase 18 task).

## Code Examples

Verified patterns từ official sources và codebase Phase 5/8:

### Cart endpoint controller (Spring)

```java
// Source: codebase pattern + Spring Web docs
@RestController
@RequestMapping("/cart")  // gateway rewrites /api/orders/cart → /cart
public class CartController {
  private final CartCrudService cartService;

  @GetMapping
  public ApiResponse<CartDto> getCart(@RequestHeader("X-User-Id") String userId) {
    if (userId == null || userId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing X-User-Id");
    }
    return ApiResponse.ok(cartService.getCartDto(userId));
  }

  @PostMapping("/items")
  public ApiResponse<CartDto> addItem(
      @RequestHeader("X-User-Id") String userId,
      @Valid @RequestBody AddItemRequest req) {
    return ApiResponse.ok(cartService.addItem(userId, req.productId(), req.quantity()));
  }

  @PatchMapping("/items/{productId}")
  public ApiResponse<CartDto> setItem(
      @RequestHeader("X-User-Id") String userId,
      @PathVariable String productId,
      @Valid @RequestBody SetItemRequest req) {
    return ApiResponse.ok(cartService.setItem(userId, productId, req.quantity()));
  }

  @DeleteMapping("/items/{productId}")
  public ApiResponse<CartDto> removeItem(
      @RequestHeader("X-User-Id") String userId,
      @PathVariable String productId) {
    return ApiResponse.ok(cartService.removeItem(userId, productId));
  }

  @DeleteMapping
  public ApiResponse<CartDto> clearCart(@RequestHeader("X-User-Id") String userId) {
    return ApiResponse.ok(cartService.clearCart(userId));
  }

  @PostMapping("/merge")
  public ApiResponse<CartDto> merge(
      @RequestHeader("X-User-Id") String userId,
      @Valid @RequestBody MergeRequest req) {
    return ApiResponse.ok(cartService.merge(userId, req.items()));
  }

  public record AddItemRequest(@NotBlank String productId, @Min(1) int quantity) {}
  public record SetItemRequest(@Min(0) int quantity) {}  // 0 = delete alias
  public record MergeRequest(@NotEmpty List<@Valid MergeItem> items) {}
  public record MergeItem(@NotBlank String productId, @Min(1) int quantity) {}
}
```

**Note auth header:** Codebase hiện tại dùng `X-User-Id` header forwarded từ gateway (Phase 5/8 pattern, NOT bearer-parse trong order-service). [VERIFIED: `OrderCrudService.createOrderFromCommand` dòng 144 — `userId == null || userId.isBlank()` check]. Phase 18 follow same pattern. JWT `sub` extraction xảy ra ở user-svc auth flow, sau đó gateway/middleware inject `X-User-Id` header (Phase 6 D-08 user_role cookie pattern + X-User-Id header convention).

### Flyway migration

```sql
-- Source: order_svc.V2__add_order_items.sql pattern
-- V4__add_cart_tables.sql

CREATE TABLE IF NOT EXISTS order_svc.carts (
  id          VARCHAR(36)    PRIMARY KEY,
  user_id     VARCHAR(36)    NOT NULL UNIQUE,
  created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS order_svc.cart_items (
  id          VARCHAR(36)    PRIMARY KEY,
  cart_id     VARCHAR(36)    NOT NULL REFERENCES order_svc.carts(id) ON DELETE CASCADE,
  product_id  VARCHAR(36)    NOT NULL,
  quantity    INT            NOT NULL CHECK (quantity > 0),
  created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
  CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON order_svc.cart_items(cart_id);
```

### Storage audit table format (cho 18-SUMMARY.md)

```markdown
## Storage Audit Report (STORE-01)

**Method:** `grep -rn "localStorage\|sessionStorage" sources/frontend/src/` + manual review.
**Date:** 2026-05-XX
**Total keys discovered:** N

| Key | Source File(s) | Purpose | Classification | Action | Reason |
|-----|---------------|---------|----------------|--------|--------|
| `cart` | services/cart.ts | Guest cart items array | (a) DB-migrate (user) + UI-keep (guest) | STORE-02 hybrid | Migrated to `order_svc.carts/cart_items` for logged-in users; localStorage retained for guest pre-login UX (out-of-scope: server-side guest cart) |
| `userProfile` | providers/AuthProvider.tsx | UI session cache: id/email/name | (b) UI-kept | None | DB source of truth via user-svc Phase 10 `/api/users/me`; cache để hydrate AuthProvider tránh flash unauth state. Không phải data leak. |
| `accessToken` | services/token.ts | JWT bearer token | (c) Auth-deferred | Deferred to STORE-04 | XSS tradeoff accepted Phase 6 D-11. Visible-first defer per REQUIREMENTS.md §carry-over. Migrate sang httpOnly cookie là backend hardening invisible. |
| `refreshToken` | services/token.ts | Refresh token (intentionally unused) | (c) Auth-deferred | Deferred to STORE-04 | Phase 6 token.ts comment "intentionally not exported"; cùng phạm vi STORE-04 |
| `auth_present` (cookie) | services/token.ts, middleware.ts | Edge middleware presence flag | (b) UI-kept (cookie) | None | Non-httpOnly cookie để middleware Edge runtime check session — không thể parse JWT trong Edge. Pattern Phase 6 D-08 locked. |

**STORE-03 fold-in result:** No additional user-data keys found beyond `cart`. Wishlist / recently-viewed / search-history not implemented in current codebase. STORE-03 closed with note.

**Confidence:** HIGH — preview grep verified trong CONTEXT.md research, full audit trong execution.
```

### React Query cart hook (FE)

```typescript
// Source: TanStack Query v5 + codebase services/http.ts
// sources/frontend/src/hooks/useCart.ts (NEW — planner quyết inline page hay tách)

export function useCart() {
  const { isAuthenticated } = useAuth();
  return useQuery({
    queryKey: ['cart'],
    queryFn: () => isAuthenticated ? fetchServerCart() : Promise.resolve(readLocalCart()),
    staleTime: Infinity,
    enabled: true,  // chạy cho cả guest path
  });
}

export function useAddItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: addCartItemRouted,  // wrapper guest vs user
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cart'] }),
  });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `InMemoryCartRepository` (Phase 5 stub, never wired) | JPA `CartRepository extends JpaRepository` + native upsert | Phase 18 (this) | Phase 5 D-comment: "Cart giữ in-memory, Phase 8 sẽ migrate" — slipped to Phase 18 instead |
| Cart hardcode 1-row-per-add `CartEntity(productId, quantity)` | Aggregate root `CartEntity` + `@OneToMany cart_items` | Phase 18 | Match domain model: 1 cart per user with N items; Phase 5 schema was DTO-leaning |
| Sync `readCart()` mọi nơi | Async `useCart()` React Query khi login + sync localStorage khi guest | Phase 18 | Caller chỉ thấy queryData hoặc data từ wrapper — pattern routing transparent |
| `cart:change` CustomEvent only | React Query `['cart']` cache + `cart:change` (guest path) | Phase 18 | RQ multi-component subscribe; `cart:change` legacy giữ để guest path không break |

**Deprecated:**
- `domain/CartEntity` record stub (Phase 5) — DELETE
- `repository/InMemoryCartRepository` — DELETE
- `OrderCrudService.listCarts/getCart/createCart/updateCart/deleteCart` + `CartUpsertRequest` — DELETE (dead code)

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `@tanstack/react-query` version trong package.json là 5.x với API hiện đại (`useMutation` object form, `invalidateQueries({queryKey})`) | Standard Stack | Nếu là 4.x, API form khác (positional args). Planner kiểm package.json; risk LOW vì Phase 7+ thường dùng v5. |
| A2 | Guard `X-User-Id` header được gateway/middleware tự inject từ JWT `sub` (giống Phase 8 OrderCrudService) | Code Examples §Cart Controller | Nếu order-service hiện tự parse JWT thay vì rely header → planner verify `OrderItemsController` hay tương tự để xem pattern thực. Risk MEDIUM. |
| A3 | `InMemoryCartRepository` thực sự dead code không có endpoint nào dùng | Runtime State Inventory | Nếu có hidden CartController gọi `OrderCrudService.listCarts` → xóa sẽ break API. Mitigation: planner grep `cartRepository\\.` toàn order-service trước khi xóa. Risk LOW per CONTEXT.md "InMemoryCartRepository unused". |
| A4 | Existing Phase 8 `cart:change` CustomEvent header badge subscriber sẽ break khi user logged-in (vì wrapper gọi API thay vì dispatch event). Cần migrate header badge sang React Query | Architecture Pattern 4 | Nếu bỏ qua → header badge không update khi user-path mutate. Mitigation: planner rewrite badge subscriber. Risk LOW (đã liệt kê trong Component Responsibilities). |
| A5 | Login submit handler hiện đã `await login(...)` từ services/auth.ts trước khi `router.push` | Pitfall 9 | Nếu fire-and-forget → merge race với router.push, user có thể thấy stale empty cart trên homepage. Mitigation: planner verify login page; có thể giữ fire-and-forget vì D-14 cho phép not-block. Risk LOW. |
| A6 | Stock validation latency 50-200ms acceptable trong UAT | Pitfall 6 | Nếu user phản hồi quá chậm → optimization phase tiếp theo. Risk LOW per D-11 lock. |

**If user confirms A2 mismatch:** Planner sẽ thiết kế CartController với JWT parse helper hoặc `Principal` injection thay `X-User-Id` header. Architecture KHÔNG đổi.

## Open Questions

1. **Header `X-User-Id` vs JWT parse trong order-service**
   - What we know: `OrderCrudService.createOrderFromCommand` nhận `userId` param từ controller, không tự parse JWT. Nhưng KHÔNG rõ controller hiện đọc từ đâu (header vs Principal).
   - What's unclear: Codebase order-service có `JwtAuthFilter` hay đơn thuần trust `X-User-Id` header forwarded từ gateway?
   - Recommendation: Plan task đầu tiên — đọc `OrderCrudController` (hoặc tương đương) để xác nhận pattern, document trong PLAN.md, áp dụng nhất quán cho `CartController`.

2. **React Query setup trong app shell**
   - What we know: Phase 7+ đã dùng React Query.
   - What's unclear: `QueryClientProvider` đã wrap `RootLayout` chưa? Nếu chưa → Phase 18 cần thêm.
   - Recommendation: Planner grep `QueryClientProvider` trong `sources/frontend/src/app/layout.tsx` và `providers/`. Nếu thiếu → Wave 0 task.

3. **Stock validation cho `POST /cart/merge`**
   - What we know: D-08 nói "clamp by stock". Per-item validate hay clamp silent?
   - What's unclear: Nếu guest có 5 items quantity > stock — fail toàn bộ merge với 409, hay clamp từng item về stock và return cleaned cart?
   - Recommendation: Clamp silent + return adjusted quantities trong response. UX tốt hơn (đỡ confuse user "tại sao login xong cart trống"). Toast info "Một số sản phẩm đã giảm số lượng do hết hàng". Planner quyết.

4. **Logout flow ordering với React Query cache**
   - What we know: D-15 clear localStorage cart.
   - What's unclear: React Query cache `['cart']` có cần `queryClient.removeQueries(['cart'])` để header badge phản chiếu trống ngay?
   - Recommendation: Yes — gọi `queryClient.removeQueries({queryKey: ['cart']})` trong logout. Hoặc đổi badge subscriber check `isAuthenticated` để fallback empty.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | order_svc schema migration V4 | ✓ (Phase 5) | 15+ | — |
| Spring Boot order-service | All BE work | ✓ | 3.3.x | — |
| Flyway | V4 migration | ✓ | bundled BOM | — |
| Next.js + React Query | FE cart hooks | ✓ (Phase 7+) | 14 + RQ ~5 | — |
| api-gateway routing `/api/orders/**` | Cart endpoints expose | ✓ | order-service-base + order-service routes already wildcard-match `/api/orders/cart/**` [VERIFIED: application.yml dòng 127-138] | None — không cần thêm route mới |
| product-service `GET /api/products/{id}` for stock | Cart mutation stock validation | ✓ (Phase 8) | — | — |
| JWT auth (X-User-Id forwarding) | Cart user_id | ✓ (Phase 6) | — | — |

**Missing dependencies with no fallback:** None.
**Missing dependencies with fallback:** None.

**Gateway route confirmation:** `application.yml` dòng 127-138 có route `order-service` predicates `Path=/api/orders/**` filter `RewritePath=/api/orders/(?<seg>.*), /orders/${seg}`. Vậy `/api/orders/cart` → `/cart`, `/api/orders/cart/items/abc` → `/cart/items/abc`, `/api/orders/cart/merge` → `/cart/merge`. KHÔNG cần thêm route. Planner verify base path `/cart` không xung đột với existing `/orders` controller routes (different prefix, không overlap).

**Caveat — `order-service-admin` route ưu tiên:** Đặt `Path=/api/orders/admin/**` rewrite trước. `/api/orders/cart` KHÔNG match `admin/**` nên route đi qua `order-service-base` hoặc `order-service` chính xác. OK.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework BE | JUnit 5 + Spring Boot Test (`@SpringBootTest`) — pattern Phase 5/8 [VERIFIED: codebase precedent] |
| Framework FE | Vitest + React Testing Library — pattern Phase 7+ [ASSUMED — planner verify package.json + vitest.config] |
| Config file | `pom.xml` (BE), `vitest.config.ts` (FE) |
| Quick run BE | `mvn -pl backend/order-service test` |
| Quick run FE | `npm --prefix sources/frontend run test -- --run cart` |
| Full suite BE | `mvn test` |
| Full suite FE | `npm --prefix sources/frontend run test -- --run` |
| E2E | Playwright (Phase 7 baseline) — không bắt buộc cho phase này nếu có manual UAT |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| STORE-02 | `POST /cart/items` lần 2 cùng productId → quantity累加 (idempotent upsert) | integration (BE) | `mvn -pl order-service test -Dtest=CartCrudServiceIT#addItem_idempotent` | ❌ Wave 0 |
| STORE-02 | `GET /cart` lazy-create cart row khi user lần đầu | integration | `mvn -pl order-service test -Dtest=CartCrudServiceIT#getCart_lazyCreate` | ❌ Wave 0 |
| STORE-02 | `POST /cart/items` quantity > stock → 409 STOCK_SHORTAGE | integration | `mvn -pl order-service test -Dtest=CartCrudServiceIT#addItem_stockShortage` | ❌ Wave 0 |
| STORE-02 | `POST /cart/merge` với items duplicate productId của cart hiện tại → quantity sum | integration | `mvn -pl order-service test -Dtest=CartCrudServiceIT#merge_idempotent` | ❌ Wave 0 |
| STORE-02 | `DELETE /cart/items/{productId}` → row removed; còn lại không đổi | integration | `mvn -pl order-service test -Dtest=CartCrudServiceIT#removeItem` | ❌ Wave 0 |
| STORE-02 | `services/cart.ts` guest path: addToCart preserve localStorage logic | unit (FE) | `npm test -- cart.guest.test.ts` | ❌ Wave 0 |
| STORE-02 | `services/cart.ts` user path: addToCart gọi httpPost `/api/orders/cart/items` | unit (FE, fetch mocked) | `npm test -- cart.user.test.ts` | ❌ Wave 0 |
| STORE-02 | `AuthProvider.login()` non-empty localStorage → POST /cart/merge → clearCart | unit (FE) | `npm test -- AuthProvider.merge.test.tsx` | ❌ Wave 0 |
| STORE-02 | `AuthProvider.logout()` → clearCart localStorage | unit (FE) | `npm test -- AuthProvider.logout.test.tsx` | ❌ Wave 0 |
| STORE-01 | Audit table SUMMARY.md liệt kê đủ keys phát hiện qua grep | manual-only | grep + visual review | n/a (deliverable) |
| STORE-03 | Audit fold-in: nếu wishlist/etc found → migrate; else đóng note | manual-only | grep + decision | n/a (deliverable) |

**Manual-only:** STORE-01 và STORE-03 deliverables là document (audit table) — không có behavior automated test sense. Manual review trong UAT.

### Sampling Rate

- **Per task commit:** Quick BE: `mvn -pl backend/order-service test` (chỉ order-svc, ~10s); Quick FE: `npm --prefix sources/frontend run test -- --run cart`
- **Per wave merge:** Full module BE + FE
- **Phase gate:** All tests green + manual UAT cart guest→login flow + audit SUMMARY.md review qua `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `sources/backend/order-service/src/test/java/.../service/CartCrudServiceIT.java` — integration test với `@SpringBootTest` + Testcontainers PostgreSQL (planner verify Testcontainers available, hoặc dùng H2 với `ON CONFLICT` workaround — H2 supports MySQL/PG mode)
- [ ] `sources/backend/order-service/src/test/java/.../web/CartControllerIT.java` — REST layer test với `@WebMvcTest` hoặc `@SpringBootTest`
- [ ] `sources/frontend/src/services/__tests__/cart.guest.test.ts` — guest path Vitest
- [ ] `sources/frontend/src/services/__tests__/cart.user.test.ts` — user path Vitest với fetch mock
- [ ] `sources/frontend/src/providers/__tests__/AuthProvider.merge.test.tsx` — merge injection
- [ ] Verify `vitest` + `@testing-library/react` available trong `package.json` (Phase 7+ should have)
- [ ] Verify Testcontainers / H2 setup cho `ON CONFLICT` testing (H2 PG mode supports MERGE-style; planner xác nhận)

**H2 vs Testcontainers note:** PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` syntax KHÔNG support trên H2 default — H2 có `MERGE INTO` thay thế. Hai approaches:
- **Option A:** Testcontainers PostgreSQL (real DB) — accurate test of upsert syntax. ASSUMED Testcontainers available.
- **Option B:** Skip integration test cho upsert, viết unit test repo với mocked EntityManager. Less coverage.

Planner quyết option dựa theo có sẵn Testcontainers trong codebase test infra.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Reuse JWT issued by user-svc (Phase 6); no new auth surface trong phase này |
| V3 Session Management | yes | `userProfile` localStorage = UI cache only; tokens deferred STORE-04 — KHÔNG sửa |
| V4 Access Control | yes | `X-User-Id` header injection chỉ từ gateway (trust boundary); CartController validate `userId != null/blank` (precedent OrderCrudService.createOrderFromCommand) |
| V5 Input Validation | yes | `@Valid` Bean Validation: `@NotBlank productId`, `@Min(1) quantity`, `@NotEmpty items` cho merge; FE `services/cart.ts` không trust localStorage data — backend re-validate stock |
| V6 Cryptography | no | Phase này không thêm crypto; tokens existing |
| V7 Error Handling | yes | Reuse `ApiErrorResponse` + `GlobalExceptionHandler` — KHÔNG leak stack trace |

### Known Threat Patterns for Cart→DB Spring + Next.js

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| User A modify cart của User B (IDOR via X-User-Id spoof) | Tampering / EoP | Trust X-User-Id chỉ qua api-gateway boundary; gateway phải set header từ verified JWT (D14 v1.0 audit deferred — accepted invisible). Phase 18 KHÔNG sửa boundary. |
| SQL injection qua productId trong native query upsert | Tampering | `@Param("productId")` binding (PreparedStatement) — không string concat. Pattern Spring Data JPA standard. |
| Race condition double-add làm double-quantity | Tampering | `INSERT ... ON CONFLICT DO UPDATE` atomic — DB serialize. |
| Stock bypass: FE skip stock check, post quantity > stock | Tampering | BE re-validate qua product-svc trên mọi mutation (D-16); FE check chỉ là UX hint. |
| `localStorage['cart']` leak between user sessions sau logout | Information Disclosure | D-15 `clearCart()` trên logout. |
| XSS qua productId/name lưu DB rồi reflect back FE | XSS | productId là UUID (no script injection vector); name FE-side render với React (auto-escape JSX). KHÔNG có HTML render từ cart payload. |
| Merge endpoint abuse: spam massive items array để DoS | DoS | Bean Validation `@Size(max=100) items` (planner đề xuất); rate limiting backend hardening defer. |

**Recommended in this phase:**
- `@NotBlank productId` (UUID format implicit)
- `@Min(1) quantity` everywhere except `PATCH` (cho phép 0 = delete alias)
- `@Size(max=100) List<MergeItem> items` trên merge body (sanity bound)
- Log warning khi `userId == null` thay vì silent 200 (helps detect gateway misconfig)

## Sources

### Primary (HIGH confidence)
- Codebase: `OrderCrudService.java`, `OrderEntity.java`, `OrderItemEntity.java`, `V2__add_order_items.sql`, `cart.ts`, `AuthProvider.tsx`, `application.yml`, `StockShortageException.java` — verified pattern Phase 5/8
- `.planning/phases/18-storage-audit-cart-db/18-CONTEXT.md` — locked decisions
- `.planning/phases/08-cart-order-persistence/08-CONTEXT.md` — Phase 8 patterns
- `.planning/REQUIREMENTS.md` STORE-01..04 + visible-first carry-over policy
- `.planning/STATE.md` v1.3 locks (cart→DB placement, Flyway V4 reservation)
- PostgreSQL 15 docs `INSERT ... ON CONFLICT` [CITED: postgresql.org/docs/15/sql-insert.html]

### Secondary (MEDIUM confidence)
- TanStack Query v5 docs `invalidateQueries`/`useMutation` [CITED: tanstack.com/query/latest]
- Hibernate 6.x docs `@OneToMany` cascade, orphanRemoval, bidirectional helpers [CITED: hibernate.org/orm/documentation/6.x]
- Spring Data JPA `@Modifying @Query` native query [CITED: docs.spring.io/spring-data/jpa/reference/jpa/modifying-queries.html]

### Tertiary (LOW confidence)
- Vitest + React Testing Library setup pattern [ASSUMED — planner verify Phase 7+ package.json]
- React Query exact version [ASSUMED 5.x — planner verify]

## Project Constraints (from CLAUDE.md)

`./CLAUDE.md` không tồn tại trong project root [VERIFIED: Read attempt returned "File does not exist"]. Auto-memory ràng buộc duy nhất từ user instructions: **Vietnamese language** cho output/docs/commits, **giữ EN cho code identifiers + commit prefixes**; **visible-first priority** (STORE-04 deferred phù hợp); **dự án thử nghiệm GSD KHÔNG phải PTIT/HTPT student assignment** — tránh references này trong code/comments.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — toàn bộ tech đã có precedent trong codebase
- Architecture: HIGH — reuse y hệt Phase 8 OrderEntity↔OrderItemEntity pattern
- Pitfalls: HIGH — Phase 8 đã encounter LazyInitializationException (verified bug fix); equals/hashCode pattern entity record-style đã chuẩn
- Endpoint design: HIGH — locked decisions D-04..D-08 explicit
- React Query patterns: MEDIUM-HIGH — version assumption A1
- Stock validation latency: MEDIUM — accept user trade-off

**Research date:** 2026-05-02
**Valid until:** 2026-06-01 (30 days; stack stable)

---

## RESEARCH COMPLETE

**Phase:** 18 - Kiểm Toán Storage + Cart→DB
**Confidence:** HIGH

### Key Findings

- **Hoàn toàn reuse pattern Phase 8** cho JPA aggregate (CartEntity ↔ CartItemEntity với `@OneToMany cascade=ALL orphanRemoval=true`); tránh stub cũ + InMemoryCartRepository (delete sạch).
- **Native SQL upsert PostgreSQL `INSERT ... ON CONFLICT (cart_id, product_id) DO UPDATE`** là race-safe single-statement, atomic — không cần app-level lock; dùng cho `POST /cart/items` và `POST /cart/merge` (per-item).
- **Gateway route đã sẵn sàng** — `order-service` route wildcard `Path=/api/orders/**` cover toàn bộ `/api/orders/cart/**` mà không cần thêm config.
- **Stock validation reuse `StockShortageException` + `STOCK_SHORTAGE` code** từ Phase 8; gọi product-svc qua gateway pattern y hệt `OrderCrudService.validateStockOrThrow`.
- **Storage audit predicted output:** chỉ 5 keys (`cart`, `userProfile`, `accessToken`, `refreshToken`, `auth_present` cookie) — STORE-03 expected close với "no additional leaks".
- **AuthProvider login signature phải đổi sang async** để await merge call trước `router.push`; logout phải `clearCart()` localStorage + `queryClient.removeQueries(['cart'])`.

### File Created
`.planning/phases/18-storage-audit-cart-db/18-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | Toàn bộ đã có trong codebase, không thêm dependency |
| Architecture | HIGH | Pattern Phase 8 verified; locked decisions D-01..D-21 explicit |
| Pitfalls | HIGH | Phase 8 đã đụng LazyInit; equals/hashCode pattern codebase chuẩn; race condition mitigated by ON CONFLICT |
| FE patterns | MEDIUM-HIGH | React Query version giả định 5.x — planner verify package.json |
| Test infra | MEDIUM | Testcontainers vs H2 cho `ON CONFLICT` testing — planner chọn approach |

### Open Questions

1. JWT/header injection pattern: order-service nhận user_id qua `X-User-Id` header (Phase 8 precedent) hay qua `Principal`? Verify trước khi viết CartController.
2. `QueryClientProvider` đã wrap RootLayout chưa? Nếu không → Wave 0 task setup.
3. Merge stock-shortage handling: clamp silent với toast info, hay 409 fail-fast? Recommend clamp silent.
4. Logout cần `queryClient.removeQueries(['cart'])` để badge reset ngay — confirm injection point.

### Ready for Planning

Research complete. Planner có đủ thông tin để tạo PLAN.md cho Phase 18 với task breakdown:
- Wave 0: Test scaffolding + verify React Query setup
- Wave 1 (BE): V4 migration + entities + repository + service + controller + dọn dead code OrderCrudService
- Wave 2 (FE): cart.ts wrapper + React Query hooks + cart page + checkout + header badge
- Wave 3 (Auth): AuthProvider login merge injection + logout clearCart
- Wave 4 (Audit): SUMMARY.md storage audit deliverable
- Wave 5 (Verify): Manual UAT guest→login flow + automated tests green

Verified prerequisites: gateway routing OK, Phase 8 stock validation reusable, JPA pattern locked, React Query infra likely in place từ Phase 7+ (verify trong Wave 0).
