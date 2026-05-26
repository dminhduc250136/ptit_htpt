/**
 * MODULE 12 — THANH TOÁN MOMO (Phase 26.1 / PAY-01, PAY-02).
 *
 * Smoke spec: kiểm tra static UI/routing (KHÔNG chạy full payment flow với cổng thật).
 * Runtime execution defer cho /gsd-verify-work khi docker + MoMo sandbox ready
 * (precedent Phase 19/23/26 — Maven/Docker defer trên env Windows này).
 *
 * Test 1: Checkout có option "Thanh toán qua MoMo" (PAY-01).
 * Test 2: /checkout/result?resultCode=0&orderId=... render heading "Đang xác nhận thanh toán"
 *          — mock getMomoReturn trả orderId hợp lệ (PAY-02).
 * Test 3: /checkout/result?resultCode=1006&orderId=... render "Đã huỷ thanh toán".
 * Test 4: /checkout/result khi getMomoReturn trả valid=false → empty state.
 *
 * NOTE: Dùng page.route() để mock /api/payments/momo/return và /api/orders/{id}
 * (tránh cần backend thật chạy). Test 1 không cần mock.
 */

import { test, expect } from '@playwright/test';
import { USER_STATE } from './utils/helpers';

// ============================================================
// Test 1: Checkout selector có option MoMo
// ============================================================

test.describe('12-MOMO: Checkout selector', () => {
  test.use({ storageState: USER_STATE });

  test('có option "Thanh toán qua MoMo" trong checkout', async ({ page }) => {
    // Đi thẳng tới /checkout (không cần giỏ hàng thật — chỉ kiểm tra UI selector)
    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(800);

    // Kiểm tra option MoMo xuất hiện
    await expect(
      page.getByText('Thanh toán qua MoMo'),
      'Option MoMo phải xuất hiện trong checkout selector'
    ).toBeVisible({ timeout: 5000 });

    // Kiểm tra có thể select MoMo
    const momoRadio = page.locator('input[type="radio"][value="MOMO"]');
    if (await momoRadio.isVisible({ timeout: 3000 }).catch(() => false)) {
      await momoRadio.click();
      await expect(momoRadio).toBeChecked();
    }
  });
});

// ============================================================
// Test 2: /checkout/result hiển thị đúng heading "Đang xác nhận thanh toán"
// ============================================================

const MOCK_ORDER_ID = 'test-order-id-0001';
const MOCK_SESSION_ID = 'sess-456-momo';
const MOCK_TRANS_ID = '2024123456789';

test.describe('12-MOMO: Trang kết quả', () => {
  test('render heading "Đang xác nhận thanh toán" khi resultCode=0', async ({ page }) => {
    // Mock endpoint getMomoReturn trả orderId nội bộ hợp lệ
    await page.route('**/api/payments/momo/return**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            valid: true,
            orderId: MOCK_ORDER_ID,
            resultCode: '0',
            paymentTransactionNo: MOCK_TRANS_ID,
          },
        }),
      });
    });

    // Mock getOrderById để trả PENDING (kéo dài poll)
    await page.route(`**/api/orders/${MOCK_ORDER_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: MOCK_ORDER_ID,
            userId: 'user-001',
            items: [],
            shippingAddress: { street: '', ward: '', district: '', city: '' },
            paymentMethod: 'MOMO',
            paymentStatus: 'PENDING',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    });

    // Điều hướng tới /checkout/result với params MoMo
    await page.goto(
      `/checkout/result?resultCode=0&orderId=${MOCK_SESSION_ID}&transId=${MOCK_TRANS_ID}&amount=100000`
    );
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000); // Đợi resolve + initial render

    // Heading "Đang xác nhận thanh toán" phải xuất hiện (state = polling)
    await expect(
      page.getByRole('heading', { name: 'Đang xác nhận thanh toán' }),
      'Heading "Đang xác nhận thanh toán" phải hiển thị khi resultCode=0'
    ).toBeVisible({ timeout: 8000 });
  });

  test('render heading "Đã huỷ thanh toán" khi resultCode=1006', async ({ page }) => {
    // Mock return endpoint trả cancelled
    await page.route('**/api/payments/momo/return**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            valid: true,
            orderId: MOCK_ORDER_ID,
            resultCode: '1006',
            paymentTransactionNo: null,
          },
        }),
      });
    });

    await page.route(`**/api/orders/${MOCK_ORDER_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            id: MOCK_ORDER_ID,
            userId: 'user-001',
            items: [],
            shippingAddress: { street: '', ward: '', district: '', city: '' },
            paymentMethod: 'MOMO',
            paymentStatus: 'FAILED',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    });

    await page.goto(
      `/checkout/result?resultCode=1006&orderId=${MOCK_SESSION_ID}`
    );
    await page.waitForLoadState('domcontentloaded');

    // Đợi state resolve xong (cancelled không cần poll timeout)
    await expect(
      page.getByRole('heading', { name: 'Đã huỷ thanh toán' }),
      'Heading "Đã huỷ thanh toán" phải hiển thị khi resultCode=1006'
    ).toBeVisible({ timeout: 8000 });
  });

  test('render empty state khi getMomoReturn trả valid=false', async ({ page }) => {
    // Mock return endpoint trả invalid
    await page.route('**/api/payments/momo/return**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            valid: false,
            orderId: null,
            resultCode: '99',
            paymentTransactionNo: null,
          },
        }),
      });
    });

    await page.goto('/checkout/result?resultCode=99&orderId=invalid-session');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(500);

    await expect(
      page.getByRole('heading', { name: 'Không tìm thấy thông tin thanh toán' }),
      'Heading empty state phải xuất hiện khi getMomoReturn trả valid=false'
    ).toBeVisible({ timeout: 5000 });
  });

  test('render empty state khi thiếu orderId param', async ({ page }) => {
    await page.goto('/checkout/result?resultCode=0');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(500);

    await expect(
      page.getByRole('heading', { name: 'Không tìm thấy thông tin thanh toán' }),
      'Heading empty state phải xuất hiện khi thiếu orderId'
    ).toBeVisible({ timeout: 5000 });
  });
});
