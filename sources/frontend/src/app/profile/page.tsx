'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import Input from '@/components/ui/Input/Input';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { listMyOrders } from '@/services/orders';
import { useAuth } from '@/providers/AuthProvider';
import { formatPrice } from '@/services/api';
import type { Order } from '@/types';

type Tab = 'profile' | 'orders' | 'addresses';

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

export default function ProfilePage() {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState<Tab>('profile');
  const [showChangePassword, setShowChangePassword] = useState(false);
  const [pwForm, setPwForm] = useState({ current: '', newPw: '', confirm: '' });

  const [orders, setOrders] = useState<Order[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersFailed, setOrdersFailed] = useState(false);

  const loadOrders = useCallback(async () => {
    setOrdersLoading(true);
    setOrdersFailed(false);
    try {
      const resp = await listMyOrders({ page: 0, size: 10, sort: 'createdAt,desc' });
      setOrders(resp?.content ?? []);
    } catch {
      setOrdersFailed(true);
      setOrders([]);
    } finally {
      setOrdersLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOrders();
  }, [loadOrders]);

  const handleChangePw = async (e: React.FormEvent) => {
    e.preventDefault();
    if (pwForm.newPw !== pwForm.confirm) {
      showToast('Mật khẩu không khớp', 'error');
      return;
    }
    // Backend /auth/* not shipped yet (see 04-WAVE-STATUS.md) — mock success path.
    await new Promise((r) => setTimeout(r, 600));
    showToast('Đổi mật khẩu thành công (mock)', 'success');
    setShowChangePassword(false);
    setPwForm({ current: '', newPw: '', confirm: '' });
  };

  const displayName = user?.name || 'Khách';
  const displayEmail = user?.email || '—';
  const initial = (user?.name || user?.email || '?').charAt(0).toUpperCase();

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Tài khoản của tôi</h1>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        <div className={styles.layout}>
          {/* Sidebar */}
          <aside className={styles.sidebar}>
            <div className={styles.userCard}>
              <div className={styles.avatar}>{initial}</div>
              <div>
                <p className={styles.userName}>{displayName}</p>
                <p className={styles.userEmail}>{displayEmail}</p>
              </div>
            </div>
            <nav className={styles.sideNav}>
              <button className={`${styles.navItem} ${activeTab === 'profile' ? styles.navActive : ''}`} onClick={() => setActiveTab('profile')}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                Thông tin cá nhân
              </button>
              <button className={`${styles.navItem} ${activeTab === 'orders' ? styles.navActive : ''}`} onClick={() => setActiveTab('orders')}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/></svg>
                Đơn hàng ({orders.length})
              </button>
              <button className={`${styles.navItem} ${activeTab === 'addresses' ? styles.navActive : ''}`} onClick={() => setActiveTab('addresses')}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
                Địa chỉ
              </button>
            </nav>
          </aside>

          {/* Main Content */}
          <main className={styles.main}>
            {activeTab === 'profile' && (
              <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Thông tin cá nhân</h2>
                <div className={styles.profileGrid}>
                  <div className={styles.infoRow}><span className={styles.infoLabel}>Họ tên</span><span className={styles.infoValue}>{displayName}</span></div>
                  <div className={styles.infoRow}><span className={styles.infoLabel}>Email</span><span className={styles.infoValue}>{displayEmail}</span></div>
                </div>
                <div className={styles.profileActions}>
                  <Button variant="secondary">Chỉnh sửa</Button>
                  <Button variant="tertiary" onClick={() => setShowChangePassword(true)}>Đổi mật khẩu</Button>
                </div>
              </div>
            )}

            {activeTab === 'orders' && (
              <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Lịch sử đơn hàng</h2>
                {ordersLoading ? (
                  <div className="skeleton" style={{ height: 120, borderRadius: 'var(--radius-lg)' }} />
                ) : ordersFailed ? (
                  <RetrySection onRetry={() => loadOrders()} loading={ordersLoading} />
                ) : orders.length === 0 ? (
                  <p className={styles.emptyText}>Bạn chưa có đơn hàng nào.</p>
                ) : (
                  <div className={styles.orderList}>
                    {orders.map((order) => (
                      <Link key={order.id} href={`/profile/orders/${order.id}`} className={styles.orderCard}>
                        <div className={styles.orderHeader}>
                          <span className={styles.orderCode}>{order.orderCode}</span>
                          <Badge variant={statusMap[order.orderStatus ?? '']?.variant || 'default'}>
                            {statusMap[order.orderStatus ?? '']?.label ?? order.orderStatus}
                          </Badge>
                        </div>
                        <div className={styles.orderItems}>
                          {order.items?.slice(0, 4).map((item) => (
                            <div key={item.id} className={styles.orderItemThumb}>
                              {item.productImage ? (
                                <Image src={item.productImage} alt={item.productName} fill sizes="48px" style={{ objectFit: 'cover' }} />
                              ) : null}
                            </div>
                          ))}
                          <span className={styles.orderItemCount}>{order.items?.length ?? 0} sản phẩm</span>
                        </div>
                        <div className={styles.orderFooter}>
                          <span className={styles.orderDate}>
                            {order.createdAt ? new Date(order.createdAt).toLocaleDateString('vi-VN') : '—'}
                          </span>
                          <span className={styles.orderTotal}>{formatPrice(order.totalAmount ?? 0)}</span>
                        </div>
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            )}

            {activeTab === 'addresses' && (
              <div className={styles.section}>
                <h2 className={styles.sectionTitle}>Sổ địa chỉ</h2>
                <p className={styles.emptyText}>
                  Chưa có địa chỉ nào. Bạn có thể nhập địa chỉ khi thanh toán.
                </p>
                <Button variant="secondary" style={{ marginTop: 'var(--space-3)' }}>
                  + Thêm địa chỉ mới
                </Button>
              </div>
            )}
          </main>
        </div>
      </div>

      {/* Modal: Change Password */}
      {showChangePassword && (
        <div className={styles.modalOverlay} onClick={() => setShowChangePassword(false)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3 className={styles.modalTitle}>Đổi mật khẩu</h3>
              <button className={styles.modalClose} aria-label="Đóng" onClick={() => setShowChangePassword(false)}>✕</button>
            </div>
            <form className={styles.modalForm} onSubmit={handleChangePw}>
              <Input label="Mật khẩu hiện tại" type="password" value={pwForm.current} onChange={(e) => setPwForm((p) => ({ ...p, current: e.target.value }))} fullWidth />
              <Input label="Mật khẩu mới" type="password" value={pwForm.newPw} onChange={(e) => setPwForm((p) => ({ ...p, newPw: e.target.value }))} fullWidth />
              <Input label="Xác nhận mật khẩu mới" type="password" value={pwForm.confirm} onChange={(e) => setPwForm((p) => ({ ...p, confirm: e.target.value }))} fullWidth />
              <div className={styles.modalActions}>
                <Button variant="secondary" type="button" onClick={() => setShowChangePassword(false)}>Hủy</Button>
                <Button type="submit">Đổi mật khẩu</Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
