# Phase 24: Database Per Service (tách CSDL hạ tầng) - Context

**Gathered:** 2026-05-20
**Status:** Ready for planning
**Depends on:** Khuyến nghị làm SAU Phase 23 (để Phase 23 đã commit xong các thay đổi docker-compose RabbitMQ + processed_events table); không cứng.

<domain>
## Phase Boundary

Phase này tách hạ tầng CSDL từ mô hình hiện tại **"một postgres container — nhiều schema"** sang **"mỗi service một postgres container riêng + credential riêng"**. Củng cố yêu cầu 3.4 (database-per-service) của đề chủ đề 4 và mục 4 (tính chịu lỗi độc lập): khi 1 DB chết, các service khác vẫn chạy được phần chức năng không phụ thuộc.

**Trong scope:**
- Tách 5 postgres container: `postgres-user`, `postgres-product`, `postgres-order`, `postgres-inventory`, `postgres-payment`. Mỗi container có DB + user + password riêng.
- **Không tách** postgres cho notification-service vì hiện tại notification-service không dùng Flyway/JPA persistent (Phase 23 sẽ thêm `dispatch_log` — phase này phải tính đến và tách luôn nếu Phase 23 đã merge; xem D-02).
- Mỗi service `application.yml` trỏ DB host riêng (`postgres-user`, `postgres-product`,...) với credential riêng.
- Mỗi service Flyway chạy trên DB của chính mình — bỏ `default_schema` (vì DB riêng → schema mặc định `public` đủ).
- Seed migrations V100/V101 (categories + products) chuyển hoàn toàn về `postgres-product`.
- Seed migration V102 (`inventory_items`) — nếu Phase 23 đã tạo — chuyển về `postgres-inventory`. Vì DB tách rồi, không thể JOIN cross-DB → V102 sẽ là dữ liệu **độc lập** (hardcode hoặc duplicate từ V101) chứ không phải `INSERT ... SELECT FROM product_svc.products`.
- E2E demo failure isolation: dừng `postgres-order` container → `/api/users/me`, `/api/products/*` vẫn hoạt động; chỉ `/api/orders/*` lỗi 503/500 (có error contract chuẩn).
- Smoke integration test bằng Testcontainers (cho riêng order-svc) tiếp tục pass — vì mỗi service đã isolated, Testcontainers chỉ start postgres của service đó.

**Ngoài scope (defer):**
- Service mesh / Kubernetes — vẫn dùng docker-compose plain.
- DB replication, HA, backup automation — chỉ demo isolation, không demo HA.
- Migration data từ shared DB sang per-service DB cho môi trường có dữ liệu live — phase này coi như fresh start (xóa volume cũ).
- Saga / outbox pattern xử lý cross-DB transactional consistency — vẫn dùng pattern Phase 23 (publish sau commit + idempotent consumer).
- Tách DB cho `chat_sessions`/`chat_messages` (hiện FE Next.js dùng connection trực tiếp qua `pg` — Phase 22) — phase này KHÔNG động đến. Xem D-09.

</domain>

<decisions>
## Implementation Decisions

### Topology

- **D-01:** **5 postgres container** riêng biệt cho 5 service backend Java: user, product, order, inventory, payment. Mỗi container image `postgres:16-alpine`. Đặt tên service trong compose: `postgres-user`, `postgres-product`, `postgres-order`, `postgres-inventory`, `postgres-payment`.
- **D-02:** **notification-service**: nếu thời điểm thực thi Phase 24 đã merge Phase 23 (notification-svc có Flyway + `dispatch_log` + `processed_events`), thì thêm container thứ 6 `postgres-notification`. Nếu Phase 23 CHƯA merge (làm Phase 24 trước), bỏ qua — sẽ bổ sung tại Phase 23 hoặc phase clean-up sau. **Plan-phase phải check git state để quyết định.**
- **D-03:** **Mỗi container 1 DB, 1 user**: ví dụ `postgres-user` → `POSTGRES_DB=user_svc`, `POSTGRES_USER=user_svc`, `POSTGRES_PASSWORD=user_svc_pw`. Tương tự cho product/order/inventory/payment. Mật khẩu hardcode trong compose cho dev (KHÔNG production-grade — đề chủ đề là demo).
- **D-04:** **Không expose host port** cho các postgres container (bỏ `ports: 5432:5432` cũ). Chỉ accessible trong docker network. Lý do: giảm rủi ro local conflict + buộc dev dùng `docker exec` để truy vấn. (Trade-off: dev tool như DBeaver phải kết nối qua exec/forward. Chấp nhận được — đây là demo.)
- **D-05:** **Volume riêng** per container: `pgdata-user`, `pgdata-product`, `pgdata-order`, `pgdata-inventory`, `pgdata-payment` (và `pgdata-notification` nếu D-02 áp dụng). Xóa volume cũ `tmdt-pgdata`.

### Schema & Migrations

- **D-06:** **Bỏ `default_schema: <svc>_svc`** trong tất cả `application.yml`. Mỗi service connect vào DB riêng, schema mặc định `public`. Lý do: tách DB là biên cô lập đủ mạnh, không cần thêm 1 lớp schema; đơn giản hóa Flyway + JPA config. Phải verify mọi truy vấn JPA + native SQL không hardcode schema prefix (`SELECT FROM order_svc.orders` → đổi thành `SELECT FROM orders`).
- **D-07:** **Grep audit bắt buộc trong plan**: `grep -rn "<svc>_svc\." sources/backend/` cho mỗi service — tìm mọi reference schema-prefix cũ và refactor. Đặc biệt chú ý:
  - Phase 23 (nếu đã merge) các bảng `inventory_svc.processed_events`, `notification_svc.dispatch_log`.
  - Native SQL trong `*Repository.java` (`@Query(value=..., nativeQuery=true)`).
  - Flyway script `V*.sql`.
- **D-08:** **Seed migrations layout sau khi tách**:
  - product-svc: `V1..V7` (cũ) + `V100__seed_categories.sql` + `V101__seed_catalog_realistic.sql` ở `db/seed-dev/` — đã đúng vị trí.
  - inventory-svc: `V1..V?` (cũ) + (nếu Phase 23 đã tạo) `V102__seed_initial_inventory.sql` — sửa lại không JOIN cross-DB nữa, hardcode hoặc generate từ script độc lập (chấp nhận duplicate dữ liệu để giữ tính độc lập DB).
  - Các service khác: chỉ giữ migration core (schema), không seed.
- **D-09:** **Chat tables (Phase 22)**: `chat_sessions`/`chat_messages` được Next.js frontend tạo qua `services/chat-schema-init.ts` và kết nối trực tiếp `pg` đến postgres container chính (host = `postgres` cũ). **Quyết định:** thêm container thứ 7 `postgres-chat` HOẶC để chat dùng chung với `postgres-product` (đơn giản, 1 service backend duy nhất là FE Next.js dùng → không vi phạm DB-per-service nguyên tắc). **Chọn:** tạo `postgres-chat` riêng để giữ kỷ luật "tách hoàn toàn". FE env `DB_HOST=postgres-chat`, `DB_NAME=chat_svc`. Plan-phase phải refactor `services/chat-schema-init.ts` để tương thích.

### Application Config

- **D-10:** **Mỗi service `application.yml`**: env vars riêng theo service.
  - user-svc: `DB_HOST: postgres-user`, `DB_NAME: user_svc`, `DB_USER: user_svc`, `DB_PASSWORD: user_svc_pw`.
  - product-svc: `DB_HOST: postgres-product`, `DB_NAME: product_svc`, ...
  - tương tự cho 3 service còn lại.
- **D-11:** **`depends_on`**: mỗi backend service chỉ `depends_on` postgres của riêng nó (không phải tất cả). Đây là điểm chứng minh isolation: nếu `postgres-order` chết, compose vẫn start được user-service và product-service.
- **D-12:** **Healthcheck per postgres container**: copy nguyên pattern `pg_isready -U <user> -d <db>` từ compose hiện tại — chỉ đổi user/db cho phù hợp.

### Failure Isolation Demo

- **D-13:** **Demo script**: thêm `docs/db-isolation-demo.md` chỉ rõ cách trình bày tính chịu lỗi:
  1. `docker compose up -d` — verify tất cả service healthy.
  2. `docker compose stop postgres-order` — verify `curl localhost:8080/api/products` vẫn 200, `curl localhost:8080/api/users/me` vẫn 200 (với JWT), `curl POST /api/orders` trả 500/503.
  3. `docker compose start postgres-order` — verify order-svc tự reconnect (HikariCP retry) và `/api/orders` hoạt động lại trong vòng <60s.
- **D-14:** **Error contract khi DB down**: order-svc trả `503 Service Unavailable` với `ApiErrorResponse(code="DATABASE_UNAVAILABLE", message="Order database is temporarily unavailable")`. Cách triển khai: thêm `@ControllerAdvice` bắt `CannotCreateTransactionException` + `JDBCConnectionException` → 503. Chấp nhận: nếu lúc startup DB chưa lên, Spring Boot có thể fail-fast — config `spring.datasource.hikari.initialization-fail-timeout=-1` để cho phép start với DB chưa sẵn sàng (lazy connection).

### Testing

- **D-15:** **Integration tests**: mỗi service `*IntegrationTest` dùng Testcontainers postgres riêng (pattern đã có ở Phase 23 cho order-svc). Phase 24 chỉ verify không break — không thêm test mới về isolation (test isolation chạy thủ công qua D-13 script).
- **D-16:** **E2E Playwright**: smoke spec mới `db-isolation.spec.ts` — KHÔNG bắt buộc (do trừ liệu UI thay đổi). Defer sang phase verify hoặc bỏ qua nếu thời gian không đủ. Phase 24 ưu tiên backend hardening trước.

### Documentation

- **D-17:** **Update `docs/architecture.md`** (nếu có) — diagram cập nhật từ "1 postgres / multi-schema" → "N postgres / per-service". Nếu chưa có file đó thì tạo mới trong Phase 24 plan cuối.
- **D-18:** **Update `README.md`** section "Local Dev" — thêm cảnh báo dữ liệu cũ trong `tmdt-pgdata` sẽ bị bỏ qua sau Phase 24, hướng dẫn `docker compose down -v` để clean slate.

### Scope Boundaries (deferred ideas)

- **D-19:** **DB user least-privilege** (read-only user cho query analytics, write user cho service) — defer; phase này dùng 1 user per DB cho cả read/write.
- **D-20:** **Secrets management** (Vault, Docker secrets) — defer; password hardcode trong compose cho demo.
- **D-21:** **Connection pool tuning** per service — giữ HikariCP default (10 connections). Defer.

</decisions>

<reusable_assets>
## Reusable Assets

- **docker-compose.yml**: pattern healthcheck postgres đã có sẵn (line 11-15) — copy 5 lần.
- **application.yml per service**: cấu trúc env-driven `${DB_HOST}/${DB_NAME}/${DB_USER}/${DB_PASSWORD}` đã có — chỉ đổi default values, không refactor pattern.
- **Flyway config**: mỗi service đã có `db/migration` + `db/seed-dev` riêng — chỉ cần đổi DB target, không cần restructure folder.
- **Testcontainers integration test pattern**: order-svc Phase 23 đã có (`@Testcontainers @Container PostgreSQLContainer`) — copy cho các service khác nếu cần.

</reusable_assets>

<open_questions>
## Open Questions (for research phase)

- **Q-01:** Spring Boot 3.x + Hikari `initialization-fail-timeout=-1` có thực sự cho phép start container khi DB chưa healthy? Hoặc cần `spring.sql.init.continue-on-error`? → researcher xác minh + chốt config.
- **Q-02:** Khi `postgres-order` chết giữa runtime, Hikari retry sau bao lâu? Có cần custom `connection-test-query`? → researcher kiểm tra default behavior + đề xuất tuning nhẹ.
- **Q-03:** V102 seed inventory — nếu Phase 23 đã merge và đang JOIN cross-schema `product_svc.products`, refactor sang dữ liệu hardcoded có làm tăng kích thước file đáng kể không (100 product × 1 dòng)? → researcher confirm OK hoặc đề xuất script generator.
- **Q-04:** `services/chat-schema-init.ts` ở FE — connection pool có shared singleton hay tạo mới mỗi request? Khi đổi `DB_HOST` sang `postgres-chat`, có cần restart strategy? → researcher đọc code.

</open_questions>
