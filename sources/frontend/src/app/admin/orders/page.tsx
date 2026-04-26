'use client';

import React, { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import styles from '../products/page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminOrders } from '@/services/orders';

// Backend AdminOrderDto shape
interface AdminOrder {
  id: string;
  userId: string;
  total?: number;
  totalAmount?: number;
  status: string;
  note?: string;
  createdAt: string;
  updatedAt?: string;
}

const statusLabel: Record<string, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};
const statusVariant: Record<string, 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock'> = {
  PENDING: 'default',
  CONFIRMED: 'new',
  SHIPPING: 'hot',
  DELIVERED: 'sale',
  CANCELLED: 'out-of-stock',
};

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState<AdminOrder[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const router = useRouter();
  const { showToast: _showToast } = useToast();

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const resp = await listAdminOrders();
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setOrders((resp?.content ?? []) as any[]);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý đơn hàng</h1>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Mã đơn</th>
              <th>Khách hàng</th>
              <th>Số sản phẩm</th>
              <th>Tổng tiền</th>
              <th>Trạng thái</th>
              <th>Ngày đặt</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {loading && [...Array(5)].map((_, i) => (
              <tr key={i}>
                <td colSpan={7}>
                  <div className="skeleton" style={{ height: 60, borderRadius: 'var(--radius-md)' }} />
                </td>
              </tr>
            ))}

            {!loading && failed && (
              <tr>
                <td colSpan={7}>
                  <RetrySection onRetry={load} loading={loading} />
                </td>
              </tr>
            )}

            {!loading && !failed && orders.length === 0 && (
              <tr>
                <td colSpan={7}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-7) 0' }}>
                    <h3 style={{ fontSize: 'var(--text-title-lg)', color: 'var(--on-surface)' }}>Chưa có đơn hàng nào</h3>
                    <p style={{ fontSize: 'var(--text-body-md)', color: 'var(--on-surface-variant)' }}>Các đơn hàng của khách sẽ hiển thị ở đây</p>
                  </div>
                </td>
              </tr>
            )}

            {!loading && !failed && orders.map(o => (
              <tr key={o.id}>
                <td className={styles.tdBold}>{o.id.slice(0, 8)}</td>
                <td>{o.userId.slice(0, 8)}</td>
                <td>—</td>
                <td className={styles.price}>{(o.totalAmount ?? o.total)?.toLocaleString('vi-VN')}₫</td>
                <td>
                  <Badge variant={statusVariant[o.status] ?? 'default'}>
                    {statusLabel[o.status] ?? o.status}
                  </Badge>
                </td>
                <td className={styles.tdMuted}>{new Date(o.createdAt).toLocaleDateString('vi-VN')}</td>
                <td>
                  <button
                    className={styles.actionBtn}
                    aria-label="Xem chi tiết đơn hàng"
                    onClick={() => router.push(`/admin/orders/${o.id}`)}
                  >
                    📋
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
