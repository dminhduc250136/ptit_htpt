# UC-CART: Giỏ hàng

## Tóm tắt
Customer quản lý giỏ hàng: add/update/remove items, xem total. Guest dùng localStorage, logged-in dùng DB (merge khi login). Quantity 1-10/item, max 50 SKU/cart. Giá snapshot tại thời điểm add, sync khi render.

## Context Links
- Strategy: [../strategy/services/order-business.md#cart-rules](../strategy/services/order-business.md#cart-rules)
- Technical Spec: [../technical-spec/ts-cart.md](../technical-spec/ts-cart.md)
- Architecture: [../architecture/services/order-service.md](../architecture/services/order-service.md)

## Actors
- **Primary**: Customer (logged-in), Guest (không login — MVP local cart only)

## Preconditions
- Product tồn tại, status=ACTIVE, stock > 0

---

## Flow A — Add to Cart (logged-in)

### Main Flow
1. User ở product detail, chọn quantity (1-10), click "Thêm vào giỏ"
2. FE gửi POST /api/v1/cart/items { productId, quantity }
3. BE:
   - Find hoặc create cart cho userId
   - Check product exists + ACTIVE (gọi Product Service hoặc cache)
   - Check `stock >= quantity`
   - Check cart đã có product này chưa:
     - Có → tăng quantity (cap max 10)
     - Chưa → insert cart_item với priceSnapshot = current price
   - Update cart.updatedAt
4. BE trả 200 { cart }
5. FE mở CartDrawer, hiển thị item mới + total

### Exception Flows
- **EF-A1: Product INACTIVE** → 400 `PRODUCT_UNAVAILABLE`
- **EF-A2: Out of stock** → 400 `OUT_OF_STOCK`
- **EF-A3: Quantity > stock** → 400 `INSUFFICIENT_STOCK` với message hiện số còn
- **EF-A4: Tăng quantity > 10 (limit)** → FE warn, cap 10
- **EF-A5: Cart > 50 SKU** → 400 `CART_LIMIT_EXCEEDED` (MVP không limit, có thể bỏ)

### Acceptance Criteria
- [ ] AC-A1: Add same product → increment quantity
- [ ] AC-A2: Quantity max 10 per item
- [ ] AC-A3: Stock check real-time tại thời điểm add
- [ ] AC-A4: Drawer mở tự động hiển thị cart

---

## Flow B — Add to Cart (guest)

### Main Flow
1. Guest click "Thêm vào giỏ" ở product detail
2. **Option MVP**: redirect `/login?redirect=/product/{slug}` → sau login quay lại → user click lại thêm
3. **Option phase 2 (guest cart)**:
   - FE lưu item vào Zustand store (persist localStorage)
   - Hiển thị drawer với items local
   - Khi guest vào /checkout → prompt login/register → merge cart khi login

### Acceptance Criteria (MVP)
- [ ] AC-B1: Guest click add → redirect login, preserve product intent

### Acceptance Criteria (Phase 2)
- [ ] AC-B2: Guest cart lưu localStorage, persist 30 ngày
- [ ] AC-B3: Login → merge local cart với server cart (union, quantity sum cap 10)

---

## Flow C — View Cart

### Main Flow
1. User vào `/cart` (hoặc click cart icon header)
2. FE gọi GET /api/v1/cart
3. BE trả cart với items:
   ```json
   {
     "id": "uuid",
     "items": [
       {
         "productId": "uuid",
         "productName": "iPhone 15 Pro",
         "productImage": "...",
         "quantity": 2,
         "priceSnapshot": 28990000,
         "salePriceSnapshot": 26990000,
         "effectivePriceSnapshot": 26990000,
         "currentPrice": 27990000,
         "currentSalePrice": 25990000,
         "priceChanged": true,
         "stock": 15
       }
     ],
     "subtotal": 53980000,
     "itemCount": 2
   }
   ```
4. FE render:
   - Table/Cards với items: image, name, price, quantity stepper, line total, remove button
   - Warning nếu `priceChanged: true`: "Giá đã thay đổi, cập nhật?"
   - Warning nếu `stock < quantity`: "Chỉ còn X sản phẩm, giảm số lượng?"
   - Sidebar: Subtotal, shipping fee (placeholder "Tính ở checkout"), CTA "Tiến hành đặt hàng"

### Acceptance Criteria
- [ ] AC-C1: Hiển thị price change warning nếu current != snapshot
- [ ] AC-C2: Hiển thị stock warning nếu quantity > current stock
- [ ] AC-C3: Subtotal tính từ current price (không snapshot) — công bằng với user
- [ ] AC-C4: Empty state: "Giỏ hàng trống" + CTA "Mua sắm ngay"

---

## Flow D — Update Quantity

### Main Flow
1. User click +/- stepper trên cart item
2. FE debounce 300ms → gửi PATCH /api/v1/cart/items/{productId} { quantity }
3. BE:
   - Validate quantity 1-10
   - Validate quantity <= current stock
   - Update cart_item
4. BE trả cart updated
5. FE re-render subtotal

### Exception Flows
- **EF-D1: Quantity = 0** → FE ask "Xóa sản phẩm?" → gọi DELETE thay PATCH
- **EF-D2: Quantity > stock** → 400 `INSUFFICIENT_STOCK`, FE reset về stock max
- **EF-D3: Quantity > 10** → FE cap trước, không send

### Acceptance Criteria
- [ ] AC-D1: Update <500ms, UI optimistic
- [ ] AC-D2: Stock re-validate mỗi lần update

---

## Flow E — Remove Item

### Main Flow
1. User click X icon trên item
2. FE confirm hoặc optimistic remove
3. FE gửi DELETE /api/v1/cart/items/{productId}
4. BE delete row
5. BE trả cart updated
6. FE re-render

### Acceptance Criteria
- [ ] AC-E1: Remove success < 300ms
- [ ] AC-E2: Nếu cart empty sau remove → hiển thị empty state

---

## Flow F — Clear Cart

### Main Flow
1. User click "Xóa tất cả" (optional button, confirm dialog)
2. FE gửi DELETE /api/v1/cart
3. BE soft-delete all items
4. FE redirect hoặc empty state

### Acceptance Criteria
- [ ] AC-F1: Confirm dialog required

---

## Flow G — Merge cart khi login (từ guest)

### Main Flow (chỉ apply nếu có guest cart phase 2)
1. Guest login
2. FE detect guest cart trong localStorage
3. FE gửi POST /api/v1/cart/merge { items: [...guestCart] }
4. BE:
   - Load server cart (nếu có)
   - Với mỗi guest item:
     - Nếu product đã có → max(guest.qty, server.qty, 10)
     - Chưa có → insert
5. FE clear localStorage, render merged cart

---

## Business Rules (references)
- Cart storage: DB for auth, localStorage for guest
- Max 10 qty/item
- Price snapshot khi add, sync khi render, dùng current khi checkout
- TTL cart 30 ngày inactive

## Non-functional Requirements
- **Performance**:
  - GET /cart p95 < 300ms
  - POST item p95 < 500ms (có gọi Product Service)
- **Consistency**: Stock check tại thời điểm add + checkout (không chỉ add)

## UI Screens
- `/cart` — cart page
- CartDrawer (slide-in từ right, desktop + mobile)
- Cart icon header với badge số items
