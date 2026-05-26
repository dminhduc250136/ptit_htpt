---
phase: 26-vnpay-payment-integration
plan: "04"
subsystem: frontend
tags: [vnpay, frontend, checkout, payment-result, polling, order-display, playwright]
dependency_graph:
  requires:
    - "26-01: GET /api/payments/vnpay/return endpoint public (gateway whitelist)"
    - "26-03: OrderDto.paymentUrl populated for VNPAY; Order type paymentStatus/vnpTransactionNo"
  provides:
    - "frontend/checkout/page.tsx: selector VNPAY + redirect sang paymentUrl (D-14)"
    - "frontend/checkout/result/page.tsx: trang kết quả 5 trạng thái + poll 3s×5 (D-12, D-13)"
    - "frontend/services/payments.ts: getVNPayReturn(searchParams) public endpoint caller"
    - "frontend/orderLabels.ts: paymentMethodMap entry VNPAY"
    - "frontend/profile/orders/[id]: hiển thị mã giao dịch VNPay"
    - "frontend/admin/orders/[id]: hiển thị payment status badge + mã giao dịch VNPay"
  affects:
    - "PAY-01, PAY-02, PAY-04 — hoàn tất frontend của Phase 26"
tech-stack:
  added: []
  patterns:
    - "getVNPayReturn: httpGet public (không cần JWT) forward toàn bộ query string VNPay"
    - "checkout/result: useSearchParams + useEffect polling setInterval 3s×5 + cleanup"
    - "T-26-13 mitigate: vnp_ResponseCode CHỈ render sơ bộ; nguồn sự thật là poll payment_status"
    - "Suspense wrapper cho useSearchParams (pattern checkout/page.tsx)"
key-files:
  created:
    - "sources/frontend/src/app/checkout/result/page.tsx"
    - "sources/frontend/e2e/12-vnpay-payment.spec.ts"
  modified:
    - "sources/frontend/src/types/index.ts"
    - "sources/frontend/src/lib/orderLabels.ts"
    - "sources/frontend/src/app/checkout/page.tsx"
    - "sources/frontend/src/services/payments.ts"
    - "sources/frontend/src/app/profile/orders/[id]/page.tsx"
    - "sources/frontend/src/app/admin/orders/[id]/page.tsx"
key-decisions:
  - "paymentStatus widened từ 'PENDING'|'PAID'|'FAILED'|'REFUNDED' → string (loại bỏ duplicate field declaration)"
  - "Playwright spec đặt trong e2e/ (testDir config) thay vì tests/ trong plan template"
  - "3 Playwright tests cho result page dùng page.route() mock (không cần backend thật)"
  - "Inline spinner + keyframe CSS (không tạo CSS module mới — UI-SPEC §Component reuse)"
  - "getVNPayReturn forward window.location.search đúng khi mount (toàn bộ params VNPay)"
requirements-completed: [PAY-01, PAY-02, PAY-04]
duration: ~15min
completed: 2026-05-22
---

# Phase 26 Plan 04: Frontend VNPay — checkout selector + result page + order display

**Selector VNPAY tại /checkout redirect sang paymentUrl; /checkout/result poll payment_status 3s×5 với 5 trạng thái UI-SPEC verbatim; order display hiển thị mã giao dịch VNPay.**

## Hiệu suất

- **Duration:** ~15 phút
- **Completed:** 2026-05-22
- **Tasks:** 2/2
- **Files tạo mới:** 2 (checkout/result/page.tsx, 12-vnpay-payment.spec.ts)
- **Files sửa:** 6

## Thành tựu

### Task 1: Type Order + orderLabels + checkout selector + redirect

- `types/index.ts` — interface `Order`: thêm `paymentStatus?: string` (widened, loại bỏ duplicate), `vnpTransactionNo?`, `paymentUrl?`; `CreateOrderRequest.paymentMethod` thêm `'VNPAY'`
- `orderLabels.ts` — `paymentMethodMap`: thêm `VNPAY: 'Thanh toán qua VNPay'`
- `checkout/page.tsx`:
  - Type union `paymentMethod` thêm `'VNPAY'`
  - Mảng payment options thêm entry `{ value: 'VNPAY', label: 'Thanh toán qua VNPay', icon: '💳' }`
  - `submitOrder`: sau cart cleanup, nếu `VNPAY && order.paymentUrl` → `showToast('Đang chuyển tới cổng thanh toán VNPay...', 'success')` + `window.location.assign(order.paymentUrl)` (D-14)

### Task 2: /checkout/result + polling + order display

- `services/payments.ts` — thêm `getVNPayReturn(searchParams)` → `GET /api/payments/vnpay/return` (public, không JWT)
- `checkout/result/page.tsx` — trang mới:
  - Bọc `<Suspense>` vì dùng `useSearchParams`
  - Resolve orderId qua `getVNPayReturn` (forward toàn bộ `window.location.search`)
  - `empty` state khi thiếu `vnp_TxnRef` hoặc `valid=false`
  - Render sơ bộ từ `vnp_ResponseCode` (T-26-13: chỉ hiển thị, KHÔNG update DB)
  - Poll `getOrderById` mỗi 3000ms tối đa 5 lần, dừng sớm khi `paymentStatus !== 'PENDING'`
  - 5 copy string verbatim UI-SPEC: đang xác nhận / thành công / thất bại / huỷ / timeout
  - Components tái dụng: `Button` (primary/secondary), `RetrySection` (lỗi mạng), inline `Spinner`
- `profile/orders/[id]/page.tsx` — thêm `Mã giao dịch VNPay:` (ẩn nếu rỗng)
- `admin/orders/[id]/page.tsx` — thêm `Badge` payment status (`sale`/`out-of-stock`/`default`) + mã GD VNPay (ẩn nếu rỗng)
- `e2e/12-vnpay-payment.spec.ts` — 4 smoke tests: checkout selector + result page 3 states (với page.route mock)

## Task Commits

1. **Task 1: Order type + orderLabels + checkout selector + redirect** — `bdcd43d`
2. **Task 2: /checkout/result + polling + order display + Playwright spec** — `dde7f6b`

## Verification

- `npx tsc --noEmit` — PASS (không có lỗi mới trong các file phase 26)
- `npx playwright test 12-vnpay-payment --list` — PASS (4 tests listed)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Duplicate `paymentStatus` declaration trong interface Order**
- **Found during:** Task 1
- **Issue:** `interface Order` đã có `paymentStatus?: 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED'` (từ phase trước). Thêm `paymentStatus: string` theo plan gây TS2300 duplicate identifier.
- **Fix:** Merge thành 1 declaration duy nhất `paymentStatus?: string` (widened — backward compat với code dùng paymentStatusMap lookup), loại bỏ duplicate
- **Files modified:** `types/index.ts`
- **Commit:** `bdcd43d`

**2. [Rule 1 - Config] Playwright spec cần đặt trong `e2e/` thay vì `tests/`**
- **Found during:** Task 2
- **Issue:** `playwright.config.ts` có `testDir: './e2e'` — spec trong `tests/` không được pick up
- **Fix:** Tạo spec tại `e2e/12-vnpay-payment.spec.ts` (testDir thực tế)
- **Files modified:** `e2e/12-vnpay-payment.spec.ts`
- **Commit:** `dde7f6b`

## Known Stubs

Không có stubs. `getVNPayReturn` gọi HTTP thật. `checkout/result/page.tsx` wired đầy đủ:
- Resolve orderId thật qua payment-service return endpoint
- Poll getOrderById thật
- 5 copy string verbatim UI-SPEC

Playwright spec E2E runtime defer cho /gsd-verify-work khi docker + VNPay sandbox sẵn sàng (precedent Phase 19/23 — spec đã list 4 tests tĩnh với `--list`).

## Threat Flags

T-26-13 (Spoofing/Tampering) đã được mitigate đúng:
- `vnp_ResponseCode` chỉ dùng cho render sơ bộ (initial `setState`)
- Nguồn sự thật là `payment_status` poll qua `GET /api/orders/{id}` — user không thể fake
- Comment rõ ràng trong code: `// T-26-13 mitigate`

Không có threat surface mới ngoài threat model đã có.

## Self-Check: PASSED

Files created:
- `sources/frontend/src/app/checkout/result/page.tsx`: EXISTS
- `sources/frontend/e2e/12-vnpay-payment.spec.ts`: EXISTS

Commits verified:
- `bdcd43d`: EXISTS
- `dde7f6b`: EXISTS

Acceptance criteria:
- `grep "vnp_ResponseCode" checkout/result/page.tsx`: PASS
- `grep "Suspense" checkout/result/page.tsx`: PASS
- `grep "3000" checkout/result/page.tsx`: PASS (POLL_INTERVAL_MS = 3000)
- `grep "POLL_MAX_ATTEMPTS = 5" checkout/result/page.tsx`: PASS
- `grep "Đang xác nhận thanh toán" checkout/result/page.tsx`: PASS
- `grep "getOrderById" checkout/result/page.tsx`: PASS
- `grep "getVNPayReturn" checkout/result/page.tsx`: PASS
- `grep "vnpay/return" services/payments.ts`: PASS
- `grep "vnpTransactionNo" profile/orders/[id]/page.tsx`: PASS
- `grep "vnpTransactionNo" admin/orders/[id]/page.tsx`: PASS
- `npx tsc --noEmit` clean (no new errors): PASS
- `npx playwright test 12-vnpay-payment --list` 4 tests: PASS
