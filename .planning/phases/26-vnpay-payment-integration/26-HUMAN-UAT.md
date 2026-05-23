---
status: partial
phase: 26-vnpay-payment-integration
source: [26-VERIFICATION.md]
started: 2026-05-22T00:00:00Z
updated: 2026-05-23T00:00:00Z
---

## Current Test

[3/4 verified bằng Playwright E2E mock; còn 1 manual item cần VNPay sandbox credentials thật]

## Tests

### 1. Live VNPay Sandbox Redirect
expected: Chọn VNPAY tại /checkout → đặt hàng → trình duyệt redirect sang https://sandbox.vnpayment.vn với đúng params (vnp_Amount, vnp_TxnRef, vnp_SecureHash). Cần merchant credentials thật (VNP_TMN_CODE + VNP_HASH_SECRET) + docker stack chạy.
result: [partial] 2026-05-23 — Playwright E2E xác nhận option "Thanh toán qua VNPay" hiển thị trong /checkout selector (PAY-01 UI). Phần redirect thật sang sandbox.vnpayment.vn vẫn cần merchant credentials.

### 2. IPN Callback Server-to-Server
expected: VNPay POST IPN tới /api/payments/vnpay/ipn (qua tunnel công khai), BE verify HMAC SHA512, cập nhật payment_status=PAID, phát PaymentSucceeded event, order-service consume và cập nhật đơn. Cần tunnel (ngrok/cloudflared) + IPN URL cấu hình tại merchant dashboard sandbox.
result: [pending]

### 3. Return Page Polling Flow
expected: Sau redirect về từ VNPay, trang /checkout/result hiển thị "Đang xác nhận" → poll mỗi 3s → khi IPN về cập nhật thành "Thanh toán thành công" / "Thanh toán thất bại". Cần luồng real-time VNPay sandbox → IPN → BE → FE poll.
result: [passed] 2026-05-23 — Playwright E2E mock vnp_ResponseCode=00 → heading "Đang xác nhận thanh toán" + poll PENDING orderId; mock vnp_ResponseCode=24 → heading "Đã huỷ thanh toán". Logic FE hoạt động đúng D-13.

### 4. Order Display với Dữ Liệu Thật
expected: /profile/orders/[id] và /admin/orders/[id] hiển thị paymentMethod="Thanh toán qua VNPay", paymentStatus="Đã thanh toán", mã giao dịch VNPay thực tế. Cần vnpTransactionNo thật từ giao dịch sandbox.
result: [pending]

## Summary

total: 4
passed: 1
issues: 0
pending: 2
skipped: 0
blocked: 0
partial: 1

## Gaps
