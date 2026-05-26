# Báo cáo kiểm thử E2E — Dự án TMDT (E-commerce)

> **Ngày chạy:** 2026-05-19
> **Môi trường:** Full stack thật — `docker-compose` (Next.js frontend + 7 microservices Spring Boot + PostgreSQL)
> **Công cụ:** Playwright 1.59.1 (Chromium), Node.js 24, locale `vi-VN`
> **Phạm vi:** Bộ E2E 10 module nghiệp vụ + verify seed catalog.

---

## 1. Tóm tắt kết quả

| Chỉ số | Giá trị |
|--------|---------|
| **Tổng test case** | **77** |
| ✅ **Đạt (passed)** | **77** |
| ❌ **Lỗi (failed)** | **0** |
| ⏭️ **Bỏ qua (skipped)** | **0** |
| ⏱️ Thời gian chạy | ~2.7 phút (headless) |
| 📊 **Tỷ lệ đạt** | **77/77 = 100%** |

> **Kết luận:** Toàn bộ 77 test case **PASS**. Không còn test nào bị skip — sau khi bổ sung
> dữ liệu seed cho user demo, mọi luồng nghiệp vụ (kể cả giỏ hàng, thanh toán, đánh giá,
> đơn hàng) đều chạy thật đầu-cuối.

---

## 2. Kết quả theo module

| Module | File spec | Pass | Phủ |
|--------|-----------|:----:|-----|
| 01 — Xác thực | `01-auth.spec.ts` | 8/8 | Đăng ký, đăng nhập, đăng xuất, role gate, returnTo |
| 02 — Danh mục SP | `02-catalog.spec.ts` | 9/9 | Trang chủ, list, lọc category/brand/giá, tìm kiếm, chi tiết SP, 404 |
| 03 — Giỏ hàng | `03-cart.spec.ts` | 7/7 | Thêm/sửa/xóa item, tóm tắt, empty state |
| 04 — Thanh toán | `04-checkout.spec.ts` | 6/6 | Address picker, coupon, payment method, đặt hàng, validation |
| 05 — Đơn hàng | `05-orders.spec.ts` | 6/6 | List, lọc trạng thái/ngày, chi tiết đơn, bảo mật IDOR |
| 06 — Tài khoản | `06-profile.spec.ts` | 7/7 | Profile, settings, đổi mật khẩu, CRUD địa chỉ |
| 07 — Đánh giá SP | `07-reviews.spec.ts` | 6/6 | Xem/viết/sửa/xóa review, eligibility, sắp xếp |
| 08 — Quản trị | `08-admin.spec.ts` | 14/14 | Dashboard, CRUD sản phẩm/đơn/người dùng/coupon, kiểm duyệt review |
| 09 — Trợ lý AI | `09-chatbot.spec.ts` | 6/6 | Chat khách hàng, AI suggest reply, edge cases |
| 10 — Hành trình E2E | `10-journey.spec.ts` | 2/2 | Luồng mua hàng + luồng quản trị đầu-cuối |
| 11 — Message Queue | `11-message-queue.spec.ts` | 6 (*) | Topology RabbitMQ, consumer gắn queue, đặt hàng → message drain, DLQ |
| Seed catalog | `seed-catalog.spec.ts` | 8/8 | Verify dữ liệu seed catalog |
| **TỔNG** | **12 file** | **77/77 + 6 (*)** | |

> (*) Module 11 (Phase 23 — Message Queue) là spec mới thêm sau ngày chạy 2026-05-19.
> Kiểm chứng luồng RabbitMQ black-box qua Management HTTP API (port 15672) song song
> thao tác UI đặt hàng. Cần `docker compose up` đủ FE + 7 service + RabbitMQ; mỗi test
> có Strategy A skip khi broker chưa lên. Chạy lại bộ E2E để cập nhật con số tổng.

---k

## 3. Chi tiết toàn bộ 77 test ĐẠT

### Module 01 — Xác thực (8 ✅)
AUTH-01 đăng ký → tự đăng nhập · AUTH-02 email trùng → báo lỗi · AUTH-03 đăng nhập đúng ·
AUTH-04 đăng nhập sai → báo lỗi · AUTH-05 đăng xuất → xóa session · AUTH-06 route bảo vệ →
redirect `/login?returnTo` · AUTH-07 returnTo → quay lại trang đích · AUTH-08 USER vào `/admin` → `/403`.

### Module 02 — Danh mục sản phẩm (9 ✅)
CAT-01 hero + CTA · CAT-02 danh sách SP · CAT-03 lọc category · CAT-04 lọc brand ·
CAT-05 lọc khoảng giá · CAT-06 tìm kiếm trong `/products` · CAT-07 trang `/search` ·
CAT-08 chi tiết SP đủ thông tin · CAT-09 slug không tồn tại → trạng thái lỗi.

### Module 03 — Giỏ hàng (7 ✅)
CART-01 giỏ có item → hiển thị · CART-02 tăng số lượng · CART-03 giảm số lượng ·
CART-04 xóa item · CART-05 tóm tắt tạm tính + tổng tiền · CART-06 nút "Tiến hành thanh toán" ·
CART-07 xóa hết → empty state.

### Module 04 — Thanh toán (6 ✅)
CHK-01 render đủ section · CHK-02 chọn địa chỉ từ AddressPicker · CHK-03 coupon không hợp lệ →
báo lỗi · CHK-04 chọn thanh toán COD · CHK-05 đặt hàng thành công → redirect đơn hàng ·
CHK-06 submit thiếu thông tin → chặn validation.

### Module 05 — Đơn hàng (6 ✅)
ORD-01 danh sách đơn hàng · ORD-02 lọc theo trạng thái · ORD-03 lọc theo ngày ·
ORD-04 chi tiết đơn (bảng items 4 cột + địa chỉ + thanh toán) · **ORD-05 (Bảo mật)** truy cập
đơn hàng ID giả → không lộ dữ liệu (chống IDOR) · ORD-06 tìm mã đơn không tồn tại → empty state.

### Module 06 — Tài khoản (7 ✅)
PRF-01 `/profile` render · PRF-02 sửa thông tin → lưu + persist · PRF-03 đổi mật khẩu sai →
field error · PRF-04 đổi mật khẩu đúng → giữ session · ADDR-01 danh sách địa chỉ ·
ADDR-02 thêm địa chỉ → xuất hiện → dọn dẹp · ADDR-03 xóa địa chỉ → biến mất.

### Module 07 — Đánh giá sản phẩm (6 ✅)
REV-01 tab đánh giá render danh sách · REV-02 user verified-buyer → không thấy hint "chưa mua" ·
REV-03 sửa review của mình · REV-04 xóa review → tạo lại · REV-05 đổi sắp xếp → URL persist `?sort=`.

### Module 08 — Quản trị (14 ✅)
ADM-01 dashboard KPI + chart · ADM-02 đổi khoảng thời gian → refetch · ADM-03 section low-stock ·
ADM-04 danh sách SP · ADM-05 tạo SP → toast · ADM-06 tạo SP thiếu tên → chặn validation ·
ADM-07 danh sách đơn · ADM-08 chi tiết đơn · ADM-09 danh sách người dùng · ADM-10 sửa người dùng ·
ADM-11 danh sách coupon · ADM-12 tạo coupon → toast · ADM-13 trang kiểm duyệt review ·
ADM-14 ẩn review → bỏ ẩn.

### Module 09 — Trợ lý AI / Chatbot (6 ✅)
BOT-01 khách thấy CTA đăng nhập (không FAB) · BOT-02 gửi tin → streaming · BOT-03 render
markdown · BOT-04 lịch sử chat persist sau reload · **BOT-05 (Edge)** lỗi stream → error banner ·
BOT-06 admin tạo gợi ý AI → modal + disclaimer.

### Module 10 — Hành trình E2E (2 ✅)
JOURNEY-1 khách hàng: duyệt → giỏ → checkout → đặt hàng → xem đơn ·
JOURNEY-2 admin: dashboard → quản lý SP → quản lý đơn.

### Seed catalog (8 ✅)
SEED-CAT-1 (×5 category) · SEED-CAT-2 ảnh không lỗi · SEED-CAT-3 brand panel domain-tech.

---

## 4. Quá trình hoàn thiện — từ 50/77 lên 77/77

Bộ test trải qua nhiều vòng chạy-sửa. Ban đầu chỉ 50 pass / 27 skip; sau khi xử lý 2 nhóm
nguyên nhân dưới đây, đạt **77/77 pass**.

### 4.1. Bổ sung dữ liệu seed cho user demo
27 test ban đầu skip vì user demo (`demo@tmdt.local`) thiếu dữ liệu tiền đề. Đã thêm 3 file
seed-dev (Flyway profile `dev`):

| File seed | Nội dung | Mở khóa test |
|-----------|----------|--------------|
| `order-service/.../V103__seed_demo_cart_order_items.sql` | Giỏ hàng có 2 item + order_items cho 2 đơn demo | Module 03-cart, 04-checkout, ORD-04 |
| `product-service/.../V102__seed_demo_reviews.sql` | 2 review (1 của user demo trên SP đã mua) | Module 07-reviews, ADM-14 |
| `user-service/.../V102__seed_demo_addresses.sql` | 2 địa chỉ cố định cho user demo | CHK-02, ADDR-01 |

### 4.2. Sửa lỗi của bộ test (không phải lỗi app)

| Vấn đề | Cách sửa |
|--------|----------|
| `page.goto/reload/waitForURL` chờ event `load` không kết thúc (ảnh nền) | Fixture `utils/fixtures.ts` ghi đè mặc định `domcontentloaded` |
| `locator.isVisible({timeout})` **không chờ** — trả false ngay khi element chưa attached | Thay bằng `locator.waitFor({state})` ở helper + nhiều test |
| `gotoFirstProduct` vào nhầm SP test rác (do test admin tạo, thiếu nút add-cart) | Helper ưu tiên SP seed thật; thêm `gotoProductBySlug` + `ensureCartHasItem` duyệt nhiều SP |
| ReviewSection là **tab "Đánh giá (N)"** — phải click tab trước | Thêm helper `openReviewTab` |
| Selector CSS module hashed (`.totalPrice`, `.qtyValue`) không khớp | Đổi sang selector ngữ nghĩa (text "Tổng cộng", quan hệ DOM với nút +/−) |
| `getByLabel(/Địa chỉ/)` khớp nhầm badge "Địa chỉ mặc định" | Dùng label chính xác `"Địa chỉ (số nhà, đường)"` |
| Nút confirm trong modal vs nút trên card cùng tên | Lọc theo phạm vi `[role="dialog"]` + loại trừ "Hủy"/"✕" |
| Regex thông báo lỗi không khớp text thật | Mở rộng regex ("không chính xác", "đã được đăng ký") |

---

## 5. Vấn đề MÔI TRƯỜNG đã phát hiện & xử lý

### 🐛 order-service crash do Flyway migration validation

- **Triệu chứng:** trang đơn hàng/coupon hiện "Không tải được dữ liệu"; `order-service` `Exited (1)`.
- **Nguyên nhân:** `Detected resolved migration not applied to database: 4, 5` — volume DB cũ
  không tương thích image mới có thêm migration.
- **Khắc phục:** `docker compose down -v` (reset volume) rồi `up` lại → migration apply sạch.
- **Khuyến nghị:** khi rebuild service có migration mới, luôn reset DB volume hoặc dùng
  `flyway.outOfOrder=true`. Nên ghi vào tài liệu vận hành dev.

---

## 6. Lưu ý vận hành bộ test

- **Node.js 18+ bắt buộc** (Playwright yêu cầu). Dự án có Node 24 qua nvm — nếu terminal
  mặc định Node 16, prepend đường dẫn Node 24 vào PATH trước khi chạy.
- **Seed-dev** chỉ nạp khi service chạy profile `dev` (docker-compose đã set sẵn).
- Một số test **tự dọn dữ liệu** sau khi tạo (ADDR-02 xóa địa chỉ vừa tạo, REV-04 tạo lại
  review sau khi xóa) để chạy lại nhiều lần không tích lũy rác.
- Test chatbot mock `POST /api/chat/stream` (Anthropic Claude API) — không tốn phí, deterministic.

## 7. Cách chạy lại

```bash
# 1. Khởi động full stack — reset volume nếu rebuild service có migration mới
docker compose down -v && docker compose up -d --build

# 2. Chạy toàn bộ E2E (đảm bảo Node 18+)
cd sources/frontend
npx playwright test                     # headless (~2.7 phút)
npx playwright test --headed            # hiển thị trình duyệt
npx playwright test e2e/03-cart.spec.ts # chạy 1 module
npx playwright show-report              # báo cáo HTML chi tiết
```
