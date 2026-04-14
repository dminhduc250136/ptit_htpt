# THÔNG TIN DỰ ÁN (PROJECT CONTEXT)

> Tài liệu này đóng vai trò làm ngữ cảnh cốt lõi cho dự án. AI Agent cần đọc tài liệu này trước khi thực hiện các yêu cầu thay đổi lớn (như kiến trúc, code màn hình mới) để đảm bảo tính đồng nhất.

## 1. Tổng quan hệ thống
- **Lĩnh vực:** Thương mại điện tử (E-commerce / Cửa hàng mua sắm trực tuyến).
- **Định hướng thiết kế:** Thiết kế cao cấp, chuyên nghiệp ("The Digital Atélier" - Áp dụng Tonal Depth, màu sắc tạo thành các lớp ảo thay vì dùng các đường viền cứng nhắc - dựa theo Design System trên Stitch).
- **Kiến trúc hệ thống:** Kiến trúc vi dịch vụ (Microservices).

## 2. Stack Công Nghệ (Tech Stack)

### Frontend (FE):
- **Phạm vi:** 1 ứng dụng Frontend phân phối tới người dùng.
- **Công nghệ chính:** NextJS (React), TypeScript (khuyến nghị để có strict-typing cho các model).
- **Yêu cầu UI:** Áp dụng Mock Data khi API chưa được team BE hoàn thiện.

### Backend (BE):
- **Công nghệ chính:** Java (Spring Boot - khuyến nghị).
- **Cấu trúc:** Phân chia tối thiểu thành 3 dịch vụ đập lập (Microservices):
  1. **`User Service`:** Quản lý thông tin tài khoản người dùng, xác thực (Authentication/Authorization) và phân quyền admin/khách hàng.
  2. **`Product Service`:** Quản trị danh mục hàng hóa, thông tin chi tiết sản phẩm, mức giá, cập nhật kho (Inventory).
  3. **`Order Service`:** Logic kinh doanh liên quan đến giỏ hàng (Cart), hóa đơn, thanh toán và xử lý đơn.

## 3. Lộ trình triển khai dự án (4 Phases)
*(Áp dụng chiến lược Frontend-first và Mocking Data)*

- **Phase 1: Khởi tạo Nền tảng & Design System**
  - Khởi tạo khung dự án NextJS (FE) và khung 3 dự án Java (BE).
  - Tích hợp tài liệu thiết kế (CSS Tokens, Global Styles, Typography, Colors) vào FE để đảm bảo tính thẩm mỹ đồng bộ ngay từ đầu.

- **Phase 2: Xây dựng Bộ thành phần UI Kit (Core Components)**
  - Đóng gói các thành phần nhỏ bé và độc lập (Dumb components) dựa trên thiết kế: `Button`, `Input`, `ProductCard`, `Alerts`.
  - Nghiêm ngặt áp dụng phong cách thiết kế (ví dụ: Glassmorphism, đổ bóng không gian - Ambient Shadow).

- **Phase 3: Lắp ráp Màn Hình User (User Screens) & API Mocking** ✅
  - Đã hoàn thành tất cả:
    - `[x]` Trang chủ (Home)
    - `[x]` Danh sách sản phẩm
    - `[x]` Chi tiết sản phẩm
    - `[x]` Giỏ hàng (+ trạng thái trống)
    - `[x]` Đăng nhập
    - `[x]` Đăng ký
    - `[x]` Tìm kiếm sản phẩm (`/search`)
    - `[x]` Thanh toán (`/checkout`)
    - `[x]` Thông tin cá nhân (`/profile`)
    - `[x]` Chi tiết đơn hàng (`/profile/orders/[id]`)
  - Popup/Modal User:
    - `[x]` Modal: Đổi mật khẩu (trong Profile)
    - `[x]` Modal: Đặt hàng thành công (trong Checkout)
    - `[x]` Toast: Đã thêm vào giỏ hàng (component)
  - Sử dụng Mock Data làm hợp đồng giao tiếp (Data Contract) với 3 service của BE.

- **Phase 4: Không gian Quản trị (Admin Dashboard)** ✅
  - Đã hoàn thành tất cả:
    - `[x]` Layout Admin (Sidebar + Header riêng)
    - `[x]` Trang chủ Admin — Dashboard (`/admin`)
    - `[x]` Quản lý sản phẩm (`/admin/products`)
    - `[x]` Quản lý đơn hàng (`/admin/orders`)
    - `[x]` Quản lý tài khoản (`/admin/users`)
  - Popup/Modal Admin:
    - `[x]` Modal: Thêm sản phẩm (trong Admin Products)
    - `[x]` Modal: Chi tiết đơn hàng & Cập nhật trạng thái (trong Admin Orders)
    - `[x]` Popup: Xác nhận xóa (trong Admin Products & Users)
  - Duy trì ngôn ngữ thiết kế đồng bộ nhưng tập trung vào thao tác quản trị nhanh.

- **Phase 5: Ghép nối Frontend với Backend Thực tế (Integration)**
  - Thay thế các endpoint API Mocking bằng các endpoint thật trỏ tới Java BE.
  - Xử lý các logic về State phức tạp (ví dụ: giữ phiên đăng nhập bảo mật, đồng bộ giỏ hàng, real-time update cho admin).

## 4. Nguyên tắc chỉ đạo cho AI Agent
- Bất cứ khi code tính năng mới, hãy luôn ưu tiên mô hình **Mocking** (sinh dữ liệu giả tạo form/khung) nếu không chắc BE đã có API tương ứng.
- Đảm bảo viết UI dựa trên các biến môi trường cấu hình sẵn của thẻ màu/chữ từ Phase 1 chứ không fix "cứng" giao diện bằng custom CSS riêng lẻ.
- Tốc độ và sự liền mạch của UX phải được tối ưu lên hàng đầu nhờ sử dụng sức mạnh của NextJS.
