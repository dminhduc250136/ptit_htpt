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
import { ChartCard, type CardState } from '@/components/admin/ChartCard';
import { RevenueChart } from '@/components/admin/RevenueChart';
import { TopProductsChart } from '@/components/admin/TopProductsChart';
import { StatusDistributionChart } from '@/components/admin/StatusDistributionChart';
import { UserSignupsChart } from '@/components/admin/UserSignupsChart';
import { LowStockSection } from '@/components/admin/LowStockSection';
import {
  fetchRevenueChart,
  fetchTopProducts,
  fetchStatusDistrib,
  fetchUserSignups,
  fetchLowStock,
  type Range,
  type RevenuePoint,
  type TopProductPoint,
  type StatusPoint,
  type SignupPoint,
  type LowStockItem,
} from '@/services/charts';

type KpiCardState<T> = { status: 'loading' | 'success' | 'error'; data?: T; error?: string };

/**
 * Phase 9 / Plan 09-04 (UI-02). Trimmed dashboard:
 * - D-08: chỉ 4 KPI required, xóa totalRevenue/recent orders table/quick stats panel/mock arrays.
 * - D-09: Promise.allSettled (KHÔNG Promise.all) — 1 endpoint fail không block 3 cards còn lại.
 *         Per-card loading skeleton + error fallback với retry icon (re-fetch chỉ endpoint đó).
 *
 * Phase 19 / Plan 19-04 (ADMIN-01..05) extension:
 * - D-06: 1 dropdown global điều khiển 3 charts (revenue/top-products/signups), pie KHÔNG range.
 * - D-07: layout dọc — KPI row → dropdown → 2x2 charts grid → low-stock full-width.
 * - D-14: Promise.allSettled per-chart, ChartCard 3-state wrapper.
 */
export default function AdminDashboard() {
  const [productCard, setProductCard] = useState<KpiCardState<ProductStats>>({ status: 'loading' });
  const [orderCard, setOrderCard] = useState<KpiCardState<OrderStats>>({ status: 'loading' });
  const [userCard, setUserCard] = useState<KpiCardState<UserStats>>({ status: 'loading' });

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

  // === Phase 19 Plan 19-04: charts state ===
  const [range, setRange] = useState<Range>('30d'); // D-06 default
  const [revenueCard, setRevenueCard] = useState<CardState<RevenuePoint[]>>({ status: 'loading' });
  const [topProductsCard, setTopProductsCard] = useState<CardState<TopProductPoint[]>>({
    status: 'loading',
  });
  const [statusCard, setStatusCard] = useState<CardState<StatusPoint[]>>({ status: 'loading' });
  const [signupsCard, setSignupsCard] = useState<CardState<SignupPoint[]>>({ status: 'loading' });
  const [lowStockCard, setLowStockCard] = useState<CardState<LowStockItem[]>>({
    status: 'loading',
  });

  const loadRevenue = useCallback(async () => {
    setRevenueCard({ status: 'loading' });
    try {
      setRevenueCard({ status: 'success', data: await fetchRevenueChart(range) });
    } catch (e) {
      setRevenueCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, [range]); // D-06 + Pitfall #5: chỉ depend [range]

  const loadTopProducts = useCallback(async () => {
    setTopProductsCard({ status: 'loading' });
    try {
      setTopProductsCard({ status: 'success', data: await fetchTopProducts(range) });
    } catch (e) {
      setTopProductsCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, [range]);

  const loadStatus = useCallback(async () => {
    setStatusCard({ status: 'loading' });
    try {
      setStatusCard({ status: 'success', data: await fetchStatusDistrib() });
    } catch (e) {
      setStatusCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, []); // D-06: pie KHÔNG bị range

  const loadSignups = useCallback(async () => {
    setSignupsCard({ status: 'loading' });
    try {
      setSignupsCard({ status: 'success', data: await fetchUserSignups(range) });
    } catch (e) {
      setSignupsCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, [range]);

  const loadLowStock = useCallback(async () => {
    setLowStockCard({ status: 'loading' });
    try {
      setLowStockCard({ status: 'success', data: await fetchLowStock() });
    } catch (e) {
      setLowStockCard({ status: 'error', error: (e as Error).message ?? 'Không tải được' });
    }
  }, []); // low-stock không phụ thuộc range

  useEffect(() => {
    Promise.allSettled([
      loadRevenue(),
      loadTopProducts(),
      loadStatus(),
      loadSignups(),
      loadLowStock(),
    ]);
  }, [loadRevenue, loadTopProducts, loadStatus, loadSignups, loadLowStock]);

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

      {/* D-06 + D-07: time-window dropdown */}
      <div className={styles.timeWindowRow}>
        <label htmlFor="time-window">Khoảng thời gian:</label>
        <select
          id="time-window"
          value={range}
          onChange={(e) => setRange(e.target.value as Range)}
        >
          <option value="7d">7 ngày</option>
          <option value="30d">30 ngày</option>
          <option value="90d">90 ngày</option>
          <option value="all">Tất cả</option>
        </select>
      </div>

      {/* D-07: 2x2 charts grid */}
      <div className={styles.chartsGrid}>
        <ChartCard
          title="Doanh thu"
          state={revenueCard}
          renderChart={(d) => <RevenueChart data={d} />}
          onRetry={loadRevenue}
        />
        <ChartCard
          title="Sản phẩm bán chạy"
          state={topProductsCard}
          renderChart={(d) => <TopProductsChart data={d} />}
          onRetry={loadTopProducts}
        />
        <ChartCard
          title="Phân phối trạng thái"
          state={statusCard}
          renderChart={(d) => <StatusDistributionChart data={d} />}
          onRetry={loadStatus}
        />
        <ChartCard
          title="Khách hàng đăng ký"
          state={signupsCard}
          renderChart={(d) => <UserSignupsChart data={d} />}
          onRetry={loadSignups}
        />
      </div>

      {/* D-07: low-stock full-width cuối */}
      <ChartCard
        title="Sản phẩm sắp hết hàng"
        state={lowStockCard}
        renderChart={(d) => <LowStockSection data={d} />}
        onRetry={loadLowStock}
      />
    </div>
  );
}

interface KpiCardProps<T> {
  label: string;
  icon: string;
  color: string;
  state: KpiCardState<T>;
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
