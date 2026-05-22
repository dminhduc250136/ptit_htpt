# Phase 26: Tích Hợp Thanh Toán VNPay Sandbox - Context

**Gathered:** 2026-05-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Khách tại `/checkout` chọn phương thức "Thanh toán qua VNPay" → `order-service` tạo đơn `payment_status=PENDING` và gọi `payment-service` lấy `paymentUrl` → FE redirect sang cổng VNPay sandbox với đúng số tiền (đã trừ coupon) và mã đơn → khách quay về return URL thấy trang kết quả → `payment-service` nhận IPN server-to-server, verify HMAC SHA512, cập nhật trạng thái giao dịch idempotent và phát event → `order-service` consume event cập nhật `payment_status` đơn hàng. Đơn hiển thị đúng phương thức + trạng thái + mã giao dịch VNPay tại `/account/orders/[id]` và `/admin/orders/[id]`.

Phạm vi: PAY-01..PAY-04. KHÔNG bao gồm viết lại inventory-service sang mô hình reserve/commit/release 2 pha.

</domain>

<decisions>
## Implementation Decisions

### Service ownership (khóa theo kiến trúc v2 có sẵn)
- **D-01:** `payment-service` sở hữu TOÀN BỘ logic VNPay — build payment URL, ký/verify `vnp_SecureHash` HMAC SHA512, nhận return + IPN callback, payment state transitions, callback idempotency. `order-service` KHÔNG implement payment callback (arch order-service: "Đừng implement payment callback ở order-service trong kiến trúc v2").
- **D-02:** Cột `payment_status` (PENDING/PAID/FAILED) được sở hữu bởi `order-service` — thêm cột vào `OrderEntity` (order-service db migration). `payment-service` giữ trạng thái riêng trong `PaymentSessionEntity` / `PaymentTransactionEntity`.
- **D-03:** Endpoint VNPay tại `payment-service`: `GET /payments/vnpay/return` (browser redirect) + `GET /payments/vnpay/ipn` (server-to-server). Tạo session: `POST /payments/sessions` (đã tồn tại — mở rộng cho provider VNPAY).

### Tạo payment session (cross-service)
- **D-04:** `order-service` gọi REST đồng bộ sang `payment-service` để tạo session VNPAY và nhận về `paymentUrl` (UC-CHECKOUT-PAYMENT flow B.1–B.2). Dùng pattern client REST sẵn có trong order-service (tham khảo `ProductBatchClient`).
- **D-05:** Số tiền gửi VNPay (`vnp_Amount`) là final amount = `total` đã trừ `discount_amount` của đơn. `vnp_TxnRef` map tới order id; `vnp_OrderInfo` chứa mã đơn.

### IPN → cập nhật trạng thái đơn (event-based, theo arch + hạ tầng Phase 23)
- **D-06:** Sau khi verify IPN hợp lệ + so khớp `vnp_Amount`/`vnp_TxnRef`, `payment-service` phát event RabbitMQ `PaymentSucceeded` (khi `vnp_ResponseCode=00`) hoặc `PaymentFailed`. `order-service` consume event này để cập nhật `payment_status`. KHÔNG cập nhật DB dựa trên return URL — return URL chỉ để hiển thị; IPN là nguồn sự thật.
- **D-07:** IPN idempotent — VNPay gửi lại cùng giao dịch không gây double-update (kiểm tra trạng thái transaction hiện tại trước khi cập nhật). Trả response đúng format VNPay yêu cầu (`RspCode`/`Message`).
- **D-08:** Chữ ký sai hoặc số tiền lệch → giao dịch bị từ chối, ghi log audit, không cập nhật `payment_status` thành PAID.

### Đơn PENDING & tồn kho
- **D-09:** Với đơn phương thức VNPAY: TRÌ HOÃN phát event `OrderPlaced` (event hiện trigger inventory `decrementForOrder`) cho tới khi đơn chuyển PAID. `order-service` chỉ phát `OrderPlaced` khi consume `PaymentSucceeded`. Đơn VNPAY chưa thanh toán → không trừ kho → không leak kho khi thanh toán thất bại/bỏ dở.
- **D-10:** Đơn COD (và các phương thức non-VNPAY) giữ nguyên hành vi hiện tại — phát `OrderPlaced` ngay khi tạo đơn.
- **D-11:** KHÔNG triển khai mô hình inventory reserve/commit/release 2 pha trong phase này (arch v2 mô tả nhưng code Phase 23 hiện chỉ 1 pha decrement). Giữ inventory-service nguyên trạng — chỉ thay đổi thời điểm order-service phát `OrderPlaced`.

### Trang kết quả thanh toán (FE)
- **D-12:** Return URL trỏ về một route FE trang kết quả (vd `/checkout/result` — tên cuối do planner chốt). Hiển thị 3 trạng thái: thành công / thất bại / huỷ, đọc sơ bộ từ `vnp_ResponseCode`.
- **D-13:** Sau khi render sơ bộ, trang kết quả POLL `payment_status` của đơn (GET order) vài lần để chờ IPN xác nhận — vì return URL không phải nguồn sự thật. Hiển thị "đang xác nhận" trong lúc chờ, cập nhật khi IPN tới.

### Checkout payment selector (FE)
- **D-14:** Thêm lựa chọn "Thanh toán qua VNPay" vào selector phương thức ở `/checkout` (hiện có `COD | BANK_TRANSFER | E_WALLET` trong `page.tsx`). Khi chọn VNPay + đặt hàng → đơn tạo `payment_method=VNPAY`, `payment_status=PENDING`, FE redirect sang `paymentUrl`.

### Claude's Discretion
- **Định tuyến IPN qua gateway** (D-15): planner/researcher quyết cách định tuyến `/payments/vnpay/ipn` + `/return`. Lưu ý ràng buộc Phase 25: chỉ gateway lộ ra host, service nội bộ không có port mapping; VNPay là bên ngoài không gửi được JWT → endpoint IPN/return phải truy cập được mà không cần JWT, bảo mật dựa trên verify `vnp_SecureHash`. Chọn cách nhất quán với cấu hình gateway hiện tại.
- Cấu hình VNPay sandbox (`vnp_TmnCode`, hash secret, `vnp_Url`, `vnp_ReturnUrl`) đọc từ biến môi trường — không hardcode.
- Số lần / khoảng thời gian poll ở trang kết quả.
- Tên route FE trang kết quả + chi tiết UI.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Kiến trúc service (nguồn quyết định chính — phase này bám sát kiến trúc có sẵn)
- `architecture/services/payment-service.md` — Ownership của payment-service: build payment URL, verify callback, idempotency; key APIs `/payments/sessions`, `/payments/vnpay/return`, `/payments/vnpay/ipn`; publish `PaymentSucceeded`/`PaymentFailed`/`PaymentExpired`
- `architecture/services/order-service.md` — order-service sở hữu order state machine; consume `PaymentSucceeded`/`PaymentFailed`; KHÔNG sở hữu VNPay callback signature logic
- `ba/order-service/uc-checkout-payment.md` — UC-CHECKOUT-PAYMENT §B (VNPay flow 8 bước) + §C (COD) + exception flows (signature invalid, payment timeout, callback duplicate)
- `architecture/02-sequence-diagrams.md` — Sequence diagrams checkout/payment + event topology RabbitMQ

### Yêu cầu
- `.planning/REQUIREMENTS.md` §PAY-01..PAY-04 — Acceptance criteria checkout selector, return URL, IPN callback, order display
- `.planning/ROADMAP.md` §"Phase 26" — Goal + 4 Success Criteria

### Hạ tầng tham chiếu (Phase 23, 25)
- `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/messaging/` — RabbitMQConfig + OrderEventPublisher + OrderEventEnvelope (pattern publish event afterCommit)
- `sources/backend/inventory-service/src/main/java/com/ptit/htpt/inventoryservice/messaging/consumer/OrderPlacedListener.java` — consumer `OrderPlaced` hiện gọi `decrementForOrder` (1 pha)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `payment-service` đã có `PaymentSessionEntity` (orderId/provider/amount/status) + `PaymentTransactionEntity` (sessionId/reference/amount/method/status/message) + `PaymentController` với `POST /payments/sessions`, `GET /payments/sessions/{id}`, transactions CRUD — mở rộng cho VNPAY thay vì tạo mới từ đầu
- `order-service/.../service/ProductBatchClient.java` — pattern REST client cross-service sẵn có để order-service gọi payment-service
- `order-service/.../messaging/` — RabbitMQConfig + OrderEventPublisher (publish afterCommit + CorrelationData) — pattern để payment-service phát `PaymentSucceeded`/`PaymentFailed` và order-service consume
- FE `sources/frontend/src/app/checkout/page.tsx` — selector `paymentMethod`, gọi `createOrder` (`services/orders`), đã tích hợp coupon (`discountAmount`)

### Established Patterns
- Event envelope chung `OrderEventEnvelope` + topology RabbitMQ 3-service (Phase 23) — payment events nên theo cùng pattern envelope/exchange
- JPA entity factory-method + soft-delete (`@SQLDelete`/`@SQLRestriction`) + Flyway migration per-service
- order-service và payment-service mỗi cái một Postgres container riêng (Phase 24) — không query chéo DB, mọi liên lạc qua REST hoặc event

### Integration Points
- `OrderEntity` (order-service) — thêm cột `payment_status` (+ có thể `vnp_transaction_no`) qua Flyway migration
- order-service `POST /checkout` / `OrderCrudService` — nhánh VNPAY: gọi payment-service tạo session, hoãn publish `OrderPlaced`
- payment-service — thêm controller endpoint `/payments/vnpay/return` + `/payments/vnpay/ipn` + service build URL/verify chữ ký
- Gateway — định tuyến endpoint VNPay return/IPN (xem D-15)
- FE — route trang kết quả mới + thêm option VNPay vào checkout selector

</code_context>

<specifics>
## Specific Ideas

- "Phải đi theo kiến trúc của hệ thống có sẵn" — kiến trúc v2 trong `architecture/` và `ba/` là nguồn quyết định bắt buộc; mọi quyết định service ownership / event flow bám sát các doc này, không tự sáng tạo pattern mới.
- IPN là nguồn sự thật duy nhất cho trạng thái thanh toán — return URL chỉ hiển thị.

</specifics>

<deferred>
## Deferred Ideas

- **Inventory reserve/commit/release 2 pha** — arch v2 mô tả (StockReserved/StockReleased/StockCommitted) nhưng vượt phạm vi Phase 26. Hiện dùng giải pháp "hoãn OrderPlaced đến khi PAID" (D-09) để tránh leak kho. Mô hình 2 pha đầy đủ → phase riêng tương lai.
- **Hoàn kho khi đơn VNPAY thất bại sau khi đã trừ** — không phát sinh trong phase này vì D-09 tránh trừ kho trước thanh toán; chỉ cần xét lại nếu chuyển sang trừ-kho-sớm.
- **PaymentExpired / timeout đơn VNPAY bỏ dở** — arch có nhắc; xử lý dọn đơn PENDING quá hạn có thể là cải tiến sau (đơn PENDING chưa trừ kho nên không gây hại tồn kho).

</deferred>

---

*Phase: 26-vnpay-payment-integration*
*Context gathered: 2026-05-22*
