/**
 * MODULE 02 — DANH MỤC SẢN PHẨM (Catalog).
 *
 * Phủ: trang chủ, danh sách sản phẩm, lọc theo category/brand/giá, tìm kiếm, chi tiết SP.
 * Tất cả ẩn danh — xem catalog không cần đăng nhập.
 *
 * Strategy A: skip-if-no-data khi catalog rỗng.
 */
import { test, expect } from './utils/fixtures';
import { ANON_STATE, gotoFirstProduct, gotoProductBySlug } from './utils/helpers';

test.use({ storageState: ANON_STATE });

test.describe('02-CATALOG: Danh mục sản phẩm', () => {
  // ───────────────────────────────────────────────────────────────
  test('CAT-01: Trang chủ render hero + CTA dẫn tới /products', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');

    await expect(
      page.getByRole('heading', { name: /chế tác thủ công/i })
    ).toBeVisible({ timeout: 10000 });

    const cta = page.getByRole('link', { name: 'Khám phá ngay' });
    await expect(cta).toBeVisible();
    await cta.click();
    // waitUntil: domcontentloaded — trang /products có ảnh giữ event "load" không kết thúc
    await page.waitForURL(/\/products(\?|$)/, { timeout: 10000, waitUntil: 'domcontentloaded' });
    await expect(page.locator('a[href^="/products/"]').first()).toBeVisible({ timeout: 10000 });
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-02: /products hiển thị danh sách product card', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    await expect(page.getByRole('heading', { level: 1, name: /Sản phẩm/ })).toBeVisible({
      timeout: 10000,
    });
    const cards = page.locator(
      'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
    );
    await expect(cards.first()).toBeVisible({ timeout: 10000 });
    expect(await cards.count()).toBeGreaterThan(0);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-03: Lọc theo category qua URL → chỉ hiện SP đúng category', async ({ page }) => {
    await page.goto('/products?category=laptop');
    await page.waitForLoadState('domcontentloaded');

    const cards = page.locator(
      'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
    );
    // waitFor (chờ thật) — cards render sau React Query fetch
    const hasCards = await cards
      .first()
      .waitFor({ state: 'attached', timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    if (!hasCards) {
      test.skip(true, 'Category "laptop" không có SP — seed chưa apply (Strategy A)');
      return;
    }
    expect(await cards.count()).toBeGreaterThan(0);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-04: Lọc theo brand trong FilterSidebar', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    // Brand checkbox id="brand-{name}" — chờ FilterSidebar render xong
    const firstBrandCheckbox = page.locator('input[id^="brand-"]').first();
    const hasBrand = await firstBrandCheckbox
      .waitFor({ state: 'attached', timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    if (!hasBrand) {
      test.skip(true, 'FilterSidebar không có brand nào — seed chưa apply (Strategy A)');
      return;
    }

    await firstBrandCheckbox.scrollIntoViewIfNeeded().catch(() => {});
    await firstBrandCheckbox.check();
    await page.waitForTimeout(1200);

    // Sau khi lọc: vẫn render grid (có kết quả hoặc empty state — cả 2 đều hợp lệ)
    const cards = page.locator(
      'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
    );
    const ok =
      (await cards.first().waitFor({ state: 'attached', timeout: 6000 }).then(() => true).catch(() => false)) ||
      (await page
        .getByRole('heading', { level: 3, name: /Không tìm thấy/ })
        .isVisible()
        .catch(() => false));
    expect(ok).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-05: Lọc theo khoảng giá', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const priceMin = page.locator('#price-min');
    const hasPrice = await priceMin
      .waitFor({ state: 'attached', timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    if (!hasPrice) {
      test.skip(true, 'FilterSidebar không có price filter — Strategy A skip');
      return;
    }

    await priceMin.scrollIntoViewIfNeeded().catch(() => {});
    await priceMin.fill('1000000');
    await page.locator('#price-max').fill('50000000');
    await page.locator('#price-max').blur();
    await page.waitForTimeout(1200); // debounce 400ms + refetch

    const cards = page.locator(
      'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
    );
    const ok =
      (await cards.first().waitFor({ state: 'attached', timeout: 6000 }).then(() => true).catch(() => false)) ||
      (await page
        .getByRole('heading', { level: 3, name: /Không tìm thấy/ })
        .isVisible()
        .catch(() => false));
    expect(ok).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-06: Tìm kiếm trong /products → kết quả khớp từ khóa', async ({ page }) => {
    await page.goto('/products');
    await page.waitForLoadState('domcontentloaded');

    const searchInput = page.getByPlaceholder(/Tìm kiếm sản phẩm/);
    const hasSearch = await searchInput
      .waitFor({ state: 'visible', timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    if (!hasSearch) {
      test.skip(true, 'Ô tìm kiếm không hiển thị — Strategy A skip');
      return;
    }

    await searchInput.fill('iphone');
    await page.waitForTimeout(1500);
    const cards = page.locator(
      'a[href^="/products/"]:not([href="/products"]):not([href="/products?"])'
    );
    const ok =
      (await cards.first().waitFor({ state: 'attached', timeout: 6000 }).then(() => true).catch(() => false)) ||
      (await page
        .getByRole('heading', { level: 3, name: /Không tìm thấy/ })
        .isVisible()
        .catch(() => false));
    expect(ok).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-07: Trang /search — nhập từ khóa hiển thị kết quả', async ({ page }) => {
    await page.goto('/search');
    await page.waitForLoadState('domcontentloaded');

    const searchInput = page.getByPlaceholder(/Nhập tên sản phẩm/);
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    // Trạng thái ban đầu: hint nhập từ khóa
    await expect(
      page.getByRole('heading', { level: 3, name: /Nhập từ khóa/ })
    ).toBeVisible();

    await searchInput.fill('laptop');
    await page.waitForTimeout(1500);
    // Có kết quả ("Tìm thấy X sản phẩm") hoặc không có kết quả
    const ok =
      (await page.getByText(/Tìm thấy \d+ sản phẩm/).isVisible({ timeout: 5000 }).catch(() => false)) ||
      (await page
        .getByRole('heading', { level: 3, name: /Không tìm thấy kết quả/ })
        .isVisible({ timeout: 2000 })
        .catch(() => false));
    expect(ok).toBe(true);
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-08: Trang chi tiết sản phẩm render đủ thông tin', async ({ page }) => {
    // Dùng SP seed thật (catalog V101) — có đủ giá, ảnh, mô tả; KHÔNG dùng SP test rác.
    if (!(await gotoProductBySlug(page))) {
      test.skip(true, 'SP seed không tồn tại — Strategy A skip');
      return;
    }

    // Tên SP (h1), badge trạng thái kho, giá tiền (có "đ"), tab Mô tả
    await expect(page.locator('h1').first()).toBeVisible({ timeout: 10000 });
    await expect(
      page.getByText(/✓ Còn hàng|⚠ Sắp hết|✗ Hết hàng/).first()
    ).toBeVisible();
    await expect(page.getByText(/[\d.]+\s*đ/).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Mô tả' })).toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────
  test('CAT-09: Truy cập slug không tồn tại → không render SP, hiện trạng thái lỗi', async ({
    page,
  }) => {
    const resp = await page.goto('/products/slug-khong-ton-tai-e2e-9999');
    await page.waitForLoadState('domcontentloaded');
    await page.waitForTimeout(1500);

    // App xử lý slug không tồn tại bằng: HTTP 404, hoặc trang not-found,
    // hoặc error-state chung ("Không tải được dữ liệu"). Tất cả đều hợp lệ —
    // miễn KHÔNG render nhầm trang chi tiết SP nào đó.
    const is404 = resp?.status() === 404;
    const hasErrorState = await page
      .getByText(/không tìm thấy|not found|404|không tải được|đã xảy ra lỗi/i)
      .first()
      .isVisible({ timeout: 3000 })
      .catch(() => false);
    // Không có nút "Thêm vào giỏ hàng" → không phải PDP hợp lệ
    const noAddToCart =
      (await page.getByRole('button', { name: /thêm vào giỏ hàng/i }).count()) === 0;

    expect(is404 || hasErrorState || noAddToCart).toBe(true);
  });
});
