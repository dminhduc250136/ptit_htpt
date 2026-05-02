'use client';

import { useCallback, useEffect, useState } from 'react';
import styles from './page.module.css';
import {
  fetchProductStats,
  fetchOrderStats,
  fetchUserStats,
  type ProductStats,
  type OrderStats,
  type UserStats,
} from '@/services/stats';

type CardState<T> = { status: 'loading' | 'success' | 'error'; data?: T; error?: string };

/**
 * Phase 9 / Plan 09-04 (UI-02). Trimmed dashboard:
 * - D-08: chỉ 4 KPI required, xóa totalRevenue/recent orders table/quick stats panel/mock arrays.
 * - D-09: Promise.allSettled (KHÔNG Promise.all) — 1 endpoint fail không block 3 cards còn lại.
 *         Per-card loading skeleton + error fallback với retry icon (re-fetch chỉ endpoint đó).
 */
export default function AdminDashboard() {
  const [productCard, setProductCard] = useState<CardState<ProductStats>>({ status: 'loading' });
  const [orderCard, setOrderCard] = useState<CardState<OrderStats>>({ status: 'loading' });
  const [userCard, setUserCard] = useState<CardState<UserStats>>({ status: 'loading' });

  const loadProduct = useCallback(async () => {
    setProductCard({ status: 'loading' });
    try {
      const data = await fetchProductStats();
      setProductCard({ status: 'success', data });
    } catch (e) {
      setProductCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, []);

  const loadOrder = useCallback(async () => {
    setOrderCard({ status: 'loading' });
    try {
      const data = await fetchOrderStats();
      setOrderCard({ status: 'success', data });
    } catch (e) {
      setOrderCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, []);

  const loadUser = useCallback(async () => {
    setUserCard({ status: 'loading' });
    try {
      const data = await fetchUserStats();
      setUserCard({ status: 'success', data });
    } catch (e) {
      setUserCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, []);

  useEffect(() => {
    // D-09: Promise.allSettled — không await trong useEffect cleanup
    Promise.allSettled([loadProduct(), loadOrder(), loadUser()]);
  }, [loadProduct, loadOrder, loadUser]);

  return (
    <div className={styles.dashboard}>
      <h1 className={styles.title}>Dashboard</h1>
      <div className={styles.statsGrid}>
        <KpiCard
          label="Sản phẩm"
          icon="🏷️"
          color="var(--secondary)"
          state={productCard}
          renderValue={(d) => String(d.totalProducts)}
          onRetry={loadProduct}
        />
        <KpiCard
          label="Tổng đơn hàng"
          icon="📦"
          color="var(--primary)"
          state={orderCard}
          renderValue={(d) => String(d.totalOrders)}
          onRetry={loadOrder}
        />
        <KpiCard
          label="Khách hàng"
          icon="👥"
          color="#f59e0b"
          state={userCard}
          renderValue={(d) => String(d.totalUsers)}
          onRetry={loadUser}
        />
        <KpiCard
          label="Đơn chờ xử lý"
          icon="⏳"
          color="#dc2626"
          state={orderCard}
          renderValue={(d) => String(d.pendingOrders)}
          onRetry={loadOrder}
        />
      </div>
    </div>
  );
}

interface KpiCardProps<T> {
  label: string;
  icon: string;
  color: string;
  state: CardState<T>;
  renderValue: (d: T) => string;
  onRetry: () => void;
}

function KpiCard<T>({ label, icon, color, state, renderValue, onRetry }: KpiCardProps<T>) {
  return (
    <div className={styles.statCard} data-card-label={label}>
      <div className={styles.statIcon}>{icon}</div>
      <div className={styles.statBody}>
        {state.status === 'loading' && (
          <div className={styles.skeleton} aria-label="Đang tải" />
        )}
        {state.status === 'success' && state.data && (
          <p className={styles.statValue} style={{ color }}>{renderValue(state.data)}</p>
        )}
        {state.status === 'error' && (
          <div className={styles.errorRow}>
            <span className={styles.statValue} style={{ color: 'var(--on-surface-variant)' }}>--</span>
            <button
              type="button"
              className={styles.retryBtn}
              onClick={onRetry}
              aria-label={`Tải lại ${label}`}
              title={state.error}
            >⟳</button>
          </div>
        )}
        <p className={styles.statLabel}>{label}</p>
      </div>
    </div>
  );
}
