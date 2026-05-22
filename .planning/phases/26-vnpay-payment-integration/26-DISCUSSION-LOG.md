# Phase 26: Tích Hợp Thanh Toán VNPay Sandbox - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-22
**Phase:** 26-vnpay-payment-integration
**Areas discussed:** Kiến trúc service, IPN → cập nhật đơn, Đơn PENDING & tồn kho, Trang kết quả thanh toán, Routing IPN

---

## Khung định hướng

Người dùng chỉ đạo: "phải đi theo kiến trúc của hệ thống có sẵn". Do đó các vùng "Kiến trúc service" và "IPN → cập nhật đơn" được khóa trực tiếp theo `architecture/services/payment-service.md`, `order-service.md`, và `ba/order-service/uc-checkout-payment.md` mà không cần hỏi lại. Chỉ những điểm kiến trúc chưa nói rõ mới đưa ra hỏi.

## Đơn PENDING & tồn kho

| Option | Description | Selected |
|--------|-------------|----------|
| Trì hoãn OrderPlaced đến khi PAID | Đơn VNPAY chỉ phát OrderPlaced (→ trừ kho) sau PaymentSucceeded; COD phát ngay; không sửa inventory-service | ✓ |
| Giữ nguyên - trừ kho khi tạo đơn | Phát OrderPlaced ngay, chấp nhận leak kho khi thất bại | |
| Reserve/commit/release 2 pha đầy đủ | Viết lại inventory-service theo arch v2 — phạm vi lớn | |

**User's choice:** Trì hoãn OrderPlaced đến khi PAID
**Notes:** Tránh leak kho mà không phải viết lại inventory-service; mô hình 2 pha defer sang phase riêng.

## Trang kết quả thanh toán

| Option | Description | Selected |
|--------|-------------|----------|
| Poll payment_status sau redirect | Render sơ bộ theo vnp_ResponseCode rồi poll order chờ IPN xác nhận | ✓ |
| Chỉ hiển theo vnp_ResponseCode | Chỉ dựa return URL, không poll | |

**User's choice:** Poll payment_status sau redirect
**Notes:** return URL không phải nguồn sự thật — poll để chờ IPN xác nhận trạng thái thật.

## Routing IPN

| Option | Description | Selected |
|--------|-------------|----------|
| Qua gateway, route public | Gateway route /payments/vnpay/ipn + /return công khai không cần JWT | |
| Bạn quyết | Để researcher/planner chọn cách phù hợp cấu hình gateway | ✓ |

**User's choice:** Bạn quyết (Claude's Discretion)
**Notes:** Ràng buộc Phase 25 vẫn áp dụng — VNPay bên ngoài không gửi được JWT nên endpoint phải truy cập được không cần JWT, bảo mật dựa trên verify vnp_SecureHash.

## Claude's Discretion

- Định tuyến IPN qua gateway (D-15)
- Cấu hình VNPay sandbox đọc từ env
- Số lần / khoảng thời gian poll trang kết quả
- Tên route FE trang kết quả + chi tiết UI

## Deferred Ideas

- Inventory reserve/commit/release 2 pha (arch v2) — phase riêng tương lai
- Hoàn kho khi đơn VNPAY thất bại sau khi đã trừ
- PaymentExpired / dọn đơn PENDING quá hạn
