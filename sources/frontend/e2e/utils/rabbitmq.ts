/**
 * Helper truy vấn RabbitMQ Management HTTP API cho E2E Phase 23 (Message Queue).
 *
 * Triết lý: E2E Phase 23 không có UI mới — RabbitMQ là hạ tầng backend. Để kiểm
 * chứng "topology declared" + "message đã được consume" từ góc nhìn black-box,
 * spec gọi Management HTTP API (port 15672) song song với thao tác UI.
 *
 * Dùng Playwright `APIRequestContext` (fixture `request`) thay vì global `fetch`
 * — `fetch` không có global trên Node 16; `APIRequestContext` là API ổn định
 * của Playwright, không phụ thuộc phiên bản Node.
 *
 * Mọi hàm trả về `null`/`[]` khi không kết nối được broker (broker chưa lên /
 * sai port) → spec dùng test.skip với reason rõ ràng (Strategy A degradation,
 * đồng nhất với helpers.ts). KHÔNG hard-fail vì CI có thể chưa bật RabbitMQ.
 *
 * Credentials mặc định guest/guest (D-01 — dev only). Override qua env nếu khác.
 */
import type { APIRequestContext } from '@playwright/test';

/** Base URL Management API — override qua env cho môi trường khác localhost. */
const MQ_API_BASE = process.env.E2E_RABBITMQ_API ?? 'http://localhost:15672';
const MQ_USER = process.env.E2E_RABBITMQ_USER ?? 'guest';
const MQ_PASS = process.env.E2E_RABBITMQ_PASS ?? 'guest';

/** Header Basic Auth cho Management API. */
function authHeader(): string {
  const token = Buffer.from(`${MQ_USER}:${MQ_PASS}`).toString('base64');
  return `Basic ${token}`;
}

/**
 * GET một path của Management API qua Playwright APIRequestContext.
 * Trả về JSON đã parse, hoặc `null` nếu không kết nối được / status không 2xx
 * (broker down, sai credentials, timeout).
 */
async function mqGet<T = unknown>(
  request: APIRequestContext,
  path: string
): Promise<T | null> {
  try {
    const res = await request.get(`${MQ_API_BASE}/api${path}`, {
      headers: { Authorization: authHeader() },
      // Timeout ngắn — broker hoặc lên hoặc không, không treo test.
      timeout: 4000,
    });
    if (!res.ok()) return null;
    return (await res.json()) as T;
  } catch {
    // Network error / timeout / DNS — broker không khả dụng.
    return null;
  }
}

/** Kiểm tra Management API có sống không (dùng cho test.skip ở đầu spec). */
export async function isBrokerReachable(request: APIRequestContext): Promise<boolean> {
  const overview = await mqGet<{ rabbitmq_version?: string }>(request, '/overview');
  return overview !== null;
}

/** Thông tin 1 exchange. */
export interface MqExchange {
  name: string;
  type: string;
  durable: boolean;
}

/** Lấy danh sách exchange ở vhost mặc định "/". Trả [] nếu broker down. */
export async function listExchanges(request: APIRequestContext): Promise<MqExchange[]> {
  const data = await mqGet<MqExchange[]>(request, '/exchanges/%2F');
  return data ?? [];
}

/** Thông tin 1 queue (chỉ field cần dùng). */
export interface MqQueue {
  name: string;
  durable: boolean;
  /** Số message đang chờ trong queue (chưa được consumer ACK). */
  messages: number;
  /** Số consumer đang gắn vào queue. */
  consumers: number;
  arguments?: Record<string, unknown>;
}

/** Lấy danh sách queue ở vhost mặc định "/". Trả [] nếu broker down. */
export async function listQueues(request: APIRequestContext): Promise<MqQueue[]> {
  const data = await mqGet<MqQueue[]>(request, '/queues/%2F');
  return data ?? [];
}

/** Lấy 1 queue theo tên. Trả null nếu không tồn tại / broker down. */
export async function getQueue(
  request: APIRequestContext,
  name: string
): Promise<MqQueue | null> {
  return mqGet<MqQueue>(request, `/queues/%2F/${encodeURIComponent(name)}`);
}

/**
 * Chờ tới khi queue `name` có `messages <= maxMessages` (mặc định 0 — consumer
 * đã xử lý hết). Poll mỗi 1s, tối đa `timeoutMs`. Trả về số message còn lại
 * lần cuối, hoặc -1 nếu broker không khả dụng.
 *
 * Dùng để xác minh side-effect MQ: sau khi đặt hàng, message OrderPlaced phải
 * được 2 consumer ACK hết (queue về 0) trong vài giây.
 */
export async function waitForQueueDrained(
  request: APIRequestContext,
  name: string,
  maxMessages = 0,
  timeoutMs = 15000
): Promise<number> {
  const deadline = Date.now() + timeoutMs;
  let last = -1;
  while (Date.now() < deadline) {
    const q = await getQueue(request, name);
    if (q === null) return -1; // broker down
    last = q.messages;
    if (last <= maxMessages) return last;
    await new Promise((r) => setTimeout(r, 1000));
  }
  return last;
}

/** Tên topology Phase 23 (D-02, D-09) — dùng làm hằng số tránh gõ sai. */
export const MQ_TOPOLOGY = {
  EXCHANGE: 'order.events',
  DLX: 'order.dlx',
  QUEUE_INVENTORY: 'inventory.order-events',
  QUEUE_NOTIFICATION: 'notification.order-events',
  QUEUE_DLQ: 'order-events.dlq',
} as const;
