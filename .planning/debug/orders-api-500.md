---
slug: orders-api-500
status: root_cause_found
trigger: |
  DATA_START
  Phần "Đơn hàng của tôi", đặt đơn hàng mới, admin xem đơn hàng — tất cả 500.
  F12 trên /orders trả:
  {
    "timestamp": "2026-05-02T07:43:19.584725933Z",
    "status": 500,
    "error": "Internal Server Error",
    "message": "Internal server error",
    "code": "INTERNAL_ERROR",
    "path": "/orders",
    "traceId": "2d461e3a-9d3a-4809-b835-f4ee04a81aa7",
    "fieldErrors": []
  }
  DATA_END
created: 2026-05-02
updated: 2026-05-02
---

# Debug: orders-api-500

## Symptoms
- Expected: 3 flow (xem orders của user, tạo order, admin xem) trả 200 với data.
- Actual: tất cả trả 500 INTERNAL_ERROR (generic mask).
- Timeline: chưa bao giờ chạy được E2E trên branch `develop`.
- Path: `/orders` (qua api-gateway → order-service:8083).

## Current Focus
- hypothesis: 2-layer
  1. PROXIMATE — Container `order-service` đang chạy code cũ (pre-fix). Source trên disk có fix uncommitted nhưng image Docker chưa được rebuild → JAR đang chạy KHÔNG có fix.
  2. ROOT BUG (đã được fix locally nhưng chưa deploy) — `OrderEntity.items` là `FetchType.LAZY`, `application.yml` set `spring.jpa.open-in-view: false`. `OrderCrudService.listOrders` (admin path) gọi `orderRepository.findAll()` → entity returned detached → `OrderMapper.toDto()` iterate `e.items()` ngoài transaction → **LazyInitializationException** → fallback handler mask thành 500.
- next_action: User rebuild image + restart order-service; nếu vẫn 500 cho path "đơn hàng của tôi" hoặc "tạo order" → đọc stack trace cụ thể.

## Evidence

- timestamp: 2026-05-02T08:00Z
  finding: |
    Working tree có 2 file modified uncommitted (git status):
      M sources/backend/order-service/.../repository/OrderRepository.java
      M sources/backend/order-service/.../service/OrderCrudService.java
    Diff cho thấy đây là **fix cho chính bug này** — comment ghi rõ:
      "Bug fix (orders-api-500): findAll() trả OrderEntity với items LAZY → khi DTO mapper
       iterate items ngoài transaction (open-in-view=false, service không có @Transactional)
       → ném LazyInitializationException → 500 INTERNAL_ERROR"
    Fix thêm `findAllWithItems()` / `findByIdWithItems()` với `LEFT JOIN FETCH o.items`
    và `@Transactional(readOnly=true)` trên các service method.

- timestamp: 2026-05-02T08:00Z
  finding: |
    Verified bug pre-conditions vẫn hợp lệ:
      - OrderEntity.java:44 → `@OneToMany(... fetch = FetchType.LAZY)` private List<OrderItemEntity> items
      - application.yml:16 → `spring.jpa.open-in-view: false`
      - OrderMapper.toDto() iterate `e.items().stream()…` (line 16)
    → nếu `findAll()` được gọi (không JOIN FETCH) → 100% LazyInitializationException khi map.

- timestamp: 2026-05-02T08:00Z
  finding: |
    Dockerfile order-service (multi-stage) COPY src + mvn package tại image-build time.
    KHÔNG có hot-reload / volume mount source vào container.
    → Sửa source xong CHƯA tự áp dụng vào container đang chạy. Phải `docker compose build order-service`
    rồi `docker compose up -d order-service` (hoặc 1 lệnh: `docker compose up -d --build order-service`).

- timestamp: 2026-05-02T08:00Z
  finding: |
    GlobalExceptionHandler.java đã có logger (line 23) và log.error trong handleFallback (line 127):
      log.error("Unhandled exception at {}", request.getRequestURI(), ex);
    → Stack trace ĐANG được ghi ra docker logs order-service. User có thể grep traceId 2d461e3a hoặc
    tìm "Unhandled exception" để confirm exception type.

- timestamp: 2026-05-02T08:00Z
  finding: |
    Path "/orders" + X-User-Id (đơn hàng của tôi) thực ra dùng `listMyOrders` →
    `findByUserIdWithFilters` đã CÓ `LEFT JOIN FETCH o.items` từ Phase 11.
    Lý thuyết path này KHÔNG bị LazyInit ngay cả ở code cũ. Vì sao user thấy 500 ở đây cũng là
    dấu hỏi — có thể do JPQL `cast(:status as string)` / null-binding gặp lỗi runtime với Hibernate 6,
    hoặc service đang fail khởi động vì lỗi khác. Cần đọc log để xác nhận.

## Eliminated
(empty — chưa eliminate giả thuyết nào đến mức cao về path "đơn hàng của tôi" / "tạo order")

## Resolution

- root_cause: |
    Container `order-service` đang serve code cũ (pre-fix). Source code đã có fix
    `LazyInitializationException` cho path admin (`listOrders` / `getOrder`) nhưng chưa rebuild image.
    Path "đơn hàng của tôi" và "tạo order" có thể có lỗi riêng — cần đọc log để xác nhận sau khi
    rebuild xử lý xong path admin.

- fix: |
    Step 1 (bắt buộc): Rebuild + restart order-service để code đang sửa thực sự được chạy.
      docker compose up -d --build order-service

    Step 2: Test lại 3 flow và quan sát.
      - Admin xem orders + xem chi tiết order: nếu vẫn 500 → đọc log.
      - Đơn hàng của tôi (/orders với X-User-Id): nếu vẫn 500 → đọc log:
          docker compose logs order-service --tail=300 | grep -A 40 "Unhandled exception"
        Paste stack trace → tôi diagnose tiếp.
      - Tạo order: nếu vẫn 500 → tương tự đọc log.

    Step 3: Sau khi xác nhận tất cả OK → commit fix:
      git add sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
      git add sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
      git commit -m "fix(orders-api-500): JOIN FETCH items + @Transactional cho list/get/create order"

- verification: |
    Sau rebuild:
      curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8083/orders
      → 200 (admin path, no header)
      curl -s -o /dev/null -w "%{http_code}\n" -H "X-User-Id: <uid>" http://localhost:8083/orders
      → 200 (my-orders path)
    Nếu cả 3 flow trên FE load được data → done.

- files_changed: |
    sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
    sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/service/OrderCrudService.java
    (đã có fix sẵn trong working tree, chưa commit)
