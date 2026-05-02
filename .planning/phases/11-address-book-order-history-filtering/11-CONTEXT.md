# Phase 11: Address Book + Order History Filtering - Context

**Gathered:** 2026-04-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship address book CRUD tại `/profile/addresses` + tích hợp AddressPicker vào checkout + order filter bar tại `/profile/orders` (standalone page). Gom 2 features cùng `/profile/*` route family.

**Scope này deliver:**
1. Backend: V4 Flyway migration (addresses table), AddressController CRUD + set-default
2. Frontend: `/profile/addresses` page (list + create/edit/delete + set-default)
3. Frontend: AddressPicker snap-fill dropdown trong checkout page
4. Frontend: `/profile/orders` standalone page với filter bar (status + date + keyword)
5. Backend: listMyOrders() extend thêm filter query params

</domain>

<decisions>
## Implementation Decisions

### AddressPicker checkout UX (ACCT-06)

- **D-01:** AddressPicker dùng pattern **snap-fill form** — giữ nguyên manual form checkout. Thêm button/dropdown "Địa chỉ đã lưu" phía trên form. Chọn 1 saved address → auto-fill fullName, phone, street, ward, district, city vào form. User vẫn edit được sau khi fill.
- **D-02:** Khi user **chưa có saved address** → vẫn hiện button "Địa chỉ đã lưu", click mở dropdown → empty state với message "Chưa có địa chỉ. Thêm tại [/profile/addresses]." Link cross-sell sang address book.
- **D-03:** Khi user **có saved addresses** → button hiện, dropdown list addresses. Default address pre-highlighted. Chọn 1 → fill form. Không thay thế form (vẫn có manual entry tự nhiên vì form không bị ẩn).

### Address entity schema (ACCT-05)

- **D-04:** Addresses table gồm: `id UUID`, `user_id UUID FK`, `full_name VARCHAR(100)`, `phone VARCHAR(20)`, `street VARCHAR(200)`, `ward VARCHAR(100)`, `district VARCHAR(100)`, `city VARCHAR(100)`, `is_default BOOLEAN`, `created_at TIMESTAMPTZ`. **KHÔNG có label/nickname field** — hiển thị bằng cách nối các fields địa lý.
- **D-05:** Partial unique index `WHERE is_default = true` per user — DB enforces SC-3 (concurrent set-default chỉ giữ 1 row is_default=true). V4 Flyway migration cho user-service.
- **D-06:** Cap 10 addresses/user với error code `ADDRESS_LIMIT_EXCEEDED` (422) — theo lock v1.2 từ ROADMAP.md.
- **D-07:** Snapshot vào `OrderEntity.shippingAddress` JSONB khi checkout: `{fullName, phone, street, ward, district, city}` — full snapshot từ address entity, không FK. Hard-delete address không ảnh hưởng order history (SC-2 intact).

### Order filter page (ACCT-02)

- **D-08:** Tạo **`/profile/orders/page.tsx` route riêng** — standalone page với filter bar + URL state. KHÔNG giữ orders trong tab của `/profile/page.tsx`.
- **D-09:** Tab 'orders' trong `/profile/page.tsx` → **redirect** `router.push('/profile/orders')` khi click. Profile page giữ tab nhưng delegate sang dedicated route.
- **D-10:** `/profile/orders` hiển thị filter bar với: status dropdown (Tất cả / Pending / Confirmed / Shipping / Delivered / Cancelled) + date range (from/to native `<input type="date">`) + keyword search input.
- **D-11:** Filter state encode trong URL: `?status=DELIVERED&from=2026-04-01&to=2026-04-30&q=ORD-123`. Back/forward browser preserve filter (SC-4).

### Order filter: server-side (ACCT-02)

- **D-12:** Filtering xử lý **server-side** — backend nhận query params (status, from, to, q), query DB. Client gửi params lên API, không filter trên browser.
- **D-13:** Keyword search tìm trên **order ID only** (search `order.id ILIKE ?q?`) — không join order items / product name. Đủ cho demo scope, index-friendly.
- **D-14:** Date range timezone: client gửi date string `2026-04-30`, backend interpret là `2026-04-30T00:00:00+07:00` → `2026-04-30T23:59:59+07:00` (full day UTC+7). Đảm bảo SC-5: đơn 23:59 GMT+7 ngày 30/4 không bị miss khi filter "tháng 4".
- **D-15:** Pagination giữ nguyên offset-based (page/size params), filter params stack thêm vào query string.

### Claude's Discretion

- Validation regex cho phone trong address form — dùng VN phone pattern `^(0|\+84)[3-9]\d{8}$` hoặc loose `^\+?[\d\s-]{7,20}$`; Claude chọn theo consistency với Phase 10 phone validation
- UI display format cho address trong dropdown: `{fullName} — {street}, {ward}, {district}, {city}`; Claude format cho gọn
- Error handling khi fetch addresses fail ở checkout — silent (hide picker button) hay show error toast; Claude chọn option ít disruptive hơn
- Backend sort order cho addresses list — default by `created_at DESC` hoặc `is_default DESC, created_at DESC`; Claude chọn option user-friendly hơn

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents PHẢI đọc các file dưới trước khi plan/implement.**

### Project-level
- `.planning/PROJECT.md` — visible-first priority, demo distributed system
- `.planning/REQUIREMENTS.md` §ACCT-02, §ACCT-05, §ACCT-06 — full requirement spec
- `.planning/ROADMAP.md` §Phase 11 — Goal + 5 Success Criteria + Flyway V-number table
- `.planning/STATE.md` — locked decisions carry-over (address cap 10, ADDRESS_LIMIT_EXCEEDED)

### Research artifacts (v1.2)
- `.planning/research/PITFALLS.md` — pitfalls chung, tránh lặp lại lỗi
- `.planning/research/STACK.md` — tech stack reference

### Codebase intel
- `.planning/codebase/CONVENTIONS.md` — ApiResponse envelope, error code pattern
- `.planning/codebase/INTEGRATIONS.md` — gateway routes, service-to-service

### Existing code (đụng tới Phase 11)
- `sources/backend/user-service/src/main/resources/db/migration/` — V1, V2 tồn tại; V4 cần tạo `V4__create_addresses.sql`
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/UserMeController.java` — pattern JWT extraction → tham khảo cho AddressController
- `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/web/JwtRoleGuard.java` — extractUserIdFromBearer pattern
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderEntity.java` — shippingAddress JSONB String field (đã có)
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/` — listMyOrders endpoint cần extend filter params
- `sources/frontend/src/app/checkout/page.tsx` — manual form hiện tại; thêm AddressPicker dropdown phía trên
- `sources/frontend/src/app/profile/page.tsx` — tab 'orders' sẽ redirect, tab 'addresses' sẽ redirect hoặc remove
- `sources/frontend/src/app/profile/orders/[id]/page.tsx` — order detail page (không đụng)
- `sources/frontend/src/services/orders.ts` — extend listMyOrders() thêm filter params
- `sources/frontend/src/services/users.ts` — thêm address CRUD functions (listAddresses, createAddress, updateAddress, deleteAddress, setDefaultAddress)

### Prior phase context
- Phase 10 D-03: Toast "Đã cập nhật" pattern — áp dụng cho address CRUD success messages
- Phase 10 D-07: localStorage + router.refresh() pattern — tham khảo nếu cần state sync
- Phase 9 D-05: JWT Bearer → claims.sub pattern — AddressController dùng same approach

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserMeController.extractUserIdFromBearer()` — pattern lấy userId từ JWT, tái dụng trong AddressController
- `JwtRoleGuard` — Bearer token parse, tham khảo cho address endpoints auth
- `Modal` component (`src/components/ui/Modal/`) — dùng cho address create/edit form modal
- `Button`, `Input`, `Badge`, `RetrySection` — đã có, dùng trong `/profile/addresses` page
- `useToast()` hook — toast success/error cho address CRUD
- `httpGet`, `httpPost`, `httpPatch`, `httpDelete` — pattern từ `services/http.ts`

### Established Patterns
- `ApiResponse<T>` envelope — tất cả backend response đều wrap
- Error codes dạng string `ADDRESS_LIMIT_EXCEEDED` (422) — consistent với codebase
- rhf + zod form pattern (Phase 10) — áp dụng cho address create/edit form
- Pagination offset-based với `page/size/sort` params — `listMyOrders()` đã có pattern
- `PaginatedResponse<T>` type — dùng cho address list response
- `useSearchParams()` + `router.push()` — Next.js URL state encoding cho filter bar

### Integration Points
- Gateway route cần forward `/api/users/addresses` → user-svc (check `api-gateway/application.yml`)
- Order-svc backend cần extend `GET /orders` với filter params (status, from, to, q)
- Checkout page tích hợp AddressPicker: fetch addresses khi user logged-in, render dropdown

</code_context>

<specifics>
## Specific Ideas

- AddressPicker snap-fill: button label "Địa chỉ đã lưu ▼" phía trên form checkout (trước fullName field)
- Empty state AddressPicker: link sang `/profile/addresses` — cross-sell tự nhiên, không disruptive
- Filter bar layout: status dropdown + date from/to native inputs + search input — 1 hàng ngang trên desktop, stack dọc trên mobile
- Date range timezone handling: frontend gửi YYYY-MM-DD, backend add timezone offset server-side (không trust client timezone)

</specifics>

<deferred>
## Deferred Ideas

- **Label/nickname cho address** ("Nhà", "Văn phòng") — user quyết định không cần, defer nếu cần v1.3
- **Order keyword search trên product name** — ACCT-02 đề cập nhưng user chọn order ID only cho demo scope; defer v1.3 nếu cần JOIN order items
- **Client-side filtering** — rejected, server-side đảm bảo timezone correctness (SC-5)
- **Manual form hidden khi có addresses** (Picker thay thế form) — rejected, snap-fill đơn giản hơn

</deferred>

---

*Phase: 11-address-book-order-history-filtering*
*Context gathered: 2026-04-27*
