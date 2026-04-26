---
phase: 08-cart-order-persistence
reviewed: 2026-04-26T00:00:00Z
depth: standard
files_reviewed: 20
files_reviewed_list:
  - sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
  - sources/backend/order-service/src/main/resources/db/migration/V2__add_order_items.sql
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemEntity.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderItemRepository.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemDto.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/exception/StockShortageException.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderDto.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
  - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java
  - sources/frontend/src/types/index.ts
  - sources/frontend/src/app/checkout/page.tsx
  - sources/frontend/src/app/profile/orders/[id]/page.tsx
  - sources/frontend/src/app/profile/orders/[id]/page.module.css
  - sources/frontend/src/app/products/[slug]/page.tsx
  - sources/frontend/src/app/products/[slug]/page.module.css
findings:
  critical: 2
  warning: 4
  info: 3
  total: 9
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-04-26
**Depth:** standard
**Files Reviewed:** 20
**Status:** issues_found

## Summary

Phase 8 thêm 3 tính năng chính: (1) stock column vào product-service, (2) order_items table + shippingAddress/paymentMethod persistence vào order-service, (3) frontend hiển thị stock trên product detail page và order items table trên order detail page. Logic tổng thể đúng và các data contract giữa FE/BE khớp nhau. Phát hiện 2 critical issues liên quan đến race condition stock và một lỗi redirect sai URL, 4 warnings về lỗi logic tiềm ẩn.

---

## Critical Issues

### CR-01: Race condition stock — validate và deduct không atomic

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:126-163`

**Issue:** Luồng D-04/D-05 thực hiện GET stock → so sánh → persist order → PATCH stock qua 3 HTTP call riêng biệt, không có locking. Khi 2 user đặt hàng cùng lúc với stock = 1, cả hai đều pass validate (stock = 1 ≥ quantity = 1), cả hai persist order thành công, sau đó cả hai deduct → stock = -1. MVP acceptable theo comment code, nhưng đây là lỗi correctness thực sự (oversell), không phải vấn đề performance.

**Fix:** Thêm `@Transactional` và sử dụng `SELECT ... FOR UPDATE` (pessimistic lock) hoặc optimistic lock với version field khi gọi PATCH stock. Ở mức tối thiểu (MVP-safe): thêm guard trong `deductStockAfterPersist` để không cho `newStock` âm và log WARN khi `currentStock < item.quantity()` (tức là bị oversell):

```java
// Trong deductStockAfterPersist — thêm oversell detection
int newStock = currentStock - item.quantity();
if (newStock < 0) {
    log.warn("[D-05] OVERSELL detected productId={} currentStock={} requested={}",
        item.productId(), currentStock, item.quantity());
    newStock = 0;
}
```

---

### CR-02: Redirect sai URL sau checkout thành công

**File:** `sources/frontend/src/app/checkout/page.tsx:97`

**Issue:** Sau khi `createOrder` thành công, code redirect đến `/account/orders/` + order.id. Nhưng route thực tế của order detail page là `/profile/orders/[id]` (theo file `sources/frontend/src/app/profile/orders/[id]/page.tsx`). User sẽ bị 404 sau mỗi lần checkout thành công.

**Fix:**
```tsx
// Dòng 97 — đổi path
router.push('/profile/orders/' + order.id);
```

---

## Warnings

### WR-01: `getOrder` bỏ qua tham số `includeDeleted`

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:101-105`

**Issue:** Method `getOrder(String id, boolean includeDeleted)` nhận tham số `includeDeleted` nhưng không sử dụng nó. `@SQLRestriction("deleted = false")` luôn lọc bỏ deleted records, và không có nhánh code nào kiểm tra `includeDeleted`. Nếu admin cần xem deleted order theo id, API sẽ trả 404 mà không có cảnh báo.

```java
public OrderDto getOrder(String id, boolean includeDeleted) {
    OrderEntity order = orderRepository.findById(id)  // luôn lọc deleted=false
        .orElseThrow(...);
    return OrderMapper.toDto(order);  // includeDeleted không được dùng
}
```

**Fix:** Thêm comment rõ ràng hoặc xóa tham số nếu không dùng:
```java
// Nếu chấp nhận behavior hiện tại:
public OrderDto getOrder(String id) { ... }

// Hoặc nếu cần support includeDeleted=true về sau:
// TODO: dùng native query khi includeDeleted=true (Phase 9)
```

---

### WR-02: `StockConflictItem` field name không khớp với backend response

**File:** `sources/frontend/src/app/checkout/page.tsx:26-31` và `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java:111-116`

**Issue:** Backend trả về `{ productId, productName, requested, available }` trong mảng `items` của STOCK_SHORTAGE response (dòng 112-116 GlobalExceptionHandler). Frontend định nghĩa `StockConflictItem` với field `name`, `availableQuantity`, `requestedQuantity` — không khớp với `productName`, `available`, `requested` của backend. Kết quả: modal stock shortage sẽ hiển thị `undefined` cho tên sản phẩm và số lượng.

**Fix:** Sửa interface `StockConflictItem` trong checkout page cho khớp backend:
```tsx
interface StockConflictItem {
  productId: string;
  productName: string;   // backend trả "productName", không phải "name"
  requested: number;     // backend trả "requested"
  available: number;     // backend trả "available"
}
```

Và cập nhật các chỗ dùng (dòng 273, 279, 301):
```tsx
updateQuantity(item.productId, item.available);   // dòng 273
// dòng 301:
<strong>{item.productName}</strong> — chỉ còn {item.available} sản phẩm (bạn đã chọn {item.requested})
```

---

### WR-03: `buildStockUpdateBody` có thể gửi PATCH với thiếu required fields

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java:357-371`

**Issue:** `buildStockUpdateBody` lấy fields từ GET response của product-service và skip các entry có value `null`. `ProductUpsertRequest` có `@NotBlank` trên `name`, `slug`, `categoryId`, `status`. Nếu product-service trả về response với các field nullable (ví dụ `brand=null`, `thumbnailUrl=null`) thì đúng — những field đó không required. Nhưng nếu vì lý do gì `name` hoặc `status` null trong response (dù hiếm), PATCH sẽ bị 400 mà `deductStockAfterPersist` chỉ log error chứ không rollback. Nguy hiểm hơn, method cũng giữ lại field `category` là object (nested), nhưng `ProductUpsertRequest` cần `categoryId` (String) — GET response trả `category: {id, name, slug}` nhưng PATCH body cần `categoryId`. Điều này sẽ luôn fail với required field `categoryId` missing.

**Fix:**
```java
private Map<String, Object> buildStockUpdateBody(Map<String, Object> existingProduct, int newStock) {
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    // Map trực tiếp các required fields
    body.put("name", existingProduct.get("name"));
    body.put("slug", existingProduct.get("slug"));
    // category trong GET response là object {id, name, slug} — extract categoryId
    Object cat = existingProduct.get("category");
    if (cat instanceof Map<?, ?> catMap) {
        body.put("categoryId", catMap.get("id"));
    }
    body.put("price", existingProduct.get("price"));
    body.put("status", existingProduct.get("status"));
    body.put("brand", existingProduct.get("brand"));
    body.put("thumbnailUrl", existingProduct.get("thumbnailUrl"));
    body.put("shortDescription", existingProduct.get("shortDescription"));
    body.put("originalPrice", existingProduct.get("originalPrice"));
    body.put("stock", newStock);
    return body;
}
```

---

### WR-04: `quantity` selector cho phép vượt stock khi `stock = 0`

**File:** `sources/frontend/src/app/products/[slug]/page.tsx:228-229`

**Issue:** Khi `product.stock = 0`, biểu thức `Math.min(product.stock || 1, quantity + 1)` trả về `Math.min(1, quantity + 1)` — tức là user vẫn có thể tăng quantity lên 1 dù hết hàng. Button "Thêm vào giỏ hàng" bị disable khi `stock === 0` (dòng 235), nhưng quantity selector vẫn cho phép tăng → state không nhất quán.

**Fix:**
```tsx
// Dòng 228-229 — thêm guard khi stock = 0
onClick={() => setQuantity(Math.min(Math.max(product.stock, 1), quantity + 1))}
disabled={quantity >= product.stock || product.stock === 0}
```

Hoặc đơn giản hơn, ẩn toàn bộ quantity selector khi `product.stock === 0`.

---

## Info

### IN-01: `OrderMapper` dùng static `ObjectMapper` instance

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderMapper.java:13`

**Issue:** `private static final ObjectMapper MAPPER = new ObjectMapper()` tạo instance riêng thay vì dùng Spring-managed `ObjectMapper` bean (đã có trong `OrderCrudService` qua constructor injection). Static instance không nhận cấu hình Jackson của ứng dụng (date format, module JavaTimeModule, v.v.) — có thể gây serialize `Instant` ra dạng array thay vì ISO string trong tương lai nếu config thay đổi.

**Fix:** Chuyển `OrderMapper` thành Spring `@Component` và inject `ObjectMapper`:
```java
@Component
public class OrderMapper {
    private final ObjectMapper objectMapper;
    public OrderMapper(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    public OrderDto toDto(OrderEntity e) { ... }
}
```

---

### IN-02: `GlobalExceptionHandler.handleFallback` không log exception

**File:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/api/GlobalExceptionHandler.java:121-136`

**Issue:** Handler catch-all `Exception.class` trả 500 nhưng không log exception, khiến debugging production incidents rất khó. Stack trace bị nuốt hoàn toàn.

**Fix:**
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleFallback(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception on {}", request.getRequestURI(), ex);  // thêm dòng này
    // ... rest unchanged
}
```

---

### IN-03: CSS class `.tableHeader` rỗng trong order detail page

**File:** `sources/frontend/src/app/profile/orders/[id]/page.module.css:62-64`

**Issue:** Class `.tableHeader` được định nghĩa nhưng không có bất kỳ rule nào (chỉ có comment). Class này được dùng trong JSX (`className={styles.tableHeader}`). Không gây lỗi runtime nhưng là dead CSS.

**Fix:** Xóa class rỗng hoặc thêm style thực tế nếu có ý định dùng (ví dụ `background: var(--surface-container-low);`).

---

_Reviewed: 2026-04-26_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
