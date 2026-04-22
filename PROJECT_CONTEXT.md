# THÔNG TIN DỰ ÁN (PROJECT CONTEXT) - v2

## 1. Tổng quan
- Lĩnh vực: Thương mại điện tử (single-vendor tech store).
- Kiến trúc mục tiêu: FE + API Gateway + 6 backend services.
- Mục tiêu tài liệu: giúp AI map đúng service ownership trước khi đề xuất code.

## 2. Kiến trúc hệ thống hiện tại
### Frontend
- 1 ứng dụng NextJS (TypeScript) cho customer và admin.
- Tất cả API call đi qua API Gateway.

### Backend
- `api-gateway`: edge routing, CORS, auth/rate-limit (target design).
- `user-service`: auth, profile, address, user admin.
- `product-service`: catalog, category, review.
- `order-service`: cart, order lifecycle.
- `payment-service`: payment session, VNPay return/IPN, payment state.
- `inventory-service`: reserve/commit/release stock.
- `notification-service`: gửi notification async theo event.

## 3. Quy tắc ownership bắt buộc
1. Không đặt payment callback logic trong order-service.
2. Không đặt stock mutation trong product-service.
3. Notification là async side effect, không chặn request path.
4. Không service nào truy cập DB của service khác.

## 4. Integration pattern
- Sync: REST qua Gateway.
- Async: domain events (order, payment, inventory, user) -> consumers.
- Các flow quan trọng cần idempotency: checkout và payment callback.

## 5. Trạng thái hiện tại
- Code backend đang ở mức skeleton cho nhiều service.
- Docker compose đã có đủ 7 thành phần backend (gateway + 6 services).
- Tài liệu strategy/ba/architecture đã cập nhật theo boundary mới.

## 6. Nguyên tắc cho AI khi làm task
1. Đọc theo thứ tự: PROJECT_CONTEXT -> README -> _index -> UC -> architecture service docs.
2. Nếu task không rõ domain, xác định owner service trước rồi mới thiết kế API/code.
3. Nếu endpoint chưa có trong code, ghi rõ planned contract thay vì giả định đã implement.
4. Ưu tiên cập nhật docs khi thay đổi boundary hoặc event contracts.
