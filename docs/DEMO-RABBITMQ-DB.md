# Chứng minh & Demo: RabbitMQ + Database Per Service

> Tài liệu chuẩn bị cho buổi bảo vệ. Giải thích **tại sao** hệ thống dùng
> RabbitMQ và **tại sao** tách database, kèm **kịch bản demo trực tiếp** và
> **câu hỏi — trả lời mẫu** khi giảng viên chất vấn.
>
> Kiến trúc tổng thể: xem [KIEN-TRUC-HE-THONG.md](./KIEN-TRUC-HE-THONG.md).

---

# PHẦN A — MESSAGE QUEUE (RabbitMQ)

## A1. Nói ngắn gọn: RabbitMQ làm gì trong hệ thống?

> "Khi khách đặt hàng, `order-service` không gọi trực tiếp `inventory-service`
> và `notification-service`. Thay vào đó nó **phát một sự kiện `OrderPlaced`**
> lên RabbitMQ rồi trả kết quả ngay cho khách. Hai service kia **tự lắng nghe**
> sự kiện đó và xử lý độc lập: inventory trừ kho, notification ghi nhật ký
> thông báo. Đây là giao tiếp **bất đồng bộ** — tách rời các service."

## A2. Tại sao dùng Message Queue thay vì gọi REST trực tiếp?

| Vấn đề khi gọi REST trực tiếp | Cách Message Queue giải quyết |
|-------------------------------|-------------------------------|
| order-service phải chờ inventory + notification trả lời → khách chờ lâu | order-service phát sự kiện xong trả về ngay; trừ kho/thông báo chạy nền |
| Nếu notification-service chết → đặt hàng **thất bại** theo | notification chết → message **nằm chờ trong queue**, service sống lại xử lý tiếp; đặt hàng vẫn thành công |
| order-service phải biết địa chỉ + API của mọi service nhận | order-service chỉ cần biết RabbitMQ; thêm consumer mới không phải sửa order-service |
| Khó mở rộng (thêm 1 hành động sau đặt hàng = sửa code order) | Thêm consumer mới chỉ cần "nghe" cùng sự kiện |

**Một câu chốt:** *Message Queue giúp các service **tách rời (decoupling)**,
**chịu lỗi tốt hơn (resilience)**, và **phản hồi nhanh (async)**.*

## A3. Cơ chế tin cậy — xử lý khi message lỗi

- **Publisher Confirms:** order-service chỉ coi là phát thành công khi RabbitMQ
  xác nhận đã nhận message.
- **Manual ACK:** consumer chỉ báo "đã xử lý xong" (ACK) sau khi business logic
  thành công. Nếu service chết giữa chừng → message chưa ACK → RabbitMQ giao lại.
- **Retry 3 lần:** consumer xử lý lỗi (vd: DB tạm trục trặc) → tự thử lại 3 lần,
  giãn cách tăng dần (1s → 2s → 4s).
- **Dead Letter Queue (`order-events.dlq`):** sau 3 lần retry vẫn lỗi → message
  được chuyển vào DLQ để không chặn hàng đợi; admin xem lại sau.
- **Idempotency (chống xử lý trùng):** mỗi consumer có bảng `processed_events`
  lưu `event_id` đã xử lý. Nếu cùng một message bị giao lại lần 2 → consumer
  thấy `event_id` đã có → bỏ qua, không trừ kho 2 lần.

## A4. Kịch bản DEMO RabbitMQ trước hội đồng

### Chuẩn bị

```bash
docker compose up -d --build
# chờ ~60-90s cho các service khởi động
docker compose ps        # tất cả Up, postgres + rabbitmq là "healthy"
```

### Demo 1 — Luồng đặt hàng chạy qua queue (happy path)

**Bước 1.** Mở RabbitMQ Management UI: `http://localhost:15672` (guest/guest).
Vào tab **Exchanges** → chỉ ra `order.events`; tab **Queues** → chỉ ra
`inventory.order-events`, `notification.order-events`, `order-events.dlq`.

> *Nói:* "Đây là topology message queue — 1 exchange, 2 queue cho 2 consumer,
> 1 dead letter queue cho message lỗi."

**Bước 2.** Mở web `http://localhost:3000`, đăng nhập, **đặt một đơn hàng**.

**Bước 3.** Quay lại Management UI, tab **Queues**:
- Cột *Messages* của 2 queue tăng lên rồi **về 0** trong vài giây — chứng tỏ
  message đã được publish và 2 consumer đã ACK xong.
- Biểu đồ *Message rates* có nhịp publish/deliver.

**Bước 4.** Kiểm tra side-effect trong database:

```bash
# Kho đã bị trừ + có bản ghi ledger
docker compose exec postgres-inventory psql -U inventory_svc -d inventory_svc \
  -c "SELECT * FROM stock_ledger ORDER BY created_at DESC LIMIT 3;"

# Có bản ghi thông báo
docker compose exec postgres-notification psql -U notification_svc -d notification_svc \
  -c "SELECT event_id, subject, status FROM dispatch_log ORDER BY sent_at DESC LIMIT 3;"
```

> *Nói:* "order-service chỉ phát sự kiện. Việc trừ kho và ghi thông báo do 2
> service khác tự xử lý qua queue — đó là bất đồng bộ."

### Demo 2 — Truy vết xuyên service bằng traceId

```bash
docker compose logs order-service inventory-service notification-service | grep "MQ-"
```

Chỉ ra cùng một `traceId` xuất hiện trong log `[MQ-PUB]` (order-service) và
`[MQ-CONSUME]` (inventory + notification) → chứng minh một sự kiện được truy vết
xuyên suốt 3 service.

### Demo 3 — Chịu lỗi: consumer chết, message không mất

**Bước 1.** Tắt notification-service:

```bash
docker compose stop notification-service
```

**Bước 2.** Đặt một đơn hàng mới qua web → **vẫn thành công** (order-service
không phụ thuộc notification).

**Bước 3.** Mở Management UI → queue `notification.order-events` có message
**đang chờ** (Messages > 0) — message không mất.

**Bước 4.** Bật lại notification-service:

```bash
docker compose start notification-service
```

→ Sau vài giây, queue `notification.order-events` về 0, `dispatch_log` có thêm
bản ghi. **Message được xử lý bù** khi service sống lại.

> *Nói:* "Đây là điểm mạnh của message queue — service tiêu thụ chết tạm thời
> không làm mất dữ liệu, cũng không làm hỏng luồng đặt hàng."

### Demo 4 (tuỳ chọn) — script smoke tự động

```bash
bash scripts/verify-mq.sh
```

Script kiểm tra Management UI sống + topology đã khai báo đủ → in `OK`.

## A5. Hỏi — Đáp mẫu (RabbitMQ)

**Q: Tại sao chọn RabbitMQ mà không phải Kafka?**
> Đề chủ đề 4 yêu cầu message queue cho giao tiếp microservice. Với quy mô đồ
> án, RabbitMQ phù hợp hơn: có sẵn cơ chế **Dead Letter Queue** và **retry** ở
> mức broker, có **Management UI** trực quan để demo, cài đặt nhẹ. Kafka mạnh ở
> luồng dữ liệu lớn / stream nhưng nặng và phức tạp hơn mức cần thiết.

**Q: Nếu message bị xử lý 2 lần thì sao?**
> Mỗi consumer có bảng `processed_events` lưu `event_id`. Trước khi xử lý,
> consumer kiểm tra `event_id` đã tồn tại chưa — nếu rồi thì bỏ qua. Nhờ đó dù
> RabbitMQ giao lại message (at-least-once delivery), kho cũng chỉ bị trừ 1 lần.
> Tính chất này gọi là **idempotency**.

**Q: Message lỗi mãi thì sao, có chặn hàng đợi không?**
> Không. Consumer retry 3 lần; vẫn lỗi thì message bị đẩy sang **Dead Letter
> Queue** `order-events.dlq`. Hàng đợi chính tiếp tục xử lý message khác, còn
> message lỗi nằm ở DLQ chờ admin xử lý — quan sát được trong Management UI.

**Q: Lỡ order-service publish xong nhưng RabbitMQ chưa nhận thì sao?**
> Bật **Publisher Confirms** — order-service chỉ coi là phát thành công khi
> RabbitMQ xác nhận. Ngoài ra sự kiện chỉ được phát **sau khi** đơn hàng đã
> commit vào database, nên không có chuyện "phát sự kiện nhưng đơn không tồn tại".

---

# PHẦN B — DATABASE PER SERVICE

## B1. Nói ngắn gọn: tách database nghĩa là gì?

> "Mỗi microservice có **một database PostgreSQL riêng**, chạy trong container
> riêng, credential riêng. order-service chỉ truy cập `postgres-order`,
> inventory-service chỉ truy cập `postgres-inventory`... Không service nào đụng
> được vào database của service khác."

## B2. Tại sao tách database?

| Nếu dùng 1 database chung | Khi tách database riêng |
|---------------------------|--------------------------|
| 1 database chết → **toàn bộ** hệ thống tê liệt | 1 database chết → chỉ service đó ảnh hưởng, các service khác vẫn chạy |
| Các service ràng buộc nhau qua bảng dùng chung → khó sửa độc lập | Mỗi service tự do thay đổi schema của mình |
| Một service truy vấn nặng → cả hệ thống chậm theo | Tải database tách biệt, không ảnh hưởng chéo |
| Ranh giới dữ liệu mờ → dễ vi phạm "ai sở hữu dữ liệu gì" | Ranh giới rõ ràng: mỗi service là chủ sở hữu duy nhất dữ liệu của nó |

**Một câu chốt:** *Database-per-service đảm bảo **cô lập lỗi (failure
isolation)** và **tính tự chủ (autonomy)** của từng service — đúng tinh thần
microservice.*

## B3. Hệ quả & cách xử lý

- **Không còn JOIN chéo service.** Trước đây có thể `JOIN` bảng của 2 service;
  giờ không. Muốn dữ liệu của service khác → gọi REST, hoặc nhận qua sự kiện
  (ví dụ inventory nhận `OrderPlaced` để biết đơn hàng, không truy vấn order DB).
- **Mỗi service tự quản schema** bằng Flyway migration riêng.
- **Service vẫn khởi động được khi DB của nó chưa sẵn sàng** (cấu hình
  `hikari.initialization-fail-timeout=-1`) — kết nối DB là *lazy*, chỉ tạo khi
  có request thật.
- Khi DB của một service chết, service đó trả lỗi **`503 DATABASE_UNAVAILABLE`**
  rõ ràng thay vì treo.

## B4. Kịch bản DEMO Database Per Service

### Demo 1 — Chỉ ra 7 database tách biệt

```bash
docker compose ps | grep postgres
```

→ Hiện 7 container: `postgres-user`, `postgres-product`, `postgres-order`,
`postgres-payment`, `postgres-inventory`, `postgres-notification`,
`postgres-chat` — mỗi cái độc lập.

> *Nói:* "Mỗi service một database riêng. Đây không phải 1 database nhiều
> schema, mà là 7 tiến trình PostgreSQL riêng biệt, credential riêng."

### Demo 2 — Chịu lỗi độc lập (quan trọng nhất)

**Bước 1.** Tắt database của order-service:

```bash
docker compose stop postgres-order
```

**Bước 2.** Thử dùng tính năng **không liên quan order** — ví dụ xem danh sách
sản phẩm trên web, hoặc:

```bash
curl -s http://localhost:8080/api/products?size=3
```

→ **Vẫn trả về bình thường.** product-service không phụ thuộc `postgres-order`.

**Bước 3.** Thử dùng tính năng **của order-service** — xem đơn hàng:

```bash
curl -s http://localhost:8080/api/orders -H "Authorization: Bearer <token>"
```

→ Trả lỗi rõ ràng **`503 DATABASE_UNAVAILABLE`** — chỉ riêng order-service ảnh
hưởng, không làm sập cả hệ thống.

**Bước 4.** Bật lại:

```bash
docker compose start postgres-order
```

→ Sau khi `postgres-order` healthy, order-service hoạt động lại bình thường.

> *Nói:* "Đây là **failure isolation** — một database chết chỉ ảnh hưởng đúng
> service của nó. Nếu dùng 1 database chung, bước 2 đã sập luôn rồi."

### Demo 3 — Chứng minh dữ liệu thật sự tách

```bash
# Bảng đơn hàng nằm trong postgres-order
docker compose exec postgres-order psql -U order_svc -d order_svc -c "\dt"

# postgres-inventory KHÔNG có bảng orders — database tách biệt
docker compose exec postgres-inventory psql -U inventory_svc -d inventory_svc -c "\dt"
```

→ Mỗi database chỉ chứa bảng của service sở hữu nó.

> Chi tiết các bước demo isolation: xem thêm
> [db-isolation-demo.md](./db-isolation-demo.md).

## B5. Hỏi — Đáp mẫu (Database)

**Q: Tách database thì làm sao JOIN dữ liệu giữa các service?**
> Không JOIN qua database nữa. Có 2 cách: (1) gọi REST đồng bộ khi cần dữ liệu
> tức thời — ví dụ order-service gọi product-service kiểm tra tồn kho; (2) nhận
> qua sự kiện — inventory-service nhận `OrderPlaced` chứa sẵn thông tin đơn,
> không cần truy vấn order DB. Đây là sự đánh đổi: bớt tiện JOIN nhưng được tính
> độc lập.

**Q: 7 database thì quản lý schema thế nào?**
> Mỗi service có thư mục Flyway migration riêng (`db/migration/V1__*.sql`...).
> Khi service khởi động, Flyway tự chạy migration trên database của nó. Không có
> file SQL dùng chung.

**Q: Nếu database của một service chết, người dùng thấy gì?**
> Service đó trả `503 DATABASE_UNAVAILABLE` — một mã lỗi rõ ràng, không phải
> treo vô hạn. Các tính năng của service khác vẫn dùng bình thường. Demo B4
> chứng minh điều này.

**Q: Đây có thật là database riêng không, hay chỉ là schema riêng trong 1 DB?**
> Database riêng thật sự — `docker compose ps` cho thấy 7 tiến trình PostgreSQL
> độc lập, mỗi cái container riêng, port nội bộ riêng, volume lưu dữ liệu riêng,
> user/password riêng. Tắt một cái không ảnh hưởng cái khác (demo B2).

---

# PHẦN C — CHECKLIST TRƯỚC BUỔI BẢO VỆ

- [ ] `docker compose up -d --build` chạy trước 10–15 phút, `docker compose ps`
      tất cả `Up`/`healthy`.
- [ ] Mở sẵn 3 tab: web `:3000`, RabbitMQ UI `:15672`, terminal.
- [ ] Đăng nhập sẵn 1 tài khoản trên web, giỏ hàng có sẵn vài sản phẩm.
- [ ] Tập trước 4 demo RabbitMQ (A4) + 3 demo Database (B4).
- [ ] Thuộc 3 câu chốt:
  - RabbitMQ → *tách rời, chịu lỗi, phản hồi nhanh*.
  - Database per service → *cô lập lỗi, tự chủ*.
  - Gateway JWT → *xác thực ở biên, chống giả mạo danh tính*.
- [ ] Chuẩn bị trả lời "tại sao RabbitMQ không Kafka" và "tách DB thì JOIN sao".

---

*Tài liệu liên quan: [KIEN-TRUC-HE-THONG.md](./KIEN-TRUC-HE-THONG.md) ·
[db-isolation-demo.md](./db-isolation-demo.md) · [security.md](./security.md)*
