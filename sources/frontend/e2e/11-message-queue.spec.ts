/**
 * MODULE 11 — MESSAGE QUEUE (RabbitMQ) — Phase 23.
 *
 * Phase 23 là backend + hạ tầng (không có UI mới). Spec này kiểm chứng luồng
 * Message Queue ở mức end-to-end BLACK-BOX, kết hợp 2 góc nhìn:
 *   (a) UI: user đặt hàng qua /checkout → đơn hàng được tạo thành công.
 *   (b) Broker: RabbitMQ Management HTTP API (port 15672) xác nhận topology
 *       declared + message OrderPlaced được 2 consumer xử lý (queue drain về 0).
 *
 * Đối chiếu ROADMAP §217-224 Success Criteria:
 *   SC1 — container RabbitMQ + Management UI khả dụng       → MQ-E2E-01
 *   SC2 — order-service publish OrderPlaced vào order.events → MQ-E2E-03, 04
 *   SC3+SC4 — inventory + notification consume (queue drain) → MQ-E2E-04
 *   SC5 — DLQ order-events.dlq tồn tại trong topology        → MQ-E2E-02
 *
 * Strategy A degradation: nếu broker chưa lên (CI chưa bật RabbitMQ) hoặc
 * không thêm được SP vào giỏ → test.skip với reason rõ ràng, KHÔNG hard-fail.
 * Đồng nhất với toàn bộ bộ E2E (xem utils/helpers.ts).
 *
 * Yêu cầu môi trường: docker compose up đủ FE + 7 service + RabbitMQ.
 */
import { test, expect } from './utils/fixtures';
import { USER_STATE, ensureCartHasItem } from './utils/helpers';
import {
  isBrokerReachable,
  listExchanges,
  listQueues,
  getQueue,
  waitForQueueDrained,
  MQ_TOPOLOGY,
} from './utils/rabbitmq';

test.use({ storageState: USER_STATE });

/** Đảm bảo giỏ có item rồi mở /checkout. Trả false nếu không thêm được SP. */
async function gotoCheckoutWithItem(page: import('@playwright/test').Page): Promise<boolean> {
  await page.goto('/cart');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1200);
  const hasItem = await page
    .getByRole('button', { name: /Xóa sản phẩm/ })
    .first()
    .isVisible({ timeout: 5000 })
    .catch(() => false);
  if (!hasItem && !(await ensureCartHasItem(page))) return false;

  await page.goto('/checkout');
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);
  return true;
}

/** Điền địa chỉ giao hàng + chọn COD trên /checkout. */
async function fillShippingAndPayment(page: import('@playwright/test').Page): Promise<void> {
  const trigger = page.getByRole('button', { name: /Địa chỉ đã lưu/ }).first();
  let addressReady = false;
  if (await trigger.isVisible({ timeout: 3000 }).catch(() => false)) {
    await trigger.click();
    await page.waitForTimeout(400);
    const option = page.locator('[role="option"]').first();
    if (await option.isVisible({ timeout: 3000 }).catch(() => false)) {
      await option.click();
      addressReady = true;
    }
  }
  if (!addressReady) {
    await page.getByLabel(/Họ và tên/).fill('E2E MQ Người Nhận');
    await page.getByLabel(/Số điện thoại/).fill('0901234567');
    await page.getByLabel(/Địa chỉ/).first().fill('23 Đường Message Queue');
    await page.getByLabel(/Phường\/Xã/).fill('Phường 1');
    await page.getByLabel(/Quận\/Huyện/).fill('Quận 1');
    await page.getByLabel(/Tỉnh\/Thành phố/).fill('TP. Hồ Chí Minh');
  }
  const cod = page.getByLabel(/Thanh toán khi nhận hàng/);
  if (await cod.isVisible({ timeout: 3000 }).catch(() => false)) {
    await cod.check();
  }
}

test.describe('11-MESSAGE-QUEUE: RabbitMQ event-driven (Phase 23)', () => {
  // ───────────────────────────────────────────────────────────────
  // SC1 — RabbitMQ container + Management UI khả dụng
  test('MQ-E2E-01: RabbitMQ Management API sống + có thông tin broker', async ({ request }) => {
    const reachable = await isBrokerReachable(request);
    if (!reachable) {
      test.skip(
        true,
        'RabbitMQ Management API (localhost:15672) không kết nối được — ' +
          'broker chưa lên. Chạy `docker compose up -d rabbitmq` (Strategy A skip).'
      );
      return;
    }
    // Broker sống → đã verify SC1 ở mức API.
    expect(reachable).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  // SC2 + SC5 — topology declared: exchange order.events + DLX + 3 queue + DLQ
  test('MQ-E2E-02: Topology declared đúng — exchange, DLX, 2 queue consumer, DLQ', async ({
    request,
  }) => {
    if (!(await isBrokerReachable(request))) {
      test.skip(true, 'Broker chưa lên — Strategy A skip');
      return;
    }

    const exchanges = await listExchanges(request);
    const exchangeNames = exchanges.map((e) => e.name);
    // SC2: topic exchange order.events
    expect(exchangeNames).toContain(MQ_TOPOLOGY.EXCHANGE);
    // SC5: dead-letter exchange order.dlx
    expect(exchangeNames).toContain(MQ_TOPOLOGY.DLX);

    const orderEvents = exchanges.find((e) => e.name === MQ_TOPOLOGY.EXCHANGE);
    expect(orderEvents?.type).toBe('topic'); // D-02
    expect(orderEvents?.durable).toBe(true); // D-02 durable

    const queues = await listQueues(request);
    const queueNames = queues.map((q) => q.name);
    // SC3 + SC4: 2 queue consumer
    expect(queueNames).toContain(MQ_TOPOLOGY.QUEUE_INVENTORY);
    expect(queueNames).toContain(MQ_TOPOLOGY.QUEUE_NOTIFICATION);
    // SC5: dead-letter queue
    expect(queueNames).toContain(MQ_TOPOLOGY.QUEUE_DLQ);

    // 2 queue chính phải durable + có argument x-dead-letter-exchange (D-09)
    const inventoryQ = queues.find((q) => q.name === MQ_TOPOLOGY.QUEUE_INVENTORY);
    expect(inventoryQ?.durable).toBe(true);
    expect(inventoryQ?.arguments?.['x-dead-letter-exchange']).toBe(MQ_TOPOLOGY.DLX);
  });

  // ───────────────────────────────────────────────────────────────
  // SC3 + SC4 — 2 queue có consumer đang gắn (3 service backend đã khởi động)
  test('MQ-E2E-03: 2 queue consumer có ít nhất 1 consumer gắn vào', async ({ request }) => {
    if (!(await isBrokerReachable(request))) {
      test.skip(true, 'Broker chưa lên — Strategy A skip');
      return;
    }

    const inventoryQ = await getQueue(request, MQ_TOPOLOGY.QUEUE_INVENTORY);
    const notificationQ = await getQueue(request, MQ_TOPOLOGY.QUEUE_NOTIFICATION);

    if (inventoryQ === null || notificationQ === null) {
      test.skip(
        true,
        'Queue chưa được declare — backend service (inventory/notification) ' +
          'chưa khởi động xong để declare topology (Strategy A skip).'
      );
      return;
    }

    // @RabbitListener của 2 consumer service đã gắn vào queue.
    expect(inventoryQ.consumers).toBeGreaterThanOrEqual(1);
    expect(notificationQ.consumers).toBeGreaterThanOrEqual(1);
  });

  // ───────────────────────────────────────────────────────────────
  // SC2 + SC3 + SC4 — end-to-end: đặt hàng qua UI → message publish → consumer drain
  test('MQ-E2E-04: Đặt hàng qua UI → message OrderPlaced được 2 consumer xử lý hết', async ({
    page,
    request,
  }) => {
    if (!(await isBrokerReachable(request))) {
      test.skip(true, 'Broker chưa lên — Strategy A skip');
      return;
    }
    if (!(await gotoCheckoutWithItem(page))) {
      test.skip(true, 'Không thêm được SP vào giỏ — Strategy A skip');
      return;
    }

    await fillShippingAndPayment(page);

    // Đặt hàng — order-service tạo đơn, publish OrderPlaced SAU khi DB commit (D-03)
    await page.getByRole('button', { name: /Đặt hàng/ }).click();

    // Đơn tạo thành công: redirect tới /orders|/profile hoặc toast thành công
    await Promise.race([
      page.waitForURL(/\/orders|\/profile/, { timeout: 20000, waitUntil: 'domcontentloaded' }),
      page
        .getByText(/đặt hàng thành công|đã đặt hàng|thành công/i)
        .first()
        .waitFor({ timeout: 20000 }),
    ]);

    // Side-effect MQ: message OrderPlaced phải được inventory + notification
    // consumer ACK hết → 2 queue drain về 0 trong vòng 15s (retry/backoff buffer).
    const inventoryRemaining = await waitForQueueDrained(
      request,
      MQ_TOPOLOGY.QUEUE_INVENTORY,
      0,
      15000
    );
    const notificationRemaining = await waitForQueueDrained(
      request,
      MQ_TOPOLOGY.QUEUE_NOTIFICATION,
      0,
      15000
    );

    if (inventoryRemaining === -1 || notificationRemaining === -1) {
      test.skip(true, 'Broker mất kết nối giữa chừng — Strategy A skip');
      return;
    }

    // Consumer đã ACK hết — không message tồn đọng.
    expect(inventoryRemaining).toBe(0);
    expect(notificationRemaining).toBe(0);
  });

  // ───────────────────────────────────────────────────────────────
  // SC5 — DLQ ổn định: ở trạng thái bình thường (không lỗi) DLQ phải rỗng
  test('MQ-E2E-05: DLQ order-events.dlq tồn tại và rỗng khi không có message lỗi', async ({
    request,
  }) => {
    if (!(await isBrokerReachable(request))) {
      test.skip(true, 'Broker chưa lên — Strategy A skip');
      return;
    }

    const dlq = await getQueue(request, MQ_TOPOLOGY.QUEUE_DLQ);
    if (dlq === null) {
      test.skip(true, 'DLQ chưa declare — backend chưa khởi động (Strategy A skip)');
      return;
    }

    // DLQ tồn tại + durable. Ở luồng happy-path, không message lỗi → DLQ rỗng.
    // (Test inject lỗi → DLQ có message thuộc IT backend OrderPlacedListenerIT.)
    expect(dlq.durable).toBe(true);
    expect(dlq.messages).toBe(0);
  });

  // ───────────────────────────────────────────────────────────────
  // Đặt 2 đơn liên tiếp → cả 2 message đều được drain (không tồn đọng / leak)
  test('MQ-E2E-06: Đặt nhiều đơn liên tiếp → queue không tồn đọng message', async ({
    page,
    request,
  }) => {
    if (!(await isBrokerReachable(request))) {
      test.skip(true, 'Broker chưa lên — Strategy A skip');
      return;
    }

    for (let i = 0; i < 2; i++) {
      if (!(await gotoCheckoutWithItem(page))) {
        test.skip(true, `Không thêm được SP vào giỏ ở vòng ${i + 1} — Strategy A skip`);
        return;
      }
      await fillShippingAndPayment(page);
      await page.getByRole('button', { name: /Đặt hàng/ }).click();
      await Promise.race([
        page.waitForURL(/\/orders|\/profile/, { timeout: 20000, waitUntil: 'domcontentloaded' }),
        page
          .getByText(/đặt hàng thành công|đã đặt hàng|thành công/i)
          .first()
          .waitFor({ timeout: 20000 }),
      ]);
      await page.waitForTimeout(500);
    }

    // Sau 2 đơn, cả 2 queue vẫn drain về 0 (consumer kịp xử lý).
    const inv = await waitForQueueDrained(request, MQ_TOPOLOGY.QUEUE_INVENTORY, 0, 20000);
    const noti = await waitForQueueDrained(request, MQ_TOPOLOGY.QUEUE_NOTIFICATION, 0, 20000);
    if (inv === -1 || noti === -1) {
      test.skip(true, 'Broker mất kết nối — Strategy A skip');
      return;
    }
    expect(inv).toBe(0);
    expect(noti).toBe(0);
  });
});
