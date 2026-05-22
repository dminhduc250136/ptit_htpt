# Phase 26: Tích Hợp Thanh Toán VNPay Sandbox - Research

**Researched:** 2026-05-22
**Domain:** Cổng thanh toán VNPay sandbox + HMAC SHA512 + event-driven cross-service (Spring Boot microservices)
**Confidence:** HIGH (codebase inspection trực tiếp + VNPay official sandbox docs)

## Summary

Phase 26 tích hợp VNPay sandbox vào luồng checkout. `payment-service` sở hữu toàn bộ logic VNPay (build payment URL, ký/verify `vnp_SecureHash` HMAC SHA512, xử lý return + IPN, idempotency). `order-service` gọi REST đồng bộ sang `payment-service` để tạo session VNPAY và nhận `paymentUrl`, sau đó trì hoãn phát `OrderPlaced` cho tới khi consume event `PaymentSucceeded`. Hạ tầng RabbitMQ Phase 23 (envelope + topology + retry/DLQ + processed_events idempotency) là nền tảng tái dụng trực tiếp — `payment-service` hiện CHƯA có module messaging nên phải thêm `spring-boot-starter-amqp` + `RabbitMQConfig` + publisher; `order-service` đã có sẵn publisher/config nhưng cần thêm consumer cho payment events.

VNPay sandbox dùng spec v2.1.0: payment URL endpoint `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`, ký HMAC SHA512 trên chuỗi `key=urlEncode(value)&...` đã sort tăng dần theo tên khóa. Điểm chí mạng dễ sai chữ ký: (1) `vnp_Amount` phải nhân 100; (2) URL-encoding khi build hash phải IDENTICAL với khi verify (dùng cùng `URLEncoder.encode(v, StandardCharsets.US_ASCII)` cho cả hai chiều); (3) khi verify IPN/return phải loại `vnp_SecureHash` + `vnp_SecureHashType` khỏi tập tham số trước khi tính lại. IPN là nguồn sự thật duy nhất — return URL chỉ hiển thị; IPN phải trả JSON `{"RspCode","Message"}` đúng format VNPay (00=confirm, 02=đã xử lý → cả hai dừng retry).

Gateway Phase 25 verify JWT trên mọi route không-public. VNPay là bên ngoài không gửi được JWT → endpoint `/payments/vnpay/ipn` và `/payments/vnpay/return` PHẢI được whitelist vào `app.auth.public-endpoints` của api-gateway (GET method). Bảo mật dựa hoàn toàn vào verify `vnp_SecureHash` + so khớp số tiền/mã đơn.

**Primary recommendation:** Tự implement VNPay logic trong `payment-service` (KHÔNG dùng thư viện bên thứ ba — spec đơn giản, official sample code công khai, dependency thêm rủi ro hơn lợi ích cho dự án demo). Bám sát pattern hạ tầng Phase 23 (envelope/exchange/processed_events) và Phase 19 (`ProductBatchClient` REST cross-service). Whitelist 2 endpoint VNPay qua `public-endpoints` của gateway.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Chọn phương thức VNPay + redirect | Frontend (browser) | — | UI selector + `window.location` sang `paymentUrl` |
| Tạo order `payment_status=PENDING` | order-service | — | order-service sở hữu order state + cột `payment_status` (D-02) |
| Build VNPay payment URL + ký HMAC | payment-service | — | D-01: payment-service sở hữu toàn bộ logic VNPay |
| Verify `vnp_SecureHash` IPN/return | payment-service | — | D-01: chữ ký là trách nhiệm payment-service |
| Idempotency callback IPN | payment-service | — | D-07: check transaction status + processed-events |
| Cập nhật `payment_status` của đơn | order-service | — | D-02/D-06: order-service consume PaymentSucceeded/Failed |
| Trì hoãn `OrderPlaced` (nhánh VNPAY) | order-service | — | D-09: publish khi consume PaymentSucceeded |
| Trừ kho | inventory-service | — | Không đổi — vẫn consume OrderPlaced (D-11) |
| Trang kết quả + polling | Frontend (browser) | — | D-12/D-13: render `vnp_ResponseCode` + poll `payment_status` |
| Routing IPN/return public không JWT | api-gateway | — | D-15: whitelist `public-endpoints` |

## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** `payment-service` sở hữu TOÀN BỘ logic VNPay — build payment URL, ký/verify `vnp_SecureHash` HMAC SHA512, nhận return + IPN callback, payment state transitions, callback idempotency. `order-service` KHÔNG implement payment callback.
- **D-02:** Cột `payment_status` (PENDING/PAID/FAILED) sở hữu bởi `order-service` — thêm cột vào `OrderEntity` (order-service db migration). `payment-service` giữ trạng thái riêng trong `PaymentSessionEntity` / `PaymentTransactionEntity`.
- **D-03:** Endpoint VNPay tại `payment-service`: `GET /payments/vnpay/return` (browser redirect) + `GET /payments/vnpay/ipn` (server-to-server). Tạo session: `POST /payments/sessions` (đã tồn tại — mở rộng cho provider VNPAY).
- **D-04:** `order-service` gọi REST đồng bộ sang `payment-service` tạo session VNPAY và nhận `paymentUrl`. Dùng pattern client REST sẵn có (tham khảo `ProductBatchClient`).
- **D-05:** Số tiền gửi VNPay (`vnp_Amount`) là final amount = `total` đã trừ `discount_amount`. `vnp_TxnRef` map tới order id; `vnp_OrderInfo` chứa mã đơn.
- **D-06:** Sau verify IPN hợp lệ + so khớp `vnp_Amount`/`vnp_TxnRef`, `payment-service` phát event RabbitMQ `PaymentSucceeded` (`vnp_ResponseCode=00`) hoặc `PaymentFailed`. `order-service` consume cập nhật `payment_status`. KHÔNG cập nhật DB từ return URL.
- **D-07:** IPN idempotent — VNPay gửi lại cùng giao dịch không gây double-update. Trả response đúng format VNPay (`RspCode`/`Message`).
- **D-08:** Chữ ký sai hoặc số tiền lệch → từ chối, ghi log audit, không cập nhật `payment_status` thành PAID.
- **D-09:** Đơn VNPAY: TRÌ HOÃN phát `OrderPlaced` cho tới khi chuyển PAID. `order-service` chỉ phát `OrderPlaced` khi consume `PaymentSucceeded`.
- **D-10:** Đơn COD (và non-VNPAY) giữ nguyên — phát `OrderPlaced` ngay khi tạo đơn.
- **D-11:** KHÔNG triển khai inventory reserve/commit/release 2 pha. Giữ inventory-service nguyên trạng — chỉ thay đổi thời điểm order-service phát `OrderPlaced`.
- **D-12:** Return URL trỏ về route FE trang kết quả (tên cuối do planner chốt). Hiển thị 3 trạng thái: thành công / thất bại / huỷ, đọc sơ bộ từ `vnp_ResponseCode`.
- **D-13:** Trang kết quả POLL `payment_status` (GET order) vài lần để chờ IPN xác nhận. Hiển thị "đang xác nhận" trong lúc chờ.
- **D-14:** Thêm "Thanh toán qua VNPay" vào selector ở `/checkout`. Chọn VNPay + đặt hàng → đơn `payment_method=VNPAY`, `payment_status=PENDING`, FE redirect sang `paymentUrl`.

### Claude's Discretion

- **D-15:** Định tuyến `/payments/vnpay/ipn` + `/return` qua gateway. Phase 25 lock: chỉ gateway lộ host, service nội bộ không port mapping; VNPay không gửi JWT → endpoint IPN/return phải truy cập được không cần JWT, bảo mật dựa verify `vnp_SecureHash`.
- Cấu hình VNPay sandbox (`vnp_TmnCode`, hash secret, `vnp_Url`, `vnp_ReturnUrl`) đọc từ biến môi trường — không hardcode.
- Số lần / khoảng thời gian poll ở trang kết quả.
- Tên route FE trang kết quả + chi tiết UI.

### Deferred Ideas (OUT OF SCOPE)

- **Inventory reserve/commit/release 2 pha** — defer phase riêng tương lai.
- **Hoàn kho khi đơn VNPAY thất bại sau khi đã trừ** — không phát sinh vì D-09 tránh trừ kho trước thanh toán.
- **PaymentExpired / timeout đơn VNPAY bỏ dở** — cải tiến sau (đơn PENDING chưa trừ kho nên vô hại tồn kho).

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PAY-01 | Checkout selector VNPay → order `payment_method=VNPAY` `payment_status=PENDING` → BE build payment URL → FE redirect cổng VNPay | §VNPay Payment URL Spec, §Code Examples (build URL + HMAC), §FE Checkout Integration |
| PAY-02 | Return URL → FE render kết quả từ `vnp_ResponseCode`, KHÔNG cập nhật DB | §Return vs IPN, §FE Result Page + Polling |
| PAY-03 | IPN server-to-server: verify HMAC SHA512, so khớp amount/txnRef, cập nhật PAID/FAILED, idempotent, đúng response format | §IPN Spec, §Idempotency Pattern, §Code Examples (verify hash) |
| PAY-04 | Order display payment method/status/transaction code; chữ ký sai/lệch tiền → từ chối + audit log | §Order Display, §Common Pitfalls (audit log) |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.3.2 (parent BOM) | Service framework | [VERIFIED: codebase pom — toàn bộ services dùng parent 3.3.2] |
| spring-boot-starter-amqp | managed bởi BOM | RabbitMQ producer/consumer | [VERIFIED: order/inventory/notification pom đã có; payment-service CHƯA — cần thêm] |
| spring-boot-starter-data-jpa + Flyway | managed | Migration + persistence | [VERIFIED: payment-service pom đã có (V1__init_schema.sql tồn tại)] |
| `javax.crypto.Mac` (HMAC SHA512) | JDK 17 built-in | Ký + verify `vnp_SecureHash` | [VERIFIED: `Mac.getInstance("HmacSHA512")` là JDK chuẩn — không cần thư viện] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `RestTemplate` (Spring) | managed | order-service → payment-service REST đồng bộ | [VERIFIED: `ProductBatchClient` + `validateStockOrThrow` đã dùng RestTemplate] |
| `java.net.URLEncoder` | JDK 17 | URL-encode params trước khi hash + build query | [CITED: VNPay official sample dùng URLEncoder] |
| `@RabbitListener` | spring-amqp | order-service consume PaymentSucceeded/Failed | [VERIFIED: `OrderPlacedListener` inventory-service đã dùng pattern này] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Tự implement VNPay HMAC | Thư viện `vn.payos` / wrapper bên thứ ba | KHÔNG nên — spec VNPay đơn giản, sample code official công khai; thư viện thêm dependency + rủi ro version drift; dự án demo |
| RestTemplate | WebClient / Feign | RestTemplate đã là pattern dự án (`ProductBatchClient`) — giữ nhất quán |
| Event RabbitMQ cho payment status | REST callback order-service ← payment-service | Event đúng kiến trúc v2 (D-06) + tái dụng hạ tầng Phase 23 + giải tách coupling |

**Installation (payment-service `pom.xml` — thêm):**
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
<!-- test -->
<dependency>
  <groupId>org.springframework.amqp</groupId>
  <artifactId>spring-rabbit-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>rabbitmq</artifactId>
  <scope>test</scope>
</dependency>
```

**Version verification:** Tất cả version managed bởi Spring Boot parent BOM 3.3.2 — không pin version thủ công (pattern dự án). `spring-boot-starter-amqp` trong order-service pom đã verified tồn tại.

## Architecture Patterns

### System Architecture Diagram

```
[FE /checkout]
   │ chọn VNPAY + đặt hàng
   ▼
POST /api/orders ──► [order-service]
                        │ createOrderFromCommand
                        │ payment_method=VNPAY, payment_status=PENDING
                        │ KHÔNG publish OrderPlaced (D-09 — trì hoãn)
                        │ REST đồng bộ (D-04)
                        ▼
                  POST /api/payments/sessions ──► [payment-service]
                        │                            │ tạo PaymentSessionEntity provider=VNPAY
                        │                            │ build payment URL + ký HMAC SHA512
                        │ ◄── { paymentUrl } ────────┘
                        ▼
            response { order, paymentUrl }
   │
   ▼
[FE] window.location = paymentUrl
   │
   ▼
[Cổng VNPay Sandbox]  ──► khách thanh toán
   │                            │
   │ (browser redirect)         │ (server-to-server)
   ▼                            ▼
GET /payments/vnpay/return   GET /payments/vnpay/ipn ──► [payment-service]
   │ (chỉ hiển thị)              │ verify vnp_SecureHash
   ▼                            │ so khớp vnp_Amount + vnp_TxnRef
[FE result page]               │ idempotent (processed_events + txn status)
   │ poll GET /api/orders/{id}  │ update PaymentTransactionEntity
   │ chờ payment_status         │ publish event RabbitMQ
   ▼                            ▼
 hiển thị PAID/FAILED      exchange `payment.events` (topic)
                                │  routing key payment.succeeded / payment.failed
                                ▼
                          queue `order.payment-events` ──► [order-service consumer]
                                                              │ idempotent (processed_events)
                                                              │ update OrderEntity.payment_status
                                                              │ nếu PaymentSucceeded → publish OrderPlaced
                                                              ▼
                                                       exchange `order.events`
                                                              ▼
                                              [inventory-service] decrementForOrder (không đổi)
```

### Recommended Project Structure (payment-service — mới)
```
payment-service/src/main/java/.../paymentservice/
├── vnpay/
│   ├── VNPayConfig.java          # @ConfigurationProperties: tmnCode, hashSecret, payUrl, returnUrl, ipnUrl
│   ├── VNPaySignature.java       # ký + verify HMAC SHA512 (static utils)
│   ├── VNPayService.java         # build payment URL, xử lý IPN/return logic
│   └── VNPayController.java      # GET /payments/vnpay/return + /ipn
├── messaging/
│   ├── config/RabbitMQConfig.java        # exchange payment.events + (declare order queue binding)
│   ├── event/PaymentEventEnvelope.java   # envelope giống OrderEventEnvelope
│   └── publisher/PaymentEventPublisher.java
├── domain/  (mở rộng PaymentSessionEntity / PaymentTransactionEntity nếu cần cột mới)
└── web/PaymentController.java    # mở rộng POST /payments/sessions cho provider VNPAY
```

### Pattern 1: Build VNPay payment URL + ký HMAC SHA512
**What:** Sắp xếp tham số tăng dần theo tên khóa, URL-encode value, nối `key=value&...`, hash HMAC SHA512 với `vnp_HashSecret`.
**When to use:** Khi tạo session VNPAY (`POST /payments/sessions` provider=VNPAY).
**Example:** xem §Code Examples.

### Pattern 2: REST cross-service đồng bộ (order-service → payment-service)
**What:** order-service gọi `POST http://api-gateway:8080/api/payments/sessions` qua RestTemplate, nhận `paymentUrl`. KHÔNG forward JWT (endpoint nội bộ payment-service không yêu cầu role) — nhưng request đi qua gateway sẽ mang `X-User-Id` đã inject; payment-service không cần userId.
**When to use:** Nhánh VNPAY trong `OrderCrudService.createOrderFromCommand`.
**Decision điểm cần planner chốt:** gọi qua gateway (`http://api-gateway:8080/api/payments/...`) như `ProductBatchClient` HAY gọi trực tiếp service-to-service (`http://payment-service:8080/payments/...`). Khuyến nghị gọi qua gateway để nhất quán với `ProductBatchClient` + `validateStockOrThrow` (cả hai đã dùng `http://api-gateway:8080`). Lưu ý: `POST /api/payments/sessions` KHÔNG public → cần Bearer JWT forward HOẶC whitelist. Vì order-service tạo session khi xử lý request đã-auth của user, **forward Bearer JWT** (pattern `ProductBatchClient.fetchBatch(ids, authHeader)`) là sạch nhất.

### Pattern 3: Event-driven payment status (Phase 23 envelope reuse)
**What:** payment-service phát `PaymentSucceeded`/`PaymentFailed` vào exchange topic mới `payment.events`; order-service consume từ queue mới `order.payment-events`. Envelope theo cùng cấu trúc `OrderEventEnvelope` (eventId/eventType/occurredAt/traceId/payload).
**When to use:** Sau khi IPN verify xong (D-06).
**Anti-pattern tránh:** KHÔNG reuse exchange `order.events` cho payment events — `order.events` bind `order.#` đi tới inventory + notification; payment events cần exchange riêng để order-service consume mà không leak sang inventory.

### Pattern 4: Idempotent IPN callback
**What:** IPN có thể được VNPay gửi lại tới 10 lần (cách nhau 5 phút). payment-service phải: (1) check `PaymentTransactionEntity.status` hiện tại — nếu đã PAID/FAILED → trả `RspCode=02` (đã xử lý) và KHÔNG update lại; (2) HOẶC dùng bảng `processed_events` (PK trên `vnp_TxnRef` + `vnp_TransactionNo` hoặc `vnp_TransactionNo` đơn lẻ).
**When to use:** `GET /payments/vnpay/ipn`.
**Khuyến nghị:** Dùng `PaymentTransactionEntity.status` làm idempotency key chính (transaction đã tồn tại với status terminal → return 02). Có thể thêm bảng `processed_events` trong payment_svc nếu muốn audit như Phase 23 — nhưng status-check trên transaction là đủ và đơn giản hơn cho IPN.

### Anti-Patterns to Avoid
- **Cập nhật `payment_status` từ return URL:** return URL có thể bị user giả mạo/bypass (D-06). CHỈ IPN cập nhật.
- **URL-encode không nhất quán giữa build hash và verify hash:** nguyên nhân #1 lỗi "sai chữ ký". Dùng CÙNG MỘT hàm encode cho cả hai chiều.
- **Quên loại `vnp_SecureHash` (và `vnp_SecureHashType` nếu có) khỏi tập tham số khi verify.**
- **Reuse exchange `order.events` cho payment events** → inventory/notification consume nhầm.
- **Phát `OrderPlaced` ngay khi tạo đơn VNPAY** → trừ kho trước khi thanh toán → leak kho (vi phạm D-09).
- **So sánh `vnp_Amount` trực tiếp với `order.total`** mà quên VNPay đã nhân 100 → luôn báo lệch tiền.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HMAC SHA512 | Tự viết SHA512 | `javax.crypto.Mac.getInstance("HmacSHA512")` | JDK built-in, đúng chuẩn |
| RabbitMQ topology/retry/DLQ | Config mới từ đầu | Copy pattern `RabbitMQConfig` Phase 23 (exchange/DLX/retry yml) | Hạ tầng đã verified Phase 23 |
| Idempotency table | Logic ad-hoc | Pattern `ProcessedEventRepository.insertIfAbsent` (ON CONFLICT DO NOTHING) — nếu cần | Đã có precedent inventory/notification |
| Publish-after-commit | Publish trong tx | `OrderEventPublisher` pattern (`TransactionSynchronizationManager.afterCommit`) | Tránh phantom event nếu tx rollback |
| Cross-service REST | HttpClient thô | `RestTemplate` pattern `ProductBatchClient` | Nhất quán dự án + error fallback đã có |

**Key insight:** Phase 23 đã xây toàn bộ hạ tầng messaging (envelope record, topic exchange, DLX, retry exponential backoff trong `application.yml`, processed_events idempotency, traceId propagation). Phase 26 chỉ cần (a) thêm exchange `payment.events` + queue mới, (b) copy `RabbitMQConfig`/envelope/publisher vào payment-service, (c) thêm consumer vào order-service. KHÔNG phát minh lại pattern.

## Runtime State Inventory

> Phase này KHÔNG phải rename/refactor — chủ yếu thêm tính năng mới. Vẫn rà các điểm runtime state quan trọng:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | `OrderEntity` thiếu cột `payment_status` + `vnp_transaction_no` | Flyway migration order-svc (V6) thêm 2 cột |
| Live service config | VNPay sandbox merchant config (`vnp_TmnCode`, `vnp_HashSecret`, IPN URL) — đăng ký tại portal sandbox.vnpayment.vn, KHÔNG ở git | User phải đăng ký tài khoản sandbox VNPay + cấu hình IPN URL trỏ về gateway public của môi trường demo |
| OS-registered state | Không có | None — verified không có task scheduler/systemd liên quan |
| Secrets/env vars | `VNP_TMN_CODE`, `VNP_HASH_SECRET`, `VNP_PAY_URL`, `VNP_RETURN_URL`, `VNP_IPN_URL` — env vars mới cho payment-service | Thêm vào docker-compose.yml env block của payment-service + tài liệu .env.example |
| Build artifacts | Không có | None — không rename package/artifact |

**Lưu ý quan trọng:** VNPay IPN gọi server-to-server từ internet → cần URL public truy cập được. Trong môi trường demo local (docker-compose, không deploy public), IPN sẽ KHÔNG tới được nếu chạy localhost thuần. Planner cần xét: (a) demo qua tunnel (ngrok/cloudflared) để VNPay gọi IPN tới, HOẶC (b) demo IPN bằng cách gọi thủ công endpoint `/payments/vnpay/ipn` với payload mô phỏng (curl/Postman) — sandbox vẫn redirect return URL bình thường. Đây là **Open Question** cần làm rõ với user.

## Common Pitfalls

### Pitfall 1: Sai chữ ký `vnp_SecureHash`
**What goes wrong:** VNPay trả lỗi chữ ký hoặc verify IPN luôn fail.
**Why it happens:** URL-encoding không nhất quán giữa lúc build hash và lúc verify; không sort tăng dần; quên loại `vnp_SecureHash`/`vnp_SecureHashType` khi verify; encode space thành `+` vs `%20` khác nhau.
**How to avoid:** Dùng MỘT helper `hashAllFields(Map sorted)` cho cả ký lẫn verify. Sort bằng `TreeMap` hoặc `Collections.sort(fieldNames)`. Encode bằng `URLEncoder.encode(value, StandardCharsets.US_ASCII)` (giống VNPay official sample). Khi verify, build lại `SortedMap` từ request params, REMOVE `vnp_SecureHash` + `vnp_SecureHashType`, rồi hash so sánh case-insensitive.
**Warning signs:** Hash tính ra khác hash VNPay gửi; payment redirect báo lỗi ngay.

### Pitfall 2: `vnp_Amount` quên nhân 100
**What goes wrong:** Cổng VNPay hiển thị số tiền sai gấp 100 lần, hoặc verify IPN báo lệch tiền.
**Why it happens:** VNPay quy ước `vnp_Amount` = số tiền VND × 100 (loại bỏ phần thập phân).
**How to avoid:** Khi build: `vnp_Amount = order.total().multiply(BigDecimal.valueOf(100)).toBigInteger().toString()`. Khi verify IPN: so sánh `Long.parseLong(vnp_Amount)` với `expectedAmount × 100`.
**Warning signs:** Số tiền cổng VNPay = 100× hoặc 1/100 số tiền đơn.

### Pitfall 3: IPN double-update khi VNPay retry
**What goes wrong:** VNPay gửi lại IPN (tới 10 lần) → `payment_status` bị cập nhật nhiều lần, event phát trùng.
**Why it happens:** Không kiểm tra trạng thái transaction trước khi xử lý.
**How to avoid:** Đầu handler IPN: load `PaymentTransactionEntity` theo `vnp_TxnRef`/`vnp_TransactionNo`; nếu status đã terminal (PAID/FAILED) → trả `{"RspCode":"02","Message":"Order already confirmed"}` và return ngay, KHÔNG update + KHÔNG phát event lại. order-service consumer cũng idempotent qua `processed_events` (eventId).
**Warning signs:** Log thấy event PaymentSucceeded phát 2+ lần cho cùng đơn.

### Pitfall 4: Endpoint IPN/return bị gateway chặn 401 (Phase 25 JWT filter)
**What goes wrong:** VNPay gọi `GET /api/payments/vnpay/ipn` → gateway trả 401 vì không có Bearer JWT.
**Why it happens:** `JwtAuthenticationFilter` reject mọi non-public endpoint thiếu token.
**How to avoid:** Thêm vào `api-gateway/application.yml` `app.auth.public-endpoints`:
```yaml
- { method: GET, pattern: "/api/payments/vnpay/ipn" }
- { method: GET, pattern: "/api/payments/vnpay/return" }
```
Bảo mật đảm bảo bằng verify `vnp_SecureHash` + so khớp amount/txnRef trong payment-service (D-15).
**Warning signs:** VNPay báo IPN fail; log gateway thấy `AUTH_TOKEN_MISSING`.

### Pitfall 5: Cập nhật DB từ return URL
**What goes wrong:** User có thể giả mạo query string return URL → đánh dấu đơn PAID không thật.
**Why it happens:** Nhầm return URL là nguồn sự thật.
**How to avoid:** `GET /payments/vnpay/return` CHỈ verify chữ ký để hiển thị, KHÔNG update DB, KHÔNG phát event. Chỉ IPN cập nhật (D-06). FE poll `payment_status` (D-13).

### Pitfall 6: `vnp_TxnRef` không unique trong ngày
**What goes wrong:** VNPay từ chối hoặc gộp nhầm giao dịch.
**Why it happens:** VNPay yêu cầu `vnp_TxnRef` unique theo ngày. Nếu dùng thẳng order id (UUID) thì OK vì UUID luôn unique. Nhưng nếu user retry thanh toán cùng đơn → cùng `vnp_TxnRef` → conflict.
**How to avoid:** `vnp_TxnRef` = `paymentSessionId` (mỗi lần tạo session sinh UUID mới) thay vì order id trực tiếp. `vnp_OrderInfo` chứa mã đơn để người dùng đọc. payment-service map `vnp_TxnRef` → session → order khi xử lý IPN.
**Warning signs:** Retry thanh toán cùng đơn báo lỗi trùng giao dịch.

## Code Examples

### Build payment URL + ký HMAC SHA512
```java
// Source: phỏng theo VNPay official sandbox sample (sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)
public String buildPaymentUrl(String txnRef, long amountVnd, String orderInfo, String clientIp) {
  Map<String, String> p = new TreeMap<>(); // TreeMap → tự sort tăng dần theo key
  p.put("vnp_Version", "2.1.0");
  p.put("vnp_Command", "pay");
  p.put("vnp_TmnCode", cfg.tmnCode());
  p.put("vnp_Amount", String.valueOf(amountVnd * 100));      // Pitfall 2: nhân 100
  p.put("vnp_CurrCode", "VND");
  p.put("vnp_TxnRef", txnRef);                               // Pitfall 6: dùng sessionId
  p.put("vnp_OrderInfo", orderInfo);                         // ASCII, không dấu, không ký tự đặc biệt
  p.put("vnp_OrderType", "other");
  p.put("vnp_Locale", "vn");
  p.put("vnp_ReturnUrl", cfg.returnUrl());
  p.put("vnp_IpAddr", clientIp);
  String now = LocalDateTime.now(ZoneId.of("Etc/GMT-7"))
      .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  p.put("vnp_CreateDate", now);
  p.put("vnp_ExpireDate", LocalDateTime.now(ZoneId.of("Etc/GMT-7"))
      .plusMinutes(15).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

  StringBuilder hashData = new StringBuilder();
  StringBuilder query = new StringBuilder();
  for (Iterator<Map.Entry<String,String>> it = p.entrySet().iterator(); it.hasNext();) {
    Map.Entry<String,String> e = it.next();
    String encKey = URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII);
    String encVal = URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII);
    hashData.append(encKey).append('=').append(encVal);
    query.append(encKey).append('=').append(encVal);
    if (it.hasNext()) { hashData.append('&'); query.append('&'); }
  }
  String secureHash = hmacSHA512(cfg.hashSecret(), hashData.toString());
  query.append("&vnp_SecureHash=").append(secureHash);
  return cfg.payUrl() + "?" + query;
}

private static String hmacSHA512(String key, String data) {
  try {
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  } catch (Exception ex) {
    throw new IllegalStateException("HMAC SHA512 failed", ex);
  }
}
```

### Verify IPN signature
```java
// Source: phỏng theo VNPay official sample — verify
public boolean verifySignature(Map<String, String> params) {
  String received = params.get("vnp_SecureHash");
  Map<String, String> signed = new TreeMap<>(params);
  signed.remove("vnp_SecureHash");        // Pitfall 1: phải loại trước khi hash
  signed.remove("vnp_SecureHashType");
  StringBuilder hashData = new StringBuilder();
  for (Iterator<Map.Entry<String,String>> it = signed.entrySet().iterator(); it.hasNext();) {
    Map.Entry<String,String> e = it.next();
    hashData.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII))
            .append('=')
            .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
    if (it.hasNext()) hashData.append('&');
  }
  String computed = hmacSHA512(cfg.hashSecret(), hashData.toString());
  return computed.equalsIgnoreCase(received);
}
```

### IPN handler response (D-07)
```java
// GET /payments/vnpay/ipn — controller trả Map → Jackson serialize JSON {"RspCode","Message"}
@GetMapping("/vnpay/ipn")
public Map<String, String> ipn(@RequestParam Map<String, String> params) {
  if (!vnPayService.verifySignature(params))
    return Map.of("RspCode", "97", "Message", "Invalid signature"); // VNPay retry
  // load session theo vnp_TxnRef; so khớp amount
  // nếu transaction đã terminal → return RspCode 02
  // nếu amount lệch → return RspCode 04 + log audit (D-08)
  // hợp lệ + vnp_ResponseCode=00 → update transaction PAID + publish PaymentSucceeded → RspCode 00
  // vnp_ResponseCode != 00 → update FAILED + publish PaymentFailed → RspCode 00 (đã ghi nhận)
}
```
**Lưu ý:** Endpoint IPN/return KHÔNG nên đi qua `ApiResponseAdvice` envelope wrapping — VNPay yêu cầu JSON THUẦN `{"RspCode","Message"}`. Kiểm tra `ApiResponseAdvice` của payment-service có wrap mọi response không; nếu có, cần loại trừ controller VNPay (vd `@RestControllerAdvice` với `basePackages` hoặc trả `ResponseEntity` raw).

### Flyway migration order-service (V6)
```sql
-- order-svc V6: payment_status + vnp_transaction_no cho VNPay
ALTER TABLE orders ADD COLUMN payment_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE orders ADD COLUMN vnp_transaction_no VARCHAR(50);
-- order COD cũ: payment_status mặc định PENDING (hoặc xét backfill PAID tùy nghiệp vụ)
```
**Lưu ý V-number:** order-svc đã có V1,V2,V4,V5 (V3 = coupons theo STATE.md reservation). **Migration mới = V6** (V3 đã consumed bởi coupons trong Phase 20 — verified `V5__add_coupons.sql` tồn tại, nhưng STATE bảng reservation ghi V3; planner cần verify số V cao nhất hiện có là V5 → dùng V6).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| VNPay HMAC SHA256 | HMAC SHA512 (v2.1.0) | VNPay v2.1.0 | Dùng `HmacSHA512`, KHÔNG SHA256 |
| Payment URL `vpcpay.html` cũ | `paymentv2/vpcpay.html` | v2.1.0 | Endpoint sandbox: `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html` |
| Cập nhật trạng thái từ return URL | IPN là nguồn sự thật | Best practice hiện hành | return URL chỉ hiển thị (D-06) |

**Deprecated/outdated:**
- HMAC SHA256 / MD5 cho VNPay: thay bằng SHA512.
- `payments.ts` FE comment "payment flow runs through order-service.createOrder" — Phase 26 thay đổi: nhánh VNPAY nay cần `paymentUrl` từ response createOrder hoặc endpoint riêng.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | order-svc migration mới là V6 (V5 là số cao nhất hiện có) | §Code Examples | Flyway fail nếu V6 đã tồn tại — planner verify `ls db/migration` |
| A2 | `ApiResponseAdvice` payment-service wrap mọi response — cần loại trừ controller VNPay | §IPN handler | Nếu không loại trừ, VNPay nhận JSON sai format → IPN fail. Planner verify file `ApiResponseAdvice.java` |
| A3 | Demo local không có URL public → IPN cần tunnel hoặc gọi thủ công | §Runtime State Inventory | Nếu user kỳ vọng IPN tự động chạy mà không tunnel → demo PAY-03 không quan sát được tự nhiên |
| A4 | `vnp_OrderType="other"` chấp nhận được cho sandbox | §Code Examples | VNPay sandbox thường lỏng với OrderType; production có danh mục cụ thể |
| A5 | order-service forward Bearer JWT khi gọi payment-service tạo session (qua gateway) | §Pattern 2 | Nếu gọi service-to-service trực tiếp thì không cần JWT — planner chốt |
| A6 | payment-service dùng status terminal của `PaymentTransactionEntity` làm idempotency key (không cần bảng processed_events riêng) | §Pattern 4 | Nếu chọn bảng processed_events cần thêm migration payment-svc |

## Open Questions

1. **IPN trong môi trường demo local — VNPay gọi tới được không?**
   - What we know: VNPay IPN là server-to-server từ internet; demo chạy docker-compose localhost.
   - What's unclear: User demo qua tunnel (ngrok/cloudflared) hay chấp nhận gọi IPN thủ công?
   - Recommendation: Planner làm rõ với user. Mặc định: hỗ trợ cả hai — code IPN endpoint chuẩn (chạy được khi có tunnel) + cung cấp script curl mô phỏng IPN cho demo offline.

2. **`vnp_TxnRef` = order id hay payment session id?**
   - What we know: VNPay yêu cầu unique theo ngày; retry thanh toán cùng đơn cần txnRef mới.
   - Recommendation: Dùng `paymentSessionId` (UUID mới mỗi session) — cho phép retry. `vnp_OrderInfo` mang mã đơn.

3. **`paymentUrl` trả về FE như thế nào?**
   - What we know: D-04 — order-service gọi payment-service nhận paymentUrl.
   - What's unclear: `paymentUrl` nằm trong response của `POST /api/orders` (mở rộng `OrderDto`) hay FE gọi endpoint riêng sau khi tạo đơn?
   - Recommendation: Thêm field `paymentUrl` (nullable) vào `OrderDto` — chỉ có giá trị khi `payment_method=VNPAY`. FE đọc `order.paymentUrl` → `window.location`. Đơn giản nhất, 1 round-trip.

4. **Backfill `payment_status` cho order COD cũ?**
   - Recommendation: COD cũ giữ `payment_status=PENDING` (default migration) hoặc set theo `order.status` (DELIVERED → PAID). Planner chốt — không ảnh hưởng VNPay scope.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| RabbitMQ (docker-compose) | payment events | ✓ | rabbitmq:3-management (Phase 23) | — |
| Postgres payment_svc container | payment-service | ✓ | Phase 24 | — |
| spring-boot-starter-amqp (payment-svc) | payment publisher | ✗ | — | Thêm vào pom.xml |
| VNPay sandbox merchant account | PAY-01..04 | ✗ (cần user đăng ký) | — | Không có fallback — user phải đăng ký tại sandbox.vnpayment.vn lấy TmnCode + HashSecret |
| URL public / tunnel cho IPN | PAY-03 demo | ✗ | — | Gọi IPN thủ công bằng curl mô phỏng (xem Open Q #1) |

**Missing dependencies với no fallback:**
- VNPay sandbox merchant credentials (`vnp_TmnCode`, `vnp_HashSecret`) — user phải tự đăng ký. Là blocker cho test thực tế, KHÔNG blocker cho code (env var placeholder).

**Missing dependencies với fallback:**
- `spring-boot-starter-amqp` payment-service — thêm pom dependency.
- URL public cho IPN — fallback gọi thủ công.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (Postgres + RabbitMQ) |
| Config file | `payment-service/src/test/resources/application-test.yml` (cần tạo — chưa có, chỉ có `init-test-schema.sql`) |
| Quick run command | `mvn -pl payment-service test -Dtest=VNPaySignatureTest` |
| Full suite command | `mvn -pl payment-service,order-service verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| PAY-01 | Build payment URL đúng params + hash | unit | `mvn -pl payment-service test -Dtest=VNPaySignatureTest` | ❌ Wave 0 |
| PAY-01 | order-service nhánh VNPAY tạo session + nhận paymentUrl | integration | `mvn -pl order-service test -Dtest=OrderCrudServiceVNPayIT` | ❌ Wave 0 |
| PAY-03 | Verify HMAC SHA512 — chữ ký đúng/sai | unit | `mvn -pl payment-service test -Dtest=VNPaySignatureTest` | ❌ Wave 0 |
| PAY-03 | IPN idempotent — gửi lại không double-update | integration | `mvn -pl payment-service test -Dtest=VNPayIpnControllerIT` | ❌ Wave 0 |
| PAY-03 | IPN amount lệch → từ chối + không PAID | integration | `mvn -pl payment-service test -Dtest=VNPayIpnControllerIT` | ❌ Wave 0 |
| PAY-03 | order-service consume PaymentSucceeded → payment_status=PAID + publish OrderPlaced | integration | `mvn -pl order-service test -Dtest=PaymentEventListenerIT` | ❌ Wave 0 |
| PAY-02/04 | FE result page + order display | manual / Playwright smoke | `npx playwright test vnpay` | ❌ Wave 0 (manual chấp nhận) |

### Sampling Rate
- **Per task commit:** `mvn -pl <service> test -Dtest=<RelevantTest>`
- **Per wave merge:** `mvn -pl payment-service,order-service test`
- **Phase gate:** `mvn -pl payment-service,order-service verify` xanh trước `/gsd-verify-work`

### Wave 0 Gaps
- [ ] `payment-service/src/test/resources/application-test.yml` — Testcontainers config (Postgres + RabbitMQ @ServiceConnection)
- [ ] `VNPaySignatureTest.java` — unit test ký + verify HMAC SHA512 (vector cố định)
- [ ] `VNPayIpnControllerIT.java` — IPN happy/idempotent/amount-mismatch/bad-signature
- [ ] `OrderCrudServiceVNPayIT.java` — nhánh VNPAY tạo session
- [ ] `PaymentEventListenerIT.java` (order-service) — consume PaymentSucceeded/Failed
- [ ] payment-service pom: thêm `spring-rabbit-test` + `testcontainers:rabbitmq`

*Lưu ý: STATE.md ghi "Maven CLI defer trên Windows env" — pattern dự án là viết test đầy đủ, defer runtime cho CI/`/gsd-verify-work`.*

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Endpoint IPN/return public — bảo mật bằng HMAC chứ không JWT (D-15) |
| V3 Session Management | no | Không session mới |
| V4 Access Control | yes | order ownership check đã có (`getOrderForUser`); IPN không cần user auth |
| V5 Input Validation | yes | Validate mọi `vnp_*` param; reject nếu thiếu hoặc sai định dạng |
| V6 Cryptography | yes | HMAC SHA512 với `vnp_HashSecret` — KHÔNG hand-roll; `javax.crypto.Mac` |
| V9 Communication | yes | VNPay yêu cầu HTTPS cho IPN production; sandbox demo có thể HTTP qua tunnel |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Giả mạo return URL đánh dấu đơn PAID | Spoofing/Tampering | Không update DB từ return URL — chỉ IPN (D-06) |
| Thay đổi `vnp_Amount` trong callback | Tampering | Verify HMAC + so khớp `vnp_Amount` với `order.total × 100` (D-08) |
| Replay IPN (gửi lại payload cũ) | Tampering | Idempotency qua transaction status terminal + processed_events (D-07) |
| Lộ `vnp_HashSecret` | Information Disclosure | Đọc từ env var, KHÔNG hardcode, KHÔNG commit; KHÔNG log secret |
| Brute-force IPN endpoint không auth | DoS/Spoofing | HMAC verify reject sớm; log audit chữ ký sai (D-08) |
| Lệch tiền (amount mismatch) | Tampering | So khớp số tiền — lệch → RspCode 04 + audit log, không PAID (D-08) |

## Sources

### Primary (HIGH confidence)
- Codebase inspection trực tiếp: `payment-service` (PaymentController, PaymentSessionEntity, PaymentTransactionEntity, V1__init_schema.sql, application.yml), `order-service` (OrderCrudService, OrderEntity, OrderEventPublisher, OrderEventEnvelope, RabbitMQConfig, ProductBatchClient), `inventory-service` (OrderPlacedListener, ProcessedEventRepository), `api-gateway` (application.yml, JwtAuthenticationFilter), FE (checkout/page.tsx, orders.ts, payments.ts, profile/orders/[id]/page.tsx, types/index.ts)
- VNPay official sandbox docs — `https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html` — payment URL params, HMAC SHA512 algorithm, IPN spec, response codes

### Secondary (MEDIUM confidence)
- WebSearch VNPay Java integration — xác nhận HMAC SHA512 v2.1.0, IPN best practices, "return URL chỉ UI feedback"
- `architecture/services/payment-service.md` — xác nhận ownership v2 (payment-service sở hữu VNPay logic, IPN là source of truth)

### Tertiary (LOW confidence)
- Cộng đồng VNPay Java samples (GitHub pad1092/VNPAY-Springboot-Demo, Viblo) — tham khảo pattern, KHÔNG copy trực tiếp

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — codebase inspection trực tiếp + JDK built-in crypto
- VNPay spec: HIGH — official sandbox docs đầy đủ params + algorithm
- Architecture/event pattern: HIGH — Phase 23 hạ tầng đã verified, tái dụng trực tiếp
- Pitfalls: HIGH — pitfall chữ ký/amount là kinh điển VNPay, có cross-source
- IPN demo môi trường: MEDIUM — phụ thuộc quyết định tunnel của user (Open Q #1)

**Research date:** 2026-05-22
**Valid until:** 2026-06-21 (VNPay v2.1.0 ổn định; codebase facts cần re-verify nếu Phase 24/25 thay đổi)
