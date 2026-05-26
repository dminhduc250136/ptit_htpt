/**
 * Phase 19 / Plan 19-04 (ADMIN-05) — Low-stock list section.
 * D-10: click row → router.push('/admin/products?highlight={id}').
 * D-15 empty state: "Tất cả sản phẩm đủ hàng ✓".
 * Badge: đỏ #dc2626 nếu stock<5, cam #f59e0b nếu 5–9.
 */
'use client';
import { useRouter } from 'next/navigation';
import type { LowStockItem } from '@/services/charts';

export function LowStockSection({ data }: { data: LowStockItem[] }) {
  const router = useRouter();
  if (data.length === 0) {
    return (
      <p style={{ color: 'var(--on-surface-variant)' }}>Tất cả sản phẩm đủ hàng ✓</p>
    );
  }
  return (
    <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
      {data.map((item) => (
        <li
          key={item.id}
          onClick={() => router.push(`/admin/products?highlight=${item.id}`)}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 'var(--space-3)',
            padding: 'var(--space-3)',
            cursor: 'pointer',
            borderBottom: '1px solid var(--outline)',
          }}
        >
          {item.thumbnailUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={item.thumbnailUrl}
              alt={item.name}
              width={40}
              height={40}
              style={{ objectFit: 'cover', borderRadius: 'var(--radius-sm)' }}
            />
          )}
          <div style={{ flex: 1 }}>
            <p style={{ fontWeight: 'var(--weight-medium)', margin: 0 }}>{item.name}</p>
            {item.brand && (
              <p
                style={{
                  fontSize: 'var(--text-body-sm)',
                  color: 'var(--on-surface-variant)',
                  margin: 0,
                }}
              >
                {item.brand}
              </p>
            )}
          </div>
          <span
            style={{
              padding: '2px 8px',
              borderRadius: 'var(--radius-sm)',
              fontWeight: 'var(--weight-bold)',
              background: item.stock < 5 ? '#dc2626' : '#f59e0b',
              color: 'white',
            }}
          >
            Còn {item.stock}
          </span>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              router.push(`/admin/products?highlight=${item.id}`);
            }}
            style={{
              padding: '4px 10px',
              border: '1px solid var(--outline)',
              borderRadius: 'var(--radius-sm)',
              background: 'var(--surface)',
              cursor: 'pointer',
            }}
          >
            Sửa
          </button>
        </li>
      ))}
    </ul>
  );
}
