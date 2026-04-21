# Business Overview — Tech Store

## Tóm tắt
TechStore là nền tảng TMĐT B2C single-vendor bán đồ công nghệ (điện thoại, laptop, smartwatch, phụ kiện) tại Việt Nam. Mục tiêu: cung cấp trải nghiệm mua sắm nhanh, tin cậy, giá minh bạch cho khách hàng am hiểu công nghệ.

## Context Links
- User journeys: [01-user-journeys.md](./01-user-journeys.md)
- Business rules: [02-business-rules.md](./02-business-rules.md)
- Architecture: [../architecture/00-overview.md](../architecture/00-overview.md)

## Vision
Trở thành điểm đến tin cậy cho khách hàng Việt Nam khi mua đồ công nghệ, với chính sách giá cạnh tranh, giao hàng nhanh, và dịch vụ hậu mãi chuẩn chỉnh.

## Value Proposition
| Value | Mô tả |
|---|---|
| Giá minh bạch | Không bid ẩn, hiển thị đầy đủ giá gốc + giá sale + khuyến mãi áp dụng |
| Sản phẩm chính hãng | Cam kết 100% chính hãng, có tem niêm phong, hóa đơn VAT |
| Giao nhanh | 2h nội thành HCM/HN, 1-3 ngày tỉnh khác |
| Thanh toán linh hoạt | VNPay (ATM, Visa/Master, QR) + COD |
| Hậu mãi rõ ràng | 30 ngày đổi trả, bảo hành chính hãng |

## Target Users

### Primary: Customer (Khách hàng cá nhân)
- Độ tuổi: 20-45, thành thị, mức thu nhập trung-cao
- Nhu cầu: mua 1-2 món/lần (điện thoại, laptop) hoặc phụ kiện (tai nghe, sạc)
- Hành vi: tra cứu spec, so sánh giá, đọc review trước khi mua
- Device: 70% mobile, 30% desktop

### Secondary: Admin (Quản trị nội bộ)
- Role: quản lý sản phẩm, tồn kho, duyệt đơn, xử lý đổi/trả
- Số lượng: 3-10 người
- Yêu cầu: giao diện admin gọn, bulk action, export CSV/Excel

## KPIs

| KPI | Target | Metric |
|---|---|---|
| Conversion rate (guest → order) | >= 2.5% | Orders / Unique visitors |
| Cart abandonment rate | <= 65% | Carts không checkout / Carts tạo ra |
| Average Order Value (AOV) | >= 8,000,000 VND | Revenue / Orders |
| Repurchase rate (30 ngày) | >= 15% | Returning customers / New customers |
| On-time delivery | >= 95% | Đơn giao đúng hẹn / Tổng đơn ship |
| Review rate | >= 25% | Reviews / Delivered orders |
| Page load p95 | <= 2.5s | Real User Monitoring |

## Business Model
- **Revenue**: Bán trực tiếp (margin ~8-18% tuỳ ngành hàng)
- **Cost**: nhập hàng, vận chuyển, payment gateway fee (VNPay ~1.5-2.5%), marketing
- **Không** thu phí hoa hồng (single-vendor, không phải marketplace)

## Scope MVP

### In-scope
- Catalog: 4 category (phone, laptop, smartwatch, accessory) với ~500-2000 SKU
- Auth: email + password, social login (Google) ở v2
- Cart & checkout: guest checkout không bắt buộc (phase 2), auth flow làm trước
- Payment: VNPay (ATM, Visa/Master, QR) + COD
- Order management: PENDING → DELIVERED, cancel trong 1h sau đặt
- Review & rating: chỉ cho order đã DELIVERED
- Admin: CRUD product, stock, order state, block user

### Out-of-scope MVP (backlog)
- Marketplace (multi-vendor)
- Voucher/promotion code phức tạp (chỉ sale giá gốc → salePrice ở MVP)
- Loyalty point
- Wishlist
- Live chat
- Mobile native app
- Affiliate

## Phase Roadmap

| Phase | Timeline | Scope |
|---|---|---|
| MVP | Q1-Q2 | Auth, catalog, cart, checkout (VNPay/COD), order, admin |
| v1.1 | Q3 | Review/Rating, guest checkout, search filter nâng cao |
| v1.2 | Q4 | Wishlist, voucher đơn giản, Google login |
| v2.0 | Year 2 | Loyalty point, live chat, mobile app |
