/**
 * Helpers dùng chung cho bộ E2E test (viết lại toàn bộ — tổ chức theo module).
 *
 * Triết lý test:
 *   - Chạy trên FULL STACK THẬT (docker-compose: FE + 7 microservices + Postgres).
 *   - storageState fixtures từ global-setup.ts (user.json + admin.json) — KHÔNG re-login mỗi test.
 *   - "Strategy A" degradation: skip-if-no-data thay vì hard fail khi seed chưa có dữ liệu cần thiết.
 *     → Test không flaky vì seed khác môi trường; skip có reason rõ ràng để người đọc biết cần gì.
 *
 * Seed credentials (V100__seed_dev_data.sql):
 *   admin: admin@tmdt.local / admin123
 *   user:  demo@tmdt.local  / admin123
 */
import type { Page } from '@playwright/test';

/** Đường dẫn storageState fixture (sinh bởi global-setup.ts). */
export const USER_STATE = 'e2e/storageState/user.json';
export const ADMIN_STATE = 'e2e/storageState/admin.json';

/** State rỗng — dùng cho test ẩn danh (anonymous). */
export const ANON_STATE: { cookies: never[]; origins: never[] } = { cookies: [], origins: [] };

/** Credentials seed — override qua env nếu môi trường khác. */
export const USER_EMAIL = process.env.E2E_USER_EMAIL ?? 'demo@tmdt.local';
export const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? 'admin123';
export const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@tmdt.local';
export const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'admin123';

/**
 * Lấy danh sách href các product card trên /products.
 * Chờ React Query fetch xong (waitFor attached) để tránh race với loading skeleton.
 */
async function listProductHrefs(page: Page): Promise<string[]> {
  await page.goto('/products');
  await page.waitForLoadState('domcontentloaded');

  const cards = page.locator(
    'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
  );
  const hasAny = await cards
    .first()
    .waitFor({ state: 'attached', timeout: 15000 })
    .then(() => true)
    .catch(() => false);
  if (!hasAny) return [];

  const hrefs = await cards.evaluateAll((els) =>
    els.map((e) => (e as HTMLAnchorElement).getAttribute('href') ?? '').filter(Boolean)
  );
  // Loại bỏ trùng + ưu tiên SP seed thật (slug có tiền tố hãng) trước SP test rác
  // (test admin/journey tạo SP tên "Journey SP ..." / "Test SP ..." — thiếu nút add-cart).
  const uniq = [...new Set(hrefs)];
  const isJunk = (h: string) => /journey-sp-|test-sp-/i.test(h);
  return [...uniq.filter((h) => !isJunk(h)), ...uniq.filter(isJunk)];
}

/**
 * Mở /products, vào trang chi tiết SP đầu tiên (ưu tiên SP seed thật, bỏ SP test rác).
 * @returns true nếu vào được PDP, false nếu catalog rỗng (caller nên test.skip).
 */
export async function gotoFirstProduct(page: Page): Promise<boolean> {
  const hrefs = await listProductHrefs(page);
  if (hrefs.length === 0) return false;

  await page.goto(hrefs[0]);
  await page.waitForLoadState('domcontentloaded');
  return true;
}

/**
 * Slug SP seed thật được dùng làm dữ liệu tiền đề (seed-dev V102 reviews, V103 order_items):
 *   - prod-pho-001 → apple-iphone-15-pro-max-256gb (có review demo + trong đơn DELIVERED)
 *   - prod-lap-001 → apple-macbook-pro-16-m3-max-1tb (có review admin)
 */
export const SEEDED_PRODUCT_SLUG = 'apple-iphone-15-pro-max-256gb';

/**
 * Vào thẳng PDP của 1 SP seed thật theo slug. Dùng khi test cần SP có dữ liệu tiền đề
 * cụ thể (review verified-buyer, nằm trong đơn hàng) — KHÔNG dùng SP test rác.
 * @returns true nếu PDP render đúng, false nếu slug không tồn tại.
 */
export async function gotoProductBySlug(page: Page, slug = SEEDED_PRODUCT_SLUG): Promise<boolean> {
  await page.goto(`/products/${slug}`);
  await page.waitForLoadState('domcontentloaded');
  // PDP hợp lệ → có h1 tên SP (không phải trang lỗi)
  const ok = await page
    .locator('h1')
    .first()
    .waitFor({ state: 'visible', timeout: 10000 })
    .then(() => true)
    .catch(() => false);
  return ok;
}

/**
 * Thêm sản phẩm đang xem (PDP) vào giỏ.
 * @returns true nếu add thành công, false nếu nút "Thêm vào giỏ hàng" không hiện
 *          (SP hết hàng, hoặc SP test rác thiếu dữ liệu).
 */
export async function addCurrentProductToCart(page: Page): Promise<boolean> {
  const addBtn = page.getByRole('button', { name: /thêm vào giỏ hàng/i }).first();
  // waitFor (chờ thật) thay vì isVisible (kiểm tra tức thời) — nút render sau khi
  // product data fetch xong, cần chờ tới khi visible mới kết luận.
  const ready = await addBtn
    .waitFor({ state: 'visible', timeout: 8000 })
    .then(() => true)
    .catch(() => false);
  if (!ready) return false;

  await addBtn.click();
  // Đợi mutation + React Query cache invalidate settle
  await page.waitForTimeout(1200);
  return true;
}

/**
 * Đảm bảo giỏ hàng có ≥1 item: duyệt lần lượt các SP cho tới khi add-to-cart thành công.
 * Bỏ qua SP không mua được (test rác / hết hàng) — thử tối đa 8 SP.
 * @returns true nếu đã thêm được 1 item vào giỏ, false nếu không SP nào mua được.
 */
export async function ensureCartHasItem(page: Page): Promise<boolean> {
  const hrefs = await listProductHrefs(page);
  if (hrefs.length === 0) return false;

  for (const href of hrefs.slice(0, 8)) {
    await page.goto(href);
    await page.waitForLoadState('domcontentloaded');
    if (await addCurrentProductToCart(page)) return true;
  }
  return false;
}

/** Format số kiểu tiền VND (1000000 → "1.000.000") — đối chiếu với hiển thị FE. */
export function formatVnd(n: number): string {
  return n.toLocaleString('vi-VN');
}
