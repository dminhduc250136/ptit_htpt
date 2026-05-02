# Phase 18: Kiểm Toán Storage + Cart→DB - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 18-storage-audit-cart-db
**Areas discussed:** Schema, Merge strategy, Sync strategy, Guest cart, Stock validation, Audit scope

---

## Cart DB Schema

| Option | Description | Selected |
|--------|-------------|----------|
| carts + cart_items | Bảng `carts` (id, user_id UNIQUE, created_at, updated_at) + `cart_items` (id, cart_id FK, product_id, quantity, UNIQUE(cart_id, product_id)). Khớp ROADMAP/REQUIREMENTS, parent có timestamp riêng, dễ extend. | ✓ |
| Flat cart_items | Một bảng (id, user_id, product_id, quantity, UNIQUE(user_id, product_id)). Đơn giản nhưng không có cart-level metadata. | |

**User's choice:** carts + cart_items (Recommended)

---

## Guest → User Merge Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Auto merge sum quantity | INSERT ... ON CONFLICT DO UPDATE SET quantity = existing + new, clamp by stock. Không hỏi user. | ✓ |
| Replace DB cart bằng guest cart | Login → DB cart bị overwrite. Mất cart cũ user. | |
| Prompt user chọn | Modal hỏi 'Giữ cũ / Dùng mới / Gộp'. UX tốt nhưng phức tạp. | |

**User's choice:** Auto merge sum quantity (Recommended)

---

## Cart Sync Strategy (logged-in)

| Option | Description | Selected |
|--------|-------------|----------|
| Write-through mỗi mutation | Mỗi add/update/remove gọi API ngay, FE state derive từ server. Đơn giản, đúng ngay. | ✓ |
| Optimistic local + debounced sync | Update local ngay, debounce 300-500ms PATCH bulk. UX mượt, phức tạp hơn. | |
| Pure server-state (refetch all) | Mỗi action gọi API + refetch. Đơn giản nhất nhưng có lag. | |

**User's choice:** Write-through mỗi mutation (Recommended)

---

## Guest Cart Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ localStorage cho guest | Guest dùng localStorage; login → merge sang DB rồi clearCart(). | ✓ |
| Anonymous session_id server-side | Tạo cart server-side với UUID anonymous trong cookie. Phức tạp hơn. | |
| Bắt buộc login để add-to-cart | Guest click → redirect login. UX tệ cho e-commerce. | |

**User's choice:** Giữ localStorage cho guest (Recommended)

---

## Stock Validation Timing

| Option | Description | Selected |
|--------|-------------|----------|
| Cả cart mutation và checkout | Mỗi mutation backend validate qty <= stock, fail → 409. Checkout re-validate (Phase 8). | ✓ |
| Chỉ tại checkout | Cart cho phép qty tùy ý, fail tại POST /api/orders. UX khó. | |
| Snapshot lúc add-to-cart | Lưu stock_at_add, không re-check. Cart có thể stale. | |

**User's choice:** Cả cart mutation và checkout (Recommended)

---

## STORE-01 Audit Scope (multi-select)

| Option | Description | Selected |
|--------|-------------|----------|
| Không có gì thêm | Audit hiện tại chỉ thấy `cart`, `userProfile`, `accessToken`/`refreshToken`. Không có recently-viewed/wishlist. | ✓ |
| Thêm wishlist (nếu có) | Migrate wishlist nếu phát hiện. | ✓ |
| Thêm recently-viewed | Migrate recently-viewed nếu có. | ✓ |
| Thêm search history | Migrate search history nếu có. | ✓ |

**User's choice:** All 4 selected — interpret là "audit kỹ toàn diện, fold-in nếu phát hiện wishlist/recently-viewed/search-history; nếu không có thì STORE-03 đóng với note"
**Notes:** Auth token explicit defer to STORE-04 (REQUIREMENTS.md §carry-over).

---

## Claude's Discretion

- JPA mapping chi tiết (cascade, fetch type) cho `CartEntity ↔ CartItemEntity`
- React Query `staleTime` cho cart query
- Error message vi text cho 409 STOCK_SHORTAGE từ cart endpoint
- `cart_items.unit_price_at_add` snapshot — default KHÔNG (cart hiển thị live price)
- Endpoint URL exact path (`/api/orders/cart` vs `/api/cart`) align với gateway

## Deferred Ideas

- STORE-04 auth-token httpOnly cookie migration (security hardening, visible-first defer)
- Anonymous server-side cart
- Optimistic UI + debounced sync
- Multi-device cart conflict resolution
- Offline/PWA cart sync
- Cart cleanup job cho stale items
