---
status: partial
phase: 26-vnpay-payment-integration
source: [26-VERIFICATION.md]
started: 2026-05-22T00:00:00Z
updated: 2026-05-22T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live VNPay Sandbox Redirect
expected: Chọn VNPAY tại /checkout → đặt hàng → trình duyệt redirect sang https://sandbox.vnpayment.vn với đúng params (vnp_Amount, vnp_TxnRef, vnp_SecureHash). Cần merchant credentials thật (VNP_TMN_CODE + VNP_HASH_SECRET) + docker stack chạy.
result: [pending]

### 2. IPN Callback Server-to-Server
expected: VNPay POST IPN tới /api/payments/vnpay/ipn (qua tunnel công khai), BE verify HMAC SHA512, cập nhật payment_status=PAID, phát PaymentSucceeded event, order-service consume và cập nhật đơn. Cần tunnel (ngrok/cloudflared) + IPN URL cấu hình tại merchant dashboard sandbox.
result: [pending]

### 3. Return Page Polling Flow
expected: Sau redirect về từ VNPay, trang /checkout/result hiển thị "Đang xác nhận" → poll mỗi 3s → khi IPN về cập nhật thành "Thanh toán thành công" / "Thanh toán thất bại". Cần luồng real-time VNPay sandbox → IPN → BE → FE poll.
result: [pending]

### 4. Order Display với Dữ Liệu Thật
expected: /profile/orders/[id] và /admin/orders/[id] hiển thị paymentMethod="Thanh toán qua VNPay", paymentStatus="Đã thanh toán", mã giao dịch VNPay thực tế. Cần vnpTransactionNo thật từ giao dịch sandbox.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
