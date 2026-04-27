'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import OrderFilterBar from '@/components/ui/OrderFilterBar/OrderFilterBar';
import { listMyOrders } from '@/services/orders';
import { useAuth } from '@/providers/AuthProvider';
import { formatPrice } from '@/services/api';
import type { Order } from '@/types';

const statusMap: Record<
  string,
  { label: string; variant: 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock' }
> = {
  PENDING: { label: 'Chờ xác nhận', variant: 'default' },
  CONFIRMED: { label: 'Đã xác nhận', variant: 'new' },
  SHIPPING: { label: 'Đang giao', variant: 'hot' },
  DELIVERED: { label: 'Đã giao', variant: 'sale' },
  CANCELLED: { label: 'Đã hủy', variant: 'out-of-stock' },
  RETURNED: { label: 'Đã trả', variant: 'out-of-stock' },
};

export default function OrdersPage() {
  const { user } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();

  const statusParam = searchParams.get('status') ?? 'ALL';
  const fromParam   = searchParams.get('from')   ?? '';
  const toParam     = searchParams.get('to')     ?? '';
  const qParam      = searchParams.get('q')      ?? '';

  const [orders, setOrders] = useState<Order[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersFailed, setOrdersFailed] = useState(false);
  const [totalElements, setTotalElements] = useState(0);

  async function loadOrders() {
    setOrdersLoading(true);
    setOrdersFailed(false);
    try {
      const resp = await listMyOrders({
        page: 0,
        size: 20,
        sort: 'createdAt,desc',
        status: statusParam !== 'ALL' ? statusParam : undefined,
        from: fromParam || undefined,
        to: toParam || undefined,
        q: qParam || undefined,
      });
      setOrders(resp?.content ?? []);
      setTotalElements(resp?.totalElements ?? 0);
    } catch {
      setOrdersFailed(true);
    } finally {
      setOrdersLoading(false);
    }
  }

  useEffect(() => {
    if (!user?.id) return;
    loadOrders();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams, user?.id]);

  const handleFilterChange = useCallback(
    (filters: { status: string; from: string; to: string; q: string }) => {
      const qs = new URLSearchParams();
      if (filters.status && filters.status !== 'ALL') qs.set('status', filters.status);
      if (filters.from) qs.set('from', filters.from);
      if (filters.to)   qs.set('to',   filters.to);
      if (filters.q)    qs.set('q',    filters.q);
      router.push(`/profile/orders${qs.toString() ? '?' + qs.toString() : ''}`);
    },
    [router],
  );

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Lịch sử đơn hàng</h1>
          {totalElements > 0 && (
            <p className={styles.pageSubtitle}>{totalElements} đơn hàng</p>
          )}
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {/* OrderFilterBar — sticky sau header */}
        <div className={styles.stickyFilter}>
          <OrderFilterBar
            initialStatus={statusParam}
            initialFrom={fromParam}
            initialTo={toParam}
            initialQ={qParam}
            onChange={handleFilterChange}
          />
        </div>

        {ordersLoading ? (
          <div className="skeleton" style={{ height: 120, borderRadius: 'var(--radius-lg)' }} />
        ) : ordersFailed ? (
          <RetrySection onRetry={loadOrders} loading={ordersLoading} />
        ) : orders.length === 0 ? (
          <div className={styles.emptyState}>
            <p>Không tìm thấy đơn hàng nào</p>
            <p style={{ color: 'var(--on-surface-variant)', fontSize: 'var(--text-body-sm)' }}>
              Thử thay đổi bộ lọc hoặc xóa bộ lọc để xem tất cả đơn hàng.
            </p>
          </div>
        ) : (
          <div className={styles.orderList}>
            {orders.map((order) => (
              <Link
                key={order.id}
                href={`/profile/orders/${order.id}`}
                className={styles.orderCard}
              >
                <div className={styles.orderHeader}>
                  <span className={styles.orderCode}>{order.orderCode}</span>
                  <Badge
                    variant={statusMap[order.orderStatus ?? '']?.variant || 'default'}
                  >
                    {statusMap[order.orderStatus ?? '']?.label ?? order.orderStatus}
                  </Badge>
                </div>
                <div className={styles.orderItems}>
                  {order.items?.slice(0, 4).map((item) => (
                    <div key={item.id} className={styles.orderItemThumb}>
                      {item.productImage ? (
                        <Image
                          src={item.productImage}
                          alt={item.productName}
                          fill
                          sizes="48px"
                          style={{ objectFit: 'cover' }}
                        />
                      ) : null}
                    </div>
                  ))}
                  <span className={styles.orderItemCount}>
                    {order.items?.length ?? 0} sản phẩm
                  </span>
                </div>
                <div className={styles.orderFooter}>
                  <span className={styles.orderDate}>
                    {order.createdAt
                      ? new Date(order.createdAt).toLocaleDateString('vi-VN')
                      : '—'}
                  </span>
                  <span className={styles.orderTotal}>
                    {formatPrice(order.totalAmount ?? 0)}
                  </span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
