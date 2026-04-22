# Business Overview - Tech Store (v2)

## Tóm tắt
Tech Store là nền tảng TMĐT B2C single-vendor bán đồ công nghệ tại Việt Nam. Phiên bản tài liệu này chuẩn hóa kiến trúc thành FE + API Gateway + 6 backend services để tăng khả năng mở rộng và vận hành ổn định.

## Context Links
- User journeys: [01-user-journeys.md](./01-user-journeys.md)
- Business rules: [02-business-rules.md](./02-business-rules.md)
- Architecture overview: [../architecture/00-overview.md](../architecture/00-overview.md)

## Vision
Trở thành điểm đến mua đồ công nghệ đáng tin cậy với trải nghiệm mua nhanh, thanh toán an toàn, và theo dõi đơn minh bạch.

## Value Proposition
| Value | Mô tả |
|---|---|
| Giá minh bạch | Hiển thị rõ giá gốc, giá sale, phí ship, phí COD |
| Vận hành ổn định | Đơn hàng có vòng đời rõ, xử lý thanh toán và tồn kho tách biệt |
| Khả năng mở rộng | Tách service theo domain: user, product, order, payment, inventory, notification |

## Service Landscape
| Layer | Thành phần | Vai trò |
|---|---|---|
| Frontend | nextjs-frontend | UI cho customer/admin |
| Edge | api-gateway | Auth edge, route, rate-limit, CORS, observability |
| Core | user-service | Auth, profile, address, user admin |
| Core | product-service | Catalog, category, review/rating |
| Core | order-service | Cart, order lifecycle, admin order actions |
| Core | payment-service | Payment session, VNPay return/IPN, payment state |
| Core | inventory-service | Reserve/commit/release stock, low-stock policy |
| Core | notification-service | Gửi email/system notification theo event |

## KPI mục tiêu
| KPI | Target |
|---|---|
| Checkout conversion | >= 2.5% |
| Payment success (paid flow) | >= 95% |
| Inventory consistency | >= 99.9% |
| Notification delivery success | >= 98% |
| API latency p95 | <= 500ms |

## Scope MVP v2
### In scope
- Auth, profile, address.
- Catalog, review.
- Cart, checkout, VNPay/COD.
- Inventory reserve/commit/release tách service.
- Payment state tách service.
- Notification sự kiện: register, order milestones.
- Admin product/order/user.

### Out of scope
- Voucher engine phức tạp.
- Loyalty points.
- Marketplace multi-vendor.
- Recommendation realtime.

## Nguyên tắc cho AI
1. Luôn map yêu cầu vào service ownership trước khi code.
2. Không đặt logic payment trong order-service.
3. Không đặt logic mutation stock trong product-service.
4. Notification là consumer event, không chặn luồng đặt hàng.
5. Khi API chưa có trong code, ghi rõ planned contract.
