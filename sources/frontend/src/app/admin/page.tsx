import styles from './page.module.css';
import { formatPrice } from '@/services/api';
import type { Order, Product, User } from '@/types';

// TODO Phase 7 (UI-02): wire stats to listOrders(admin scope) + listProducts(admin scope) + listUsers qua gateway
const mockOrders: Order[] = [];
const mockProducts: Product[] = [];
const mockUsers: User[] = [];

export default function AdminDashboard() {
  const totalRevenue = mockOrders.reduce((s, o) => s + (o.totalAmount ?? 0), 0);
  const pendingOrders = mockOrders.filter(o => o.orderStatus === 'PENDING').length;
  const shippingOrders = mockOrders.filter(o => o.orderStatus === 'SHIPPING').length;

  const stats = [
    { label: 'Tổng doanh thu', value: formatPrice(totalRevenue), icon: '💰', color: '#16a34a' },
    { label: 'Tổng đơn hàng', value: mockOrders.length.toString(), icon: '📦', color: 'var(--primary)' },
    { label: 'Sản phẩm', value: mockProducts.length.toString(), icon: '🏷️', color: 'var(--secondary)' },
    { label: 'Khách hàng', value: mockUsers.filter(u => u.role === 'CUSTOMER').length.toString(), icon: '👥', color: '#f59e0b' },
  ];

  return (
    <div className={styles.dashboard}>
      <h1 className={styles.title}>Dashboard</h1>

      {/* Stats Grid */}
      <div className={styles.statsGrid}>
        {stats.map(s => (
          <div key={s.label} className={styles.statCard}>
            <div className={styles.statIcon}>{s.icon}</div>
            <div>
              <p className={styles.statValue} style={{ color: s.color }}>{s.value}</p>
              <p className={styles.statLabel}>{s.label}</p>
            </div>
          </div>
        ))}
      </div>

      <div className={styles.grid2}>
        {/* Recent Orders */}
        <div className={styles.card}>
          <h3 className={styles.cardTitle}>Đơn hàng gần đây</h3>
          {mockOrders.length === 0 ? (
            <p style={{ color: 'var(--on-surface-variant)', padding: 'var(--space-4)' }}>
              Chưa có dữ liệu — Phase 7 UI-02 sẽ wire API thật.
            </p>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr><th>Mã đơn</th><th>Trạng thái</th><th>Tổng tiền</th><th>Ngày</th></tr>
              </thead>
              <tbody>
                {mockOrders.slice(0, 5).map(o => (
                  <tr key={o.id}>
                    <td className={styles.tdBold}>{o.orderCode}</td>
                    <td><span className={`${styles.statusBadge} ${styles['status_' + o.orderStatus]}`}>{o.orderStatus}</span></td>
                    <td>{formatPrice(o.totalAmount ?? 0)}</td>
                    <td className={styles.tdMuted}>{new Date(o.createdAt).toLocaleDateString('vi-VN')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Quick Stats */}
        <div className={styles.card}>
          <h3 className={styles.cardTitle}>Tổng quan nhanh</h3>
          <div className={styles.quickStats}>
            <div className={styles.quickStat}>
              <span className={styles.quickStatNum}>{pendingOrders}</span>
              <span className={styles.quickStatLabel}>Chờ xác nhận</span>
            </div>
            <div className={styles.quickStat}>
              <span className={styles.quickStatNum}>{shippingOrders}</span>
              <span className={styles.quickStatLabel}>Đang giao</span>
            </div>
            <div className={styles.quickStat}>
              <span className={styles.quickStatNum}>{mockProducts.filter(p => p.stock < 10).length}</span>
              <span className={styles.quickStatLabel}>Sắp hết hàng</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
