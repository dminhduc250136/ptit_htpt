---
status: resolved
slug: add-cart-409-shows-success
trigger: "Thêm vào giỏ hàng — UI hiện toast thành công nhưng giỏ hàng rỗng. Network: POST /api/orders/cart/items trả 409 CONFLICT (domainCode=STOCK_SHORTAGE, available=0 cho prod-pho-002)."
created: 2026-05-03
updated: 2026-05-03
---

## Symptoms

- **Expected**: Khi backend trả 409 STOCK_SHORTAGE → UI phải hiện toast/banner lỗi rõ ràng (vd "Sản phẩm hết hàng"), KHÔNG được thêm vào cart, KHÔNG hiện toast success.
- **Actual**: UI hiện toast "Thêm vào giỏ thành công" nhưng cart thực tế rỗng (vì backend từ chối).
- **Network response (verbatim)**:
  ```json
  {
    "timestamp": "2026-05-03T01:51:24Z",
    "status": 409,
    "message": "Conflict",
    "data": {
      "domainCode": "STOCK_SHORTAGE",
      "code": "CONFLICT",
      "message": "Một số sản phẩm không đủ số lượng trong kho",
      "items": [{"productId":"prod-pho-002","productName":"prod-pho-002","requested":1,"available":0}]
    }
  }
  ```
- **Endpoint**: POST http://localhost:8080/api/orders/cart/items
- **Reproduction**: Đăng nhập → vào trang detail sản phẩm `prod-pho-002` → bấm "Thêm vào giỏ" → toast success xuất hiện sai.

## Suspected Areas

- `services/cart.ts._serverAdd` — httpPost throw ApiError 409, nhưng caller có thể catch sai và rơi vào nhánh success.
- `hooks/useCart.ts` — mutation onSuccess vs onError handler có thể nuốt 409 hoặc treat ApiError as success.
- `app/products/[slug]/page.tsx` — handler addToCart có thể `await` rồi luôn show success toast bất kể throw.
- Envelope parsing trong `http.ts`: với 409, `parsed.data` (chứa `domainCode`, `items`) có thể không match `ApiErrorBody` shape.

## Current Focus

hypothesis: "Hai bug độc lập cộng dồn: (1) caller product detail page gọi `addToCart()` thiếu `await` → promise rejection thoát try/catch sync, success toast luôn chạy; (2) `http.ts` parse failure envelope từ ROOT thay vì từ `data` → `err.code` undefined, fallback `INTERNAL_ERROR`, mất `details.items`."
test: "Đọc http.ts non-OK parse, products/[slug]/page.tsx onClick handler, useCart parseCartError, cart.ts _serverAdd."
expecting: "Xác định cả hai điểm sai và sửa cùng lúc."
next_action: "(resolved)"

## Evidence

- timestamp: 2026-05-03 — `app/products/[slug]/page.tsx:248-269` — `onClick={async () => { ... try { addToCart(...); showToast('success') } catch { ... } }`. **`addToCart` được gọi KHÔNG có `await`** (dù hàm là async). Promise rejection (vd 409) thoát ra ngoài sync try-block dưới dạng unhandled promise rejection. Nhánh `catch` không bao giờ chạy → toast success luôn hiển thị bất kể backend phản hồi gì. Đây là root cause CHÍNH của triệu chứng.
- timestamp: 2026-05-03 — `services/http.ts:131-167` — `const err = (parsed ?? {}) as Partial<ApiErrorBody>` lấy field từ ROOT object. Nhưng backend envelope wrap error body trong `data`: `{ status: 409, message: "Conflict", data: { code, message, domainCode, items } }`. Kết quả: `err.code === undefined` → ApiError được tạo với `code='INTERNAL_ERROR'` (fallback dòng 160), `err.details` undefined → mất hoàn toàn thông tin `domainCode='STOCK_SHORTAGE'` và mảng `items`. Đây là root cause THỨ HAI: kể cả nếu Bug #1 được fix, message sẽ là "Lỗi mạng — vui lòng thử lại" thay vì STOCK_SHORTAGE rõ ràng.
- timestamp: 2026-05-03 — `hooks/useCart.ts:37-53` `parseCartError` đọc `err.details.items` đúng — không có bug ở đây, chỉ chờ http.ts trả về `details` đúng shape.
- timestamp: 2026-05-03 — `app/cart/page.tsx:25-44` — cart page +/- buttons dùng `useUpdateCartItem` mutation với `onError: (err) => { showToast(parseCartError(err).message, 'error') }`. Sau khi http.ts fix, đường này tự động đúng (parseCartError sẽ thấy `code='STOCK_SHORTAGE'` và message tiếng Việt). KHÔNG có bug ở cart page.

## Eliminated

- `useAddToCart` hook (useCart.ts:65-74) không liên quan vì product detail page gọi trực tiếp `addToCart` từ services (không qua mutation hook).
- Backend envelope KHÔNG sai — đây là contract chuẩn của Phase 3 (D-05..D-07 wrap mọi response vào `data`). FE phải parse cho đúng.

## Resolution

**root_cause:** Hai bug độc lập tại frontend: (1) `app/products/[slug]/page.tsx` gọi `addToCart()` async thiếu `await` nên promise rejection thoát sync try/catch và `showToast('success')` luôn chạy; (2) `services/http.ts` parse failure envelope từ root object thay vì từ `data`, khiến `err.code` luôn fallback `'INTERNAL_ERROR'` và `err.details.items` (STOCK_SHORTAGE chi tiết) bị mất.

**fix:**
- `sources/frontend/src/app/products/[slug]/page.tsx`: thêm `await` trước `addToCart(...)`; mở rộng `catch (err)` để map `STOCK_SHORTAGE` → "Sản phẩm không đủ tồn kho (productName: còn N)" và `UNAUTHORIZED` → "Phiên đăng nhập hết hạn".
- `sources/frontend/src/services/http.ts`: refactor failure envelope parse để ưu tiên `parsed.data` (inner) cho `code/message/fieldErrors/traceId/path`, và set `err.details = inner` để chứa nguyên payload (bao gồm `items`). Promote `inner.domainCode` (vd `STOCK_SHORTAGE`) lên `err.code` để FE switch theo nghiệp vụ.
- `app/cart/page.tsx` (+/- buttons): không cần sửa — sau http.ts fix, `parseCartError` tự nhận `code='STOCK_SHORTAGE'` và `details.items` đúng.

**verification:** TypeScript `tsc --noEmit` không phát sinh lỗi mới ở các file đã sửa (chỉ còn lỗi pre-existing không liên quan ở chat/admin routes). Manual UAT cần: (a) đăng nhập, mở `prod-pho-002` (stock=0 backend), bấm "Thêm vào giỏ" → expect toast lỗi STOCK_SHORTAGE chi tiết, KHÔNG có toast success, cart vẫn rỗng; (b) sản phẩm bình thường vẫn add OK + toast success; (c) tăng quantity ở cart page khi vượt stock → toast "Số lượng vượt quá tồn kho".
