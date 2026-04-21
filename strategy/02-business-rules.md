# Business Rules — Cross-cutting

## Tóm tắt
Tập hợp business rules áp dụng xuyên suốt hệ thống. Mỗi rule có ID (BR-XXX) để reference từ BA/TS. Rules được chia thành 5 nhóm: pricing, inventory, order lifecycle, payment, refund/return.

## Context Links
- Business overview: [00-business-overview.md](./00-business-overview.md)
- User journeys: [01-user-journeys.md](./01-user-journeys.md)
- Product business: [services/product-business.md](./services/product-business.md)
- Order business: [services/order-business.md](./services/order-business.md)
- User business: [services/user-business.md](./services/user-business.md)

## BR-PRICING — Quy tắc giá

| ID | Rule |
|---|---|
| BR-PRICING-01 | Product có 2 giá: `price` (giá gốc) và `salePrice` (giá sale, optional). Nếu `salePrice` set và < `price` → hiển thị cả 2, tính theo `salePrice`. |
| BR-PRICING-02 | Giá hiển thị = `salePrice ?? price`. Luôn VND, làm tròn đơn vị nghìn. |
| BR-PRICING-03 | Giá lock khi add vào cart: snapshot `price` và `salePrice` lúc add, giữ đến khi checkout. Nếu giá thay đổi giữa chừng → thông báo, user confirm giá mới. |
| BR-PRICING-04 | Thuế: giá hiển thị ĐÃ bao gồm VAT 10%. Không hiển thị ex-VAT. |
| BR-PRICING-05 | Phí ship: tính riêng, hiển thị ở checkout. HCM/HN: 30,000 VND. Tỉnh: 50,000 VND. Đơn >= 3,000,000 VND: miễn phí ship. |
| BR-PRICING-06 | Không áp dụng voucher/discount code ở MVP — chỉ có sale giá gốc → salePrice. |

## BR-INVENTORY — Quy tắc tồn kho

| ID | Rule |
|---|---|
| BR-INVENTORY-01 | Product có field `stock` >= 0 (int). `stock = 0` → hiển thị "Hết hàng", disable add-to-cart. |
| BR-INVENTORY-02 | Khi add-to-cart: KHÔNG giảm stock. Chỉ check `stock > 0`. |
| BR-INVENTORY-03 | Khi checkout (tạo order PENDING): **reserve stock** bằng cách giảm `stock` và tăng `reservedStock`. Event `StockReserved` fire. |
| BR-INVENTORY-04 | Khi order PAID/CONFIRMED: commit stock — xóa khỏi `reservedStock`. Không add lại vào `stock`. |
| BR-INVENTORY-05 | Khi order CANCELLED / payment fail trong 30 phút: **release stock** — trả lại `stock`, xóa `reservedStock`. |
| BR-INVENTORY-06 | `reservedStock` có TTL 30 phút. Sau 30 phút payment chưa xong → auto release (cron job). |
| BR-INVENTORY-07 | Low stock alert cho admin khi `stock <= 5`. Banner "Chỉ còn X sản phẩm" cho customer khi `stock <= 10`. |
| BR-INVENTORY-08 | Stock update chỉ Admin có quyền. Audit log mọi stock change (`stock_log` table). |

## BR-ORDER — Order lifecycle

### States
```
PENDING → PAID → CONFIRMED → PROCESSING → SHIPPED → DELIVERED → COMPLETED
   │                                                      │
   └──→ CANCELLED (trước CONFIRMED)                        └──→ REFUNDED (sau DELIVERED, trong 30 ngày)
```

| ID | Rule |
|---|---|
| BR-ORDER-01 | Order states: PENDING, PAID, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, COMPLETED, CANCELLED, REFUNDED. |
| BR-ORDER-02 | PENDING: vừa tạo, chờ payment. Auto CANCELLED sau 30 phút không paid. |
| BR-ORDER-03 | PAID: VNPay callback success HOẶC COD thì skip PAID, next là CONFIRMED. |
| BR-ORDER-04 | CONFIRMED: Admin xác nhận, sẵn sàng xử lý. Không quay lại PENDING. |
| BR-ORDER-05 | PROCESSING: Đang đóng gói. |
| BR-ORDER-06 | SHIPPED: Đã giao shipper. Có `trackingCode`. |
| BR-ORDER-07 | DELIVERED: Khách nhận. Cho phép review sản phẩm. |
| BR-ORDER-08 | COMPLETED: Sau DELIVERED 7 ngày auto COMPLETED (không còn quyền refund). |
| BR-ORDER-09 | CANCELLED: Chỉ customer cancel được khi state in [PENDING]. Admin cancel được đến [CONFIRMED]. Sau CONFIRMED phải liên hệ hotline. |
| BR-ORDER-10 | REFUNDED: Admin tạo, chỉ từ DELIVERED trong 30 ngày. |
| BR-ORDER-11 | Mỗi state transition → Kafka event `OrderStateChanged`. |
| BR-ORDER-12 | Order không thể edit items sau PENDING. Chỉ address có thể edit đến CONFIRMED. |

## BR-PAYMENT — Thanh toán

| ID | Rule |
|---|---|
| BR-PAYMENT-01 | Methods: VNPAY, COD. |
| BR-PAYMENT-02 | VNPay flow: Tạo order PENDING → redirect VNPay với `TxnRef = orderId` → user pay → callback → verify signature HMAC-SHA512 → update order PAID. |
| BR-PAYMENT-03 | VNPay timeout: 15 phút ở gateway, 30 phút total (order auto CANCELLED). |
| BR-PAYMENT-04 | COD: order tạo → skip PENDING payment check → sang CONFIRMED ngay khi admin duyệt. Phí COD: 20,000 VND/đơn < 1,000,000 VND; miễn phí >= 1,000,000. |
| BR-PAYMENT-05 | Idempotency: VNPay callback có thể retry. Key = `vnp_TxnRef + vnp_ResponseCode`. Đã xử lý → trả success ngay, không double-update. |
| BR-PAYMENT-06 | Payment fail (VNPay return code != 00): order → CANCELLED, stock release. |
| BR-PAYMENT-07 | Tất cả amount truyền VNPay phải × 100 (VNPay dùng unit = xu). |
| BR-PAYMENT-08 | Signature verification: sort params alphabet, concat `key=value&...`, HMAC-SHA512 với secret key. Mọi param trừ `vnp_SecureHash`. |

## BR-REFUND — Đổi/Trả

| ID | Rule |
|---|---|
| BR-REFUND-01 | Customer yêu cầu refund trong 30 ngày từ khi DELIVERED. |
| BR-REFUND-02 | Refund chỉ áp dụng sản phẩm: (a) lỗi do NSX/vận chuyển, (b) không đúng mô tả, (c) khách đổi ý (trong 7 ngày, sản phẩm nguyên seal). |
| BR-REFUND-03 | Refund amount = order total (trừ phí ship nếu khách đổi ý). |
| BR-REFUND-04 | Refund method = payment method gốc. VNPay → hoàn về thẻ (manual ở MVP). COD → chuyển khoản/tiền mặt. |
| BR-REFUND-05 | Admin duyệt refund. Order → REFUNDED. Stock KHÔNG trả lại (sản phẩm hoàn có thể hỏng). |

## BR-USER — User & Auth

| ID | Rule |
|---|---|
| BR-USER-01 | Roles: CUSTOMER (mặc định), ADMIN (set manual qua DB). Không có registration admin qua UI. |
| BR-USER-02 | Email unique, validated format RFC 5322. |
| BR-USER-03 | Password: >= 8 ký tự, có ít nhất 1 chữ + 1 số. Hash bcrypt cost 12. |
| BR-USER-04 | Login fail 5 lần trong 15 phút → lock 30 phút. |
| BR-USER-05 | JWT access token TTL 1 giờ. Refresh token TTL 7 ngày (lưu DB). Logout = revoke refresh token. |
| BR-USER-06 | Reset password: gửi email link với token TTL 15 phút. |
| BR-USER-07 | Blocked user (admin block): không login được, thông báo "Tài khoản bị khóa, liên hệ support". |
| BR-USER-08 | Profile: email không đổi được (unique identity). Name, phone, avatar đổi được. |
| BR-USER-09 | Address book: max 5 địa chỉ/user. 1 default. |

## BR-PRODUCT — Catalog

| ID | Rule |
|---|---|
| BR-PRODUCT-01 | Product statuses: DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK. Chỉ ACTIVE hiển thị cho customer. |
| BR-PRODUCT-02 | SKU unique. Format: `{brand}-{model}-{variant}` (VD: `APPLE-IP15PRO-256`). |
| BR-PRODUCT-03 | Slug auto-generate từ name, unique. Kebab-case ASCII (bỏ dấu tiếng Việt). |
| BR-PRODUCT-04 | Images: >= 1 image, max 10. Primary image bắt buộc. Size <= 2MB mỗi file. |
| BR-PRODUCT-05 | Specs: JSON object, key-value tự do tùy category (VD phone: `ram`, `storage`, `screen_size`, `battery`). |
| BR-PRODUCT-06 | Category tree: 2 levels (parent: "Điện thoại" → child: "iPhone", "Samsung", ...). |
| BR-PRODUCT-07 | Soft delete: product không xóa cứng, chỉ INACTIVE (giữ order history reference). |

## BR-REVIEW — Review & Rating

| ID | Rule |
|---|---|
| BR-REVIEW-01 | Chỉ user có order DELIVERED chứa sản phẩm mới review được. |
| BR-REVIEW-02 | 1 user x 1 sản phẩm x 1 order = 1 review (không duplicate). |
| BR-REVIEW-03 | Rating 1-5 sao (int), comment tối đa 1000 ký tự. |
| BR-REVIEW-04 | Review hiển thị ngay (không cần duyệt ở MVP). Admin có thể hide nếu vi phạm. |
| BR-REVIEW-05 | Product rating = average rating all reviews, làm tròn 1 chữ số thập phân. |
| BR-REVIEW-06 | User có thể edit review trong 24h sau post. Sau đó khóa. |

## Mapping Rule → UC

| Rule group | UC liên quan |
|---|---|
| BR-PRICING | UC-PRODUCT-BROWSE, UC-CART, UC-CHECKOUT-PAYMENT |
| BR-INVENTORY | UC-PRODUCT-BROWSE, UC-CART, UC-CHECKOUT-PAYMENT, UC-ADMIN-PRODUCT |
| BR-ORDER | UC-CHECKOUT-PAYMENT, UC-ORDER-TRACKING, UC-ADMIN-ORDER |
| BR-PAYMENT | UC-CHECKOUT-PAYMENT |
| BR-REFUND | UC-ORDER-TRACKING, UC-ADMIN-ORDER |
| BR-USER | UC-AUTH, UC-USER-PROFILE, UC-ADMIN-USER |
| BR-PRODUCT | UC-PRODUCT-BROWSE, UC-ADMIN-PRODUCT |
| BR-REVIEW | UC-PRODUCT-REVIEW |
