/**
 * MODULE 12 — THANH TOÁN VNPAY (Phase 26 / PAY-01, PAY-02).
 *
 * Smoke spec: kiểm tra static UI/routing (KHÔNG chạy full payment flow với cổng thật).
 * Runtime execution defer cho /gsd-verify-work khi docker + VNPay sandbox ready
 * (precedent Phase 19/23 — Maven/Docker defer trên env Windows này).
 *
 * Test 1: Checkout có option "Thanh toán qua VNPay" (PAY-01).
 * Test 2: /checkout/result?vnp_ResponseCode=00&vnp_TxnRef=... render heading "Đang xác nhận
 *          thanh toán" — mock getVNPayReturn trả orderId hợp lệ (PAY-02).
 *
 * NOTE: Test 2 dùng page.route() để mock /api/payments/vnpay/return và /api/orders/{id}
 * (tránh cần backend thật chạy). Test 1 không cần mock.
 */

import { test, expect } from '@playwright/test';
import { USER_STATE } from './utils/helpers';

// ============================================================
// Test 1: Checkout selector có option VNPay
// ============================================================

test.describe('12-VNPAY: Checkout selector', () => {
  test.use({ storageState: USER_STATE });

  test('có option "Thanh toán qua VNPay" trong checkout', async ({ page }) => {
    // Đi thẳng tới /checkout (không cần giỏ hàng thật — chỉ kiểm tra UI selector)
    await page.goto('/checkout');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(800);

    // Kiểm tra option VNPay xuất hiện
    await expect(
      page.getByText('Thanh toán qua VNPay'),
      'Option VNPay phải xuất hiện trong checkout selector'
    ).toBeVisible({ timeout: 5000 });

    // Kiểm tra có thể select VNPay
    const vnpayRadio = page.locator('input[type="radio"][value="VNPAY"]');
    if (await vnpayRadio.isVisible({ timeout: 3000 }).catch(() => false)) {
      await vnpayRadio.click();
      await expect(vnpayRadio).toBeChecked();
    }
  });
});

// ============================================================
// Test 2: /checkout/result hiển thị đúng heading "Đang xác nhận thanh toán"
// ============================================================

const MOCK_ORDER_ID = 'test-order-id-0001';
const MOCK_TXN_REF = 'TXN123456789';

test.describe('12-VNPAY: Trang kết quả', () => {
  test('render heading "Đang xác nhận thanh toán" khi vnp_ResponseCode=00', async ({ page }) => {
    // Mock endpoint getVNPayReturn trả orderId hợp lệ
    await page.route('**/api/payments/vnpay/return**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            valid: true,
            orderId: MOCK_ORDER_ID,
            responseCode: '00',
            vnpTransactionNo: 'VNP123456',
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
            paymentMethod: 'VNPAY',
            paymentStatus: 'PENDING',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    });

    // Điều hướng tới /checkout/result với params VNPay
    await page.goto(
      `/checkout/result?vnp_ResponseCode=00&vnp_TxnRef=${MOCK_TXN_REF}&vnp_Amount=100000&vnp_OrderInfo=test`
    );
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1000); // Đợi resolve + initial render

    // Heading "Đang xác nhận thanh toán" phải xuất hiện (state = polling)
    await expect(
      page.getByRole('heading', { name: 'Đang xác nhận thanh toán' }),
      'Heading "Đang xác nhận thanh toán" phải hiển thị khi vnp_ResponseCode=00'
    ).toBeVisible({ timeout: 8000 });
  });

  test('render heading "Đã huỷ thanh toán" khi vnp_ResponseCode=24', async ({ page }) => {
    // Mock return endpoint trả cancelled
    await page.route('**/api/payments/vnpay/return**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          data: {
            valid: true,
            orderId: MOCK_ORDER_ID,
            responseCode: '24',
            vnpTransactionNo: null,
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
            paymentMethod: 'VNPAY',
            paymentStatus: 'PENDING',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        }),
      });
    });

    await page.goto(
      `/checkout/result?vnp_ResponseCode=24&vnp_TxnRef=${MOCK_TXN_REF}`
    );
    await page.waitForLoadState('domcontentloaded');

    // Đợi state resolve xong (cancelled không cần poll timeout)
    await expect(
      page.getByRole('heading', { name: 'Đã huỷ thanh toán' }),
      'Heading "Đã huỷ thanh toán" phải hiển thị khi vnp_ResponseCode=24'
    ).toBeVisible({ timeout: 8000 });
  });

  test('render empty state khi thiếu vnp_TxnRef', async ({ page }) => {
    await page.goto('/checkout/result?vnp_ResponseCode=00');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(500);

    await expect(
      page.getByRole('heading', { name: 'Không tìm thấy thông tin thanh toán' }),
      'Heading empty state phải xuất hiện khi thiếu vnp_TxnRef'
    ).toBeVisible({ timeout: 5000 });
  });
});
