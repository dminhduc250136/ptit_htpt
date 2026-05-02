# Phase 17: Sửa Order Detail Items - Pattern Map

**Mapped:** 2026-05-02
**Files analyzed:** 7 (5 source + 2 e2e)
**Analogs found:** 7 / 7

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `sources/frontend/src/app/admin/orders/[id]/page.tsx` | page (client component) | request-response + transform | `sources/frontend/src/app/profile/orders/[id]/page.tsx` (cùng repo, role identical) | exact (cross-tier sibling) |
| `sources/frontend/src/app/profile/orders/[id]/page.tsx` | page (client component) | request-response + transform | self (extend existing render) | self-extend |
| `sources/frontend/src/app/profile/orders/[id]/page.module.css` | CSS module | n/a (style) | self (`.itemImg` line 30 + `.tableCell` line 88) | self-extend |
| `sources/frontend/src/lib/orderLabels.ts` | utility (constants) | none (pure data maps) | inline maps trong `profile/orders/[id]/page.tsx:13-32` | extract-from |
| `sources/frontend/src/lib/useEnrichedItems.ts` | hook (data enrichment) | parallel fetch + transform | `app/admin/page.tsx:57-60` (`Promise.allSettled` pattern) | partial (pattern-only, không có hook precedent) |
| `sources/frontend/e2e/admin-orders.spec.ts` | e2e test | UI assertion | self + `e2e/order-detail.spec.ts` | self-extend |
| `sources/frontend/e2e/order-detail.spec.ts` | e2e test | UI assertion | self (extend ORD-DTL-2 assertions) | self-extend |

## Pattern Assignments

### `sources/frontend/src/app/admin/orders/[id]/page.tsx` (page, request-response + transform)

**Analog:** `sources/frontend/src/app/profile/orders/[id]/page.tsx` (đối ngẫu user-side đã render đúng phần lớn).

**Imports pattern** (admin file hiện tại line 1-10 + thêm mới theo user-side line 9-11):
```tsx
'use client';
import React, { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';                          // NEW (D-06)
import { useParams, useRouter } from 'next/navigation';
import styles from '../../products/page.module.css';     // giữ — admin reuse style
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { getAdminOrderById, updateOrderState } from '@/services/orders';
import type { Order } from '@/types';                    // NEW (D-02)
import { paymentMethodMap, statusMap } from '@/lib/orderLabels';  // NEW (D-04)
import { useEnrichedItems } from '@/lib/useEnrichedItems';        // NEW (D-01)
```

**Type/state pattern (D-02 — replace lines 12-22 + 46 + 58):**
```tsx
// XÓA: interface AdminOrder { ... } (lines 13-22)
// XÓA: const [order, setOrder] = useState<AdminOrder | null>(null);
// XÓA: const data = await getAdminOrderById(id) as any;

// THAY:
const [order, setOrder] = useState<Order | null>(null);
const data = await getAdminOrderById(id);   // đã Promise<Order>, KHÔNG cast
setOrder(data);
setNewStatus(data.status ?? data.orderStatus ?? 'PENDING');  // null-safe per types/index.ts:196-197
```

**Load + error pattern (giữ — đã đúng, lines 52-66):**
```tsx
const load = useCallback(async () => {
  if (!id) return;
  setLoading(true);
  setFailed(false);
  try {
    const data = await getAdminOrderById(id);
    setOrder(data);
    setNewStatus(data.status ?? 'PENDING');
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, [id]);
```

**Shipping card pattern (D-04 — replace lines 134-139, source: user page lines 204-208 + 211-215):**
```tsx
// User-side reference (profile/orders/[id]/page.tsx:204-208):
{addr && (
  <div className={styles.infoCard}>
    <h4 className={styles.infoCardTitle}>Địa chỉ giao hàng</h4>
    <p>{[addr.street, addr.ward, addr.district, addr.city].filter(Boolean).join(', ')}</p>
  </div>
)}

// Admin port (giữ inline-style cardStyle/labelStyle):
<div style={cardStyle}>
  <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>
    Thông tin giao hàng
  </h3>
  <p style={labelStyle}>
    Địa chỉ: <strong>{
      order.shippingAddress
        ? [order.shippingAddress.street, order.shippingAddress.ward,
           order.shippingAddress.district, order.shippingAddress.city]
            .filter(Boolean).join(', ')
        : '—'
    }</strong>
  </p>
  <p style={labelStyle}>
    Thanh toán: <strong>{paymentMethodMap[order.paymentMethod] ?? order.paymentMethod ?? '—'}</strong>
  </p>
  {order.note && <p style={labelStyle}>Ghi chú: <strong>{order.note}</strong></p>}
</div>
```

**Items table pattern (replace lines 142-152, source: user page lines 152-177 + thumbnail extension):**
```tsx
const enriched = useEnrichedItems(order.items);

<div style={cardStyle}>
  <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>
    Sản phẩm
  </h3>
  {enriched.length === 0 ? (
    <p style={{ color: 'var(--on-surface-variant)' }}>Đơn hàng không có sản phẩm</p>  // D-05
  ) : (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr>
          <th style={{ textAlign: 'left',  padding: 'var(--space-2)' }}>Sản phẩm</th>
          <th style={{ textAlign: 'center',padding: 'var(--space-2)' }}>Số lượng</th>
          <th style={{ textAlign: 'right', padding: 'var(--space-2)' }}>Đơn giá</th>
          <th style={{ textAlign: 'right', padding: 'var(--space-2)' }}>Thành tiền</th>
        </tr>
      </thead>
      <tbody>
        {enriched.map(it => {
          const unitPrice = it.unitPrice ?? it.price ?? 0;
          const lineTotal = it.lineTotal ?? it.subtotal ?? 0;
          return (
            <tr key={it.id} style={{ borderTop: '1px solid rgba(195,198,214,0.15)' }}>
              <td style={{ padding: 'var(--space-2)', display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                {/* Thumbnail per D-06 — see Shared Pattern: Image+Placeholder */}
                <ProductThumb url={it.thumbnailUrl} alt={it.productName} />
                <div>
                  <div style={{ fontWeight: 500 }}>{it.productName}</div>
                  <div style={{ fontSize: 'var(--text-body-sm)', color: 'var(--on-surface-variant)' }}>
                    {it.brand ?? '—'}
                  </div>
                </div>
              </td>
              <td style={{ textAlign: 'center', padding: 'var(--space-2)' }}>{it.quantity}</td>
              <td style={{ textAlign: 'right',  padding: 'var(--space-2)' }}>{unitPrice.toLocaleString('vi-VN')}₫</td>
              <td style={{ textAlign: 'right',  padding: 'var(--space-2)', fontWeight: 600 }}>{lineTotal.toLocaleString('vi-VN')}₫</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  )}
  <div style={{ borderTop: '1px solid rgba(195,198,214,0.15)', marginTop: 'var(--space-3)', paddingTop: 'var(--space-3)' }}>
    <span style={{ fontWeight: 700, fontSize: 'var(--text-title-sm)' }}>Tổng cộng: </span>
    <span style={{ color: 'var(--primary)', fontWeight: 700 }}>
      {(order.totalAmount ?? order.total ?? 0).toLocaleString('vi-VN')}₫
    </span>
  </div>
</div>
```

**Status map cleanup (D-04 cross-cut, optional):** Có thể replace inline `STATUS_LABELS` + `STATUS_VARIANTS` (lines 25-38) bằng import `statusMap` từ `@/lib/orderLabels` — `statusMap[status].label` và `statusMap[status].variant`. KHÔNG bắt buộc trong scope (không nằm trong success criteria).

---

### `sources/frontend/src/app/profile/orders/[id]/page.tsx` (page, request-response + transform)

**Analog:** self (đã đúng 90%, chỉ extend).

**Import pattern (replace lines 13-32):**
```tsx
// XÓA inline statusMap, paymentMethodMap, paymentStatusMap (lines 13-32)
// THAY:
import { statusMap, paymentMethodMap, paymentStatusMap } from '@/lib/orderLabels';
import Image from 'next/image';                          // NEW (D-06)
import { useEnrichedItems } from '@/lib/useEnrichedItems';  // NEW (D-01)
```

**Items table extend (replace lines 162-175 — extend cell "Sản phẩm" với thumbnail+brand):**
```tsx
const enriched = useEnrichedItems(order.items);

{enriched.length === 0 ? (
  <p style={{ color: 'var(--on-surface-variant)' }}>Đơn hàng không có sản phẩm</p>  // D-05
) : (
  <table className={styles.itemsTable}>
    <thead>...</thead>
    <tbody>
      {enriched.map((item) => {
        const lineTotal = item.lineTotal ?? item.subtotal ?? 0;
        const unitPrice = item.unitPrice ?? item.price ?? 0;
        return (
          <tr key={item.id} className={styles.tableRow}>
            <td className={styles.tableCell}>
              <div className={styles.itemCellInner}>     {/* NEW class — see CSS */}
                <ProductThumb url={item.thumbnailUrl} alt={item.productName} />
                <div>
                  <div className={styles.itemName}>{item.productName}</div>
                  <div className={styles.itemBrand}>{item.brand ?? '—'}</div>
                </div>
              </div>
            </td>
            <td className={styles.tableCell}>{item.quantity}</td>
            <td className={styles.tableCell}>{formatPrice(unitPrice)}</td>
            <td className={`${styles.tableCell} ${styles.lineTotalCell}`}>{formatPrice(lineTotal)}</td>
          </tr>
        );
      })}
    </tbody>
  </table>
)}
```

---

### `sources/frontend/src/app/profile/orders/[id]/page.module.css` (CSS module)

**Analog:** self — pattern `.itemImg` (line 30) + `.tableCell` (line 88) đã có.

**Existing relevant rules (KHÔNG sửa):**
```css
/* Line 30 */
.itemImg { position: relative; width: 72px; height: 72px; border-radius: var(--radius-md); overflow: hidden; flex-shrink: 0; }

/* Lines 88-93 */
.tableCell {
  padding: var(--space-2) var(--space-3);
  font-size: var(--text-body-md);
  color: var(--on-surface);
  vertical-align: middle;
}
```

**Add new rules (append cuối file):**
```css
/* Phase 17: thumbnail cell layout cho items table */
.itemCellInner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}
.itemThumb {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  overflow: hidden;
  flex-shrink: 0;
  object-fit: cover;
}
.itemThumbPlaceholder {
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  background: var(--surface-container-high);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--on-surface-variant);
}
.itemBrand {
  font-size: var(--text-body-sm);
  color: var(--on-surface-variant);
  margin-top: 2px;
}
```

---

### `sources/frontend/src/lib/orderLabels.ts` (utility, NEW)

**Analog:** inline maps trong `sources/frontend/src/app/profile/orders/[id]/page.tsx:13-32`.

**Full file content (extract verbatim từ user page):**
```ts
// src/lib/orderLabels.ts
// Phase 17: extract Vietnamese label maps để reuse cho admin + user order pages.

export const statusMap: Record<string, { label: string; variant: 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock' }> = {
  PENDING:   { label: 'Chờ xác nhận', variant: 'default' },
  CONFIRMED: { label: 'Đã xác nhận',  variant: 'new' },
  SHIPPING:  { label: 'Đang giao',    variant: 'hot' },
  DELIVERED: { label: 'Đã giao',      variant: 'sale' },
  CANCELLED: { label: 'Đã hủy',       variant: 'out-of-stock' },
};

export const paymentMethodMap: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
};

export const paymentStatusMap: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  REFUNDED: 'Đã hoàn tiền',
};
```

**Test note:** không có unit test framework — verify bằng `tsc --noEmit` + import-from-page.

---

### `sources/frontend/src/lib/useEnrichedItems.ts` (hook, NEW)

**Analog:** `sources/frontend/src/app/admin/page.tsx:57-60` (`Promise.allSettled` precedent — chỉ là pattern, KHÔNG phải hook). Đây là hook đầu tiên trong project (no `src/hooks/` hay `src/lib/use*` precedent — verified by Glob).

**Pattern excerpt từ admin/page.tsx:57-60:**
```tsx
useEffect(() => {
  // D-09: Promise.allSettled — không await trong useEffect cleanup
  Promise.allSettled([loadProduct(), loadOrder(), loadUser()]);
}, [loadProduct, loadOrder, loadUser]);
```

**Service signature từ services/products.ts:55:**
```ts
export function getProductById(id: string): Promise<Product> {
  return httpGet<Product>(`/api/products/${encodeURIComponent(id)}`);
}
```

**Full file content (per RESEARCH Pattern 1, lines 156-184):**
```ts
// src/lib/useEnrichedItems.ts
// Phase 17 / D-01: parallel fetch product detail (image+brand) cho mỗi unique
// productId trong order.items. Dùng Promise.allSettled để 1 product 404 không
// kill toàn bộ render. Cleanup `cancelled` flag để tránh setState after unmount.
'use client';

import { useEffect, useState } from 'react';
import { getProductById } from '@/services/products';
import type { OrderItem } from '@/types';

export type EnrichedItem = OrderItem & {
  thumbnailUrl?: string;
  brand?: string;
};

type EnrichmentMap = Record<string, { thumbnailUrl?: string; brand?: string }>;

export function useEnrichedItems(items: OrderItem[] | undefined): EnrichedItem[] {
  const [map, setMap] = useState<EnrichmentMap>({});

  useEffect(() => {
    if (!items?.length) return;
    const uniqueIds = [...new Set(items.map(i => i.productId))];
    let cancelled = false;
    Promise.allSettled(uniqueIds.map(id => getProductById(id))).then(results => {
      if (cancelled) return;
      const next: EnrichmentMap = {};
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value) {
          next[uniqueIds[i]] = {
            thumbnailUrl: r.value.thumbnailUrl,
            brand: r.value.brand,
          };
        }
      });
      setMap(next);
    });
    return () => { cancelled = true; };
  }, [items]);

  return (items ?? []).map(it => ({ ...it, ...map[it.productId] }) as EnrichedItem);
}
```

**Pitfall reminders (per RESEARCH §Risks):**
- Pitfall 7: `cancelled` flag bắt buộc.
- Pitfall 8: deps `[items]` ổn định vì items chỉ set sau `setOrder` 1 lần. Nếu cẩn thận hơn: dùng `[items?.map(i=>i.productId).join(',')]` để dedupe identity.

---

### `sources/frontend/e2e/admin-orders.spec.ts` (e2e test, EXTEND)

**Analog:** self + sibling `e2e/order-detail.spec.ts` (cùng pattern Playwright + storageState).

**Existing pattern (lines 31-60) — navigate via "Xem chi tiết" button.** Reuse navigation, thêm assertions.

**New test cần thêm (append sau ADM-ORD-2):**
```ts
test('ADM-ORD-3: detail page render items table + KHÔNG còn placeholder Phase 8', async ({ page }) => {
  await page.goto('/admin/orders');
  await expect(page.getByRole('heading', { name: 'Quản lý đơn hàng' })).toBeVisible({ timeout: 10000 });
  await page.waitForTimeout(2000);

  const detailBtn = page.locator('[aria-label="Xem chi tiết đơn hàng"]').first();
  const btnVisible = await detailBtn.isVisible({ timeout: 5000 }).catch(() => false);
  if (!btnVisible) {
    test.skip(true, 'Chưa có đơn hàng — cần seed trước');
    return;
  }
  await detailBtn.click();
  await page.waitForURL(/\/admin\/orders\/[^/]+$/, { timeout: 10000 });

  // Assert placeholder cũ KHÔNG còn (success criterion ADMIN-06)
  await expect(page.getByText('khả dụng sau khi Phase 8')).toHaveCount(0);

  // Assert shipping/payment headings render
  await expect(page.getByText('Thông tin giao hàng')).toBeVisible({ timeout: 5000 });
  await expect(page.getByRole('heading', { name: 'Sản phẩm' })).toBeVisible();
});
```

---

### `sources/frontend/e2e/order-detail.spec.ts` (e2e test, EXTEND)

**Analog:** self (extend ORD-DTL-2 — lines 34-74).

**Existing assertions (lines 64-73)** check 4 column headers + 2 info card headings. Cần thêm:

```ts
// Append vào cuối ORD-DTL-2 (sau line 73), trước test close brace:

// Phase 17: assert items table có ≥ 1 row (skip nếu order rỗng / legacy)
const rowCount = await page.locator('table tbody tr').count();
if (rowCount > 0) {
  // Brand subtitle (có thể là "—" nếu enrichment fail)
  await expect(page.locator('table tbody tr').first()).toBeVisible();
}
```

**KHÔNG assert `<img>` thumbnail** (per Open Question #2 — thumbnail nice-to-have, manual UAT).

---

## Shared Patterns

### Pattern A: Image + Placeholder fallback (D-06)

**Source:** RESEARCH Pattern 3 + Phase 15 PUB-01 precedent.
**Apply to:** Both `admin/orders/[id]/page.tsx` + `profile/orders/[id]/page.tsx`.

Có thể inline tại từng call-site HOẶC tạo small helper component (planner discretion):

```tsx
// Inline pattern (KHÔNG cần component nếu chỉ 2 callers)
{thumbnailUrl ? (
  <Image
    src={thumbnailUrl}
    width={64} height={64}
    alt={productName}
    style={{ borderRadius: 'var(--radius-md)', objectFit: 'cover', flexShrink: 0 }}
  />
) : (
  <div style={{
    width: 64, height: 64,
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface-container-high)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    color: 'var(--on-surface-variant)',
    flexShrink: 0,
  }}>
    📦
  </div>
)}
```

**Pitfall (RESEARCH #6):** `next.config.ts` đã whitelist `images.unsplash.com` + `lh3.googleusercontent.com`. URL ngoài whitelist → fallback placeholder.

### Pattern B: Auto-unwrap envelope (no manual `.data`)

**Source:** `sources/frontend/src/services/http.ts:106-108`.
**Apply to:** Cả 2 page khi gọi `getAdminOrderById` / `getOrderById` / `getProductById`.

```ts
// services/orders.ts:77 đã trả Promise<Order> (sau httpGet auto-unwrap)
export function getAdminOrderById(id: string): Promise<Order> { ... }

// → KHÔNG cần `as any`, KHÔNG cần `.data`
const order = await getAdminOrderById(id);
```

### Pattern C: Null-safe order field aliases

**Source:** `sources/frontend/src/app/profile/orders/[id]/page.tsx:103-107` + `types/index.ts:188-218`.
**Apply to:** Both pages khi đọc fields có cả legacy alias.

```tsx
const orderStatus = order.orderStatus ?? order.status ?? 'PENDING';
const totalAmount = order.totalAmount ?? order.total ?? 0;
const unitPrice  = item.unitPrice ?? item.price ?? 0;
const lineTotal  = item.lineTotal ?? item.subtotal ?? 0;
```

**Reason:** BE trả `status` + `unitPrice` + `lineTotal`; FE legacy types support cả `orderStatus` + `price` + `subtotal`. Always `??` cả hai.

### Pattern D: Loading + Error + Empty state triad

**Source:** `profile/orders/[id]/page.tsx:60-99` (skeleton + RetrySection + notFound).
**Apply to:** Cả 2 page (admin đã có pattern này lines 84-94 — giữ nguyên).

```tsx
if (loading) return <Skeleton... />;
if (failed)  return <RetrySection onRetry={load} loading={loading} />;
if (!order)  return <EmptyState... />;
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/lib/useEnrichedItems.ts` | hook (data enrichment) | parallel fetch + transform | Project chưa có custom hook nào (`src/hooks/` không tồn tại, không file nào export `function use*`). Pattern lấy từ `Promise.allSettled` precedent ở `app/admin/page.tsx:59` nhưng đó là code inline trong page, không phải hook. Planner cần dùng RESEARCH Pattern 1 làm authoritative reference. |

**Note:** `src/lib/` directory có thể chưa tồn tại — planner cần tạo cùng 2 file mới.

---

## Metadata

**Analog search scope:**
- `sources/frontend/src/app/**/orders/**` (page roles)
- `sources/frontend/src/services/**` (data layer)
- `sources/frontend/src/types/**` (type definitions)
- `sources/frontend/src/lib/**` + `src/hooks/**` (hook precedent — NONE found)
- `sources/frontend/e2e/*.spec.ts` (test analogs)

**Files scanned:** 9 (2 page tsx, 1 css module, 1 types, 1 services products, 2 e2e specs, 1 admin dashboard for `Promise.allSettled` precedent, 1 hook directory glob).

**Pattern extraction date:** 2026-05-02
