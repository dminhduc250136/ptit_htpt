/**
 * Phase 25 Plan 04 — Regression-guard cho lỗ hổng `orders-cross-user-leak`.
 *
 * Bối cảnh: trước Phase 25, backend tin tuyệt đối header `X-User-Id` do client
 * gửi. Một user đăng nhập có thể giả mạo `X-User-Id` của nạn nhân để xem đơn
 * hàng của người khác. Phase 25 chuyển trust boundary về API Gateway: gateway
 * verify JWT, strip mọi `X-User-Id` client gửi, inject lại từ claim `sub`.
 *
 * Test này gọi THẲNG API Gateway (cổng 8080) bằng APIRequestContext — không qua
 * UI — để có thể chèn header `X-User-Id` giả, điều mà trình duyệt bình thường
 * không cho phép.
 *
 * Yêu cầu môi trường: docker compose up (gateway 8080 + toàn bộ backend).
 * Hai user test được đăng ký mới trong beforeAll nên không phụ thuộc seed.
 */
import { test, expect, type APIRequestContext } from '@playwright/test';

const GATEWAY = process.env.E2E_GATEWAY_URL ?? 'http://localhost:8080';

/** Decode segment payload của JWT, trả claim `sub` (userId). */
function decodeJwtSub(token: string): string {
  const payload = token.split('.')[1];
  const json = Buffer.from(payload, 'base64').toString('utf-8');
  return JSON.parse(json).sub as string;
}

/** Đăng ký user mới qua API, trả { token, userId }. */
async function registerUser(
  request: APIRequestContext,
  username: string,
  email: string,
  password: string,
): Promise<{ token: string; userId: string }> {
  const res = await request.post(`${GATEWAY}/api/users/auth/register`, {
    data: { username, email, password, fullName: username },
  });
  expect(res.ok(), `đăng ký ${email} thất bại: ${res.status()}`).toBeTruthy();
  const body = await res.json();
  const token: string = body.data?.token ?? body.token;
  expect(token, 'response đăng ký phải có token').toBeTruthy();
  return { token, userId: decodeJwtSub(token) };
}

test.describe('Phase 25 — cross-user order leak bị chặn ở gateway', () => {
  let alice: { token: string; userId: string };
  let bob: { token: string; userId: string };

  test.beforeAll(async ({ playwright }) => {
    const request = await playwright.request.newContext();
    const ts = Date.now();
    alice = await registerUser(
      request,
      `leak-alice-${ts}`,
      `leak-alice-${ts}@tmdt.local`,
      'TestPass123',
    );
    bob = await registerUser(
      request,
      `leak-bob-${ts}`,
      `leak-bob-${ts}@tmdt.local`,
      'TestPass123',
    );
    await request.dispose();
  });

  test('Bob giả mạo X-User-Id của Alice → gateway strip, chỉ thấy đơn của Bob', async ({
    request,
  }) => {
    const res = await request.get(`${GATEWAY}/api/orders`, {
      headers: {
        Authorization: `Bearer ${bob.token}`,
        // Header giả mạo — gateway PHẢI strip header này.
        'X-User-Id': alice.userId,
      },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    const orders = body.data?.content ?? body.data ?? [];
    // Mọi đơn trả về phải thuộc Bob, KHÔNG phải Alice.
    for (const order of orders) {
      expect(order.userId).not.toBe(alice.userId);
    }
  });

  test('Endpoint protected không Bearer → 401 AUTH_TOKEN_MISSING', async ({ request }) => {
    const res = await request.get(`${GATEWAY}/api/orders`);
    expect(res.status()).toBe(401);
    const body = await res.json();
    expect(body.code).toBe('AUTH_TOKEN_MISSING');
  });

  test('Endpoint protected với Bearer rác → 401 AUTH_TOKEN_INVALID', async ({ request }) => {
    const res = await request.get(`${GATEWAY}/api/orders`, {
      headers: { Authorization: 'Bearer khong-phai-jwt-hop-le' },
    });
    expect(res.status()).toBe(401);
    const body = await res.json();
    expect(body.code).toBe('AUTH_TOKEN_INVALID');
  });
});
