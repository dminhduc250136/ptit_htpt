/**
 * Fixture Playwright dùng chung — ghi đè `page.goto` và `page.reload` để mặc định
 * chờ tới `domcontentloaded` thay vì `load`.
 *
 * LÝ DO: trang web có ảnh (next/image), iframe, hoặc resource nền giữ sự kiện
 * `load` không bao giờ hoàn tất → `page.goto()` / `page.reload()` mặc định (chờ
 * `load`) bị timeout dù trang đã render xong và tương tác được. Chờ
 * `domcontentloaded` là đủ cho E2E vì test luôn dùng `expect(...).toBeVisible()`
 * để chờ phần tử cụ thể sau đó.
 *
 * Mọi spec import { test, expect } từ file này thay vì từ '@playwright/test'.
 */
import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    const originalGoto = page.goto.bind(page);
    const originalReload = page.reload.bind(page);
    // Ghi đè: nếu caller không chỉ định waitUntil → mặc định 'domcontentloaded'
    page.goto = ((url: string, options?: Parameters<typeof originalGoto>[1]) => {
      return originalGoto(url, { waitUntil: 'domcontentloaded', ...options });
    }) as typeof page.goto;
    page.reload = ((options?: Parameters<typeof originalReload>[0]) => {
      return originalReload({ waitUntil: 'domcontentloaded', ...options });
    }) as typeof page.reload;
    await use(page);
  },
});

export { expect };
