# Hướng dẫn cài đặt & chạy dự án trên thiết bị khác

Tài liệu này hướng dẫn **tất tần tật** để clone và chạy dự án TMĐT (E-commerce
microservices) trên một máy mới — không cần đọc tài liệu nào khác.

---

## 1. Yêu cầu phần mềm (cài trước)

| Phần mềm | Phiên bản | Dùng để | Kiểm tra |
|----------|-----------|---------|----------|
| **Docker Desktop** | mới nhất | Chạy toàn bộ stack (16 container) | `docker --version` |
| **Git** | bất kỳ | Clone source | `git --version` |
| **Node.js** | **>= 18** (khuyến nghị 20/24) | Chạy bộ test E2E Playwright | `node -v` |

> Backend Spring Boot (Java 17) và frontend Next.js **đều build bên trong Docker**
> — KHÔNG cần cài JDK, Maven hay npm trên máy. Chỉ cần Node nếu muốn chạy E2E test.

---

## 2. Lấy source code

```bash
git clone https://github.com/dminhduc250136/ptit_htpt.git
cd ptit_htpt
git checkout develop          # nhánh tích hợp mới nhất
```

---

## 3. Cấu hình biến môi trường (.env)

Dự án đọc 2 biến môi trường từ file `.env` ở thư mục gốc. File này **không có
sẵn trong git** (đã `.gitignore` vì chứa secret) — phải tự tạo từ mẫu.

```bash
# Windows PowerShell
Copy-Item .env.example .env

# macOS / Linux
cp .env.example .env
```

Mở `.env` và điền giá trị thật:

```dotenv
# Bắt buộc nếu muốn dùng chatbot AI (Phase 22). Lấy key tại https://console.anthropic.com
# Nếu chưa có, để nguyên placeholder — các tính năng khác vẫn chạy, chỉ chatbot lỗi.
ANTHROPIC_API_KEY=sk-ant-replace-me

# Khóa ký JWT. Phải >= 32 ký tự (thuật toán HS256).
# Dùng chung cho api-gateway + user-service — KHÔNG đổi lệch nhau.
# Giá trị dev mặc định dùng được ngay; production phải đổi sang chuỗi ngẫu nhiên bí mật.
JWT_SECRET=dev-jwt-secret-key-minimum-32-characters-for-hs256-ok
```

> Nếu không tạo `.env`, Docker vẫn chạy: `JWT_SECRET` rơi về giá trị dev mặc định,
> còn `ANTHROPIC_API_KEY` rỗng (chatbot sẽ lỗi, phần còn lại bình thường).

---

## 4. Chạy toàn bộ hệ thống

```bash
docker compose up -d --build
```

Lệnh này build và khởi động **16 container**:

- **7 PostgreSQL** — mỗi service 1 database riêng (Phase 24 — Database Per Service):
  `postgres-user`, `postgres-product`, `postgres-order`, `postgres-inventory`,
  `postgres-payment`, `postgres-notification`, `postgres-chat`
- **1 RabbitMQ** — message broker (Phase 23)
- **6 backend** Spring Boot — user, product, order, payment, inventory, notification
- **1 api-gateway** + **1 frontend** Next.js

Lần đầu mất ~5–10 phút (build image). Các lần sau nhanh hơn nhờ cache.

### Kiểm tra đã lên đủ chưa

```bash
docker compose ps
```

Chờ tới khi tất cả ở trạng thái `Up` (các postgres + rabbitmq có thêm `healthy`).
Backend Spring Boot cần ~30–40s để khởi động sau khi container `Up`.

---

## 5. Cổng truy cập (sau khi chạy)

| Dịch vụ | URL | Ghi chú |
|---------|-----|---------|
| **Frontend (web)** | http://localhost:3000 | Giao diện chính |
| **API Gateway** | http://localhost:8080 | Cổng DUY NHẤT của backend (Phase 25) |
| **RabbitMQ Management UI** | http://localhost:15672 | Đăng nhập `guest` / `guest` |

> 6 backend service **không** expose port ra host (Phase 25 — bảo mật). Chỉ truy
> cập được qua API Gateway cổng 8080. RabbitMQ AMQP nội bộ ở cổng 5672.

### Tài khoản đăng nhập sẵn (seed dev)

| Vai trò | Email | Mật khẩu |
|---------|-------|----------|
| Admin | `admin@tmdt.local` | `admin123` |
| User | `demo@tmdt.local` | `admin123` |

---

## 6. Database

- Mỗi service có 1 database Postgres riêng, **không expose port ra host**.
- Schema + dữ liệu seed được tạo **tự động** khi service khởi động (Flyway migration)
  — không cần import SQL thủ công.
- Truy vấn DB trong lúc dev (ví dụ xem bảng của product-service):

  ```bash
  docker exec -it tmdt-use-gsd-postgres-product-1 psql -U product_svc -d product_svc
  ```

  Mẫu tên: container `tmdt-use-gsd-postgres-<svc>-1`, user/db đều là `<svc>_svc`
  (vd `order` → `postgres-order` / `order_svc`).

---

## 7. Lệnh thường dùng

```bash
docker compose ps                       # xem trạng thái container
docker compose logs -f <service>        # xem log 1 service (vd: order-service)
docker compose logs -f                  # xem log tất cả
docker compose down                     # dừng (giữ dữ liệu DB)
docker compose down -v                  # dừng + XÓA toàn bộ dữ liệu DB
docker compose up -d --build <service>  # rebuild lại 1 service
```

---

## 8. Chạy test E2E (tùy chọn)

Cần Node >= 18 và stack đang chạy đầy đủ.

```bash
cd sources/frontend
npm install
npx playwright install chromium     # lần đầu — tải trình duyệt test
npx playwright test                 # chạy toàn bộ
npx playwright test 11-message-queue # chạy 1 nhóm test
```

---

## 9. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|-------------|--------------------------|
| Service backend `Exited (1)`, log báo `Found more than one migration` hoặc `checksum mismatch` | Volume DB cũ không khớp migration mới. Chạy `docker compose down -v` rồi `up -d --build` lại (xóa sạch DB, tạo lại từ đầu). |
| Service `Exited (1)`, log `missing table` / `Schema-validation` | Migration chưa chạy hết — thường do volume cũ. Xử lý như trên. |
| Cổng 3000 / 8080 / 5672 / 15672 báo bận | Có ứng dụng khác đang chiếm cổng. Tắt ứng dụng đó, hoặc đổi mapping cổng trong `docker-compose.yml`. |
| Chatbot AI lỗi | `ANTHROPIC_API_KEY` trong `.env` chưa đúng / chưa điền. |
| E2E test fail `Node.js 18 or higher` | Node đang < 18. Cài/đổi sang Node >= 18 (`nvm use 20`). |
| Nâng cấp từ bản cũ (trước Phase 24) báo lỗi DB | Bản cũ dùng 1 postgres shared. Bắt buộc `docker compose down -v` để bỏ volume cũ `tmdt-pgdata` trước khi `up`. |

---

## 10. Tóm tắt nhanh — copy & chạy

```bash
git clone https://github.com/dminhduc250136/ptit_htpt.git
cd ptit_htpt
git checkout develop
cp .env.example .env          # (Windows: Copy-Item .env.example .env)
docker compose up -d --build
# chờ ~5-10 phút lần đầu, rồi mở http://localhost:3000
```
