# Bộ kiểm thử E2E — TMDT (Playwright)

Bộ test E2E phủ toàn dự án, tổ chức theo **module nghiệp vụ**. Chạy trên **full stack thật**
(docker-compose: frontend + 7 microservices Spring Boot + PostgreSQL).

## Cấu trúc

| File | Module | Phủ |
|------|--------|-----|
| `01-auth.spec.ts` | Xác thực | Đăng ký, đăng nhập, đăng xuất, role gate, returnTo |
| `02-catalog.spec.ts` | Danh mục | Trang chủ, list SP, lọc category/brand/giá, tìm kiếm, chi tiết SP, 404 |
| `03-cart.spec.ts` | Giỏ hàng | Thêm/sửa/xóa item, tóm tắt, empty state |
| `04-checkout.spec.ts` | Thanh toán | Address picker, coupon, payment method, đặt hàng, validation |
| `05-orders.spec.ts` | Đơn hàng | Danh sách, lọc trạng thái/ngày, chi tiết, bảo mật IDOR |
| `06-profile.spec.ts` | Tài khoản | Profile, settings, đổi mật khẩu, CRUD địa chỉ |
| `07-reviews.spec.ts` | Đánh giá | Xem/viết/sửa/xóa review, eligibility, sắp xếp |
| `08-admin.spec.ts` | Quản trị | Dashboard, low-stock, CRUD sản phẩm/đơn/người dùng/coupon, kiểm duyệt review |
| `09-chatbot.spec.ts` | Trợ lý AI | Chat khách hàng, AI suggest reply (admin), edge cases |
| `10-journey.spec.ts` | Hành trình E2E | Luồng mua hàng đầu-cuối + luồng quản trị đầu-cuối |
| `seed-catalog.spec.ts` | Verify seed | Kiểm tra dữ liệu seed catalog đã apply đúng |

Hỗ trợ:
- `global-setup.ts` — đăng nhập user + admin trước khi chạy, lưu `storageState`.
- `utils/helpers.ts` — hằng số + hàm dùng chung (`gotoFirstProduct`, `addCurrentProductToCart`...).
- `utils/mockChatStream.ts` — mock `POST /api/chat/stream` cho test chatbot.

## Triết lý test

- **storageState fixtures**: test không re-login mỗi lần — `global-setup.ts` đăng nhập sẵn
  `user.json` + `admin.json`. Giảm flakiness, tăng tốc.
- **Strategy A (skip-if-no-data)**: khi seed chưa có dữ liệu cần thiết (đơn hàng, review,
  địa chỉ...), test `test.skip()` với lý do rõ ràng thay vì fail. Test không giòn vì
  môi trường seed khác nhau.
- **Chatbot**: tuy chạy full stack, riêng API gọi Anthropic Claude được mock — vì gọi
  AI thật trong E2E tốn phí và không deterministic.

## Cách chạy

```bash
# 1. Khởi động full stack (từ thư mục gốc repo)
docker-compose up -d --build

# 2. Chạy toàn bộ E2E (từ sources/frontend)
cd sources/frontend
npx playwright test

# Chạy 1 module
npx playwright test e2e/03-cart.spec.ts

# Chạy có giao diện
npx playwright test --ui

# Liệt kê test mà không chạy
npx playwright test --list
```

## Credentials seed

Override qua env nếu môi trường khác (`E2E_USER_EMAIL`, `E2E_USER_PASSWORD`,
`E2E_ADMIN_EMAIL`, `E2E_ADMIN_PASSWORD`):

| Vai trò | Email | Mật khẩu |
|---------|-------|----------|
| User | `demo@tmdt.local` | `admin123` |
| Admin | `admin@tmdt.local` | `admin123` |
