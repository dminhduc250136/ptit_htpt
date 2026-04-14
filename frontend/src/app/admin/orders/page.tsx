'use client';

import React, { useState } from 'react';
import styles from '../products/page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import Input from '@/components/ui/Input/Input';
import { mockOrders, mockUsers } from '@/mock-data/orders';
import { formatPrice } from '@/services/api';
import type { Order } from '@/types';

const statusOptions = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'] as const;
const statusLabels: Record<string, string> = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', SHIPPING: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy' };
const statusVariants: Record<string, 'default' | 'new' | 'hot' | 'sale' | 'outOfStock'> = { PENDING: 'default', CONFIRMED: 'new', SHIPPING: 'hot', DELIVERED: 'sale', CANCELLED: 'outOfStock' };

export default function AdminOrdersPage() {
  const [orders, setOrders] = useState(mockOrders);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [newStatus, setNewStatus] = useState('');

  const handleUpdateStatus = () => {
    if (selectedOrder && newStatus) {
      setOrders(prev => prev.map(o => o.id === selectedOrder.id ? { ...o, orderStatus: newStatus as Order['orderStatus'] } : o));
      setSelectedOrder(null);
    }
  };

  const getUserName = (userId: string) => mockUsers.find(u => u.id === userId)?.fullName || userId;

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý đơn hàng</h1>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead><tr><th>Mã đơn</th><th>Khách hàng</th><th>Sản phẩm</th><th>Tổng tiền</th><th>Trạng thái</th><th>Ngày đặt</th><th>Thao tác</th></tr></thead>
          <tbody>
            {orders.map(o => (
              <tr key={o.id}>
                <td className={styles.tdBold}>{o.orderCode}</td>
                <td>{getUserName(o.userId)}</td>
                <td>{o.items.length} sản phẩm</td>
                <td className={styles.price}>{formatPrice(o.totalAmount)}</td>
                <td><Badge variant={statusVariants[o.orderStatus] || 'default'}>{statusLabels[o.orderStatus]}</Badge></td>
                <td className={styles.tdMuted}>{new Date(o.createdAt).toLocaleDateString('vi-VN')}</td>
                <td>
                  <button className={styles.actionBtn} onClick={() => { setSelectedOrder(o); setNewStatus(o.orderStatus); }}>📋</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Modal: Order Detail & Status Update */}
      {selectedOrder && (
        <div className={styles.overlay} onClick={() => setSelectedOrder(null)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>Chi tiết đơn hàng {selectedOrder.orderCode}</h3>
              <button className={styles.closeBtn} onClick={() => setSelectedOrder(null)}>✕</button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
              {/* Items */}
              <div>
                <h4 style={{ fontSize: 'var(--text-title-sm)', fontWeight: 600, marginBottom: 'var(--space-2)' }}>Sản phẩm</h4>
                {selectedOrder.items.map(item => (
                  <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-2) 0', fontSize: 'var(--text-body-md)' }}>
                    <span>{item.productName} x{item.quantity}</span>
                    <span style={{ fontWeight: 600 }}>{formatPrice(item.subtotal)}</span>
                  </div>
                ))}
                <div style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-3) 0 0', borderTop: '1px solid rgba(195,198,214,0.15)', fontWeight: 700, fontSize: 'var(--text-title-sm)', color: 'var(--primary)' }}>
                  <span>Tổng cộng</span>
                  <span>{formatPrice(selectedOrder.totalAmount)}</span>
                </div>
              </div>

              {/* Info */}
              <div style={{ fontSize: 'var(--text-body-md)', color: 'var(--on-surface-variant)' }}>
                <p><strong>Khách hàng:</strong> {getUserName(selectedOrder.userId)}</p>
                <p><strong>Địa chỉ:</strong> {selectedOrder.shippingAddress.street}, {selectedOrder.shippingAddress.district}, {selectedOrder.shippingAddress.city}</p>
                <p><strong>Thanh toán:</strong> {selectedOrder.paymentMethod} — {selectedOrder.paymentStatus}</p>
                {selectedOrder.note && <p><strong>Ghi chú:</strong> {selectedOrder.note}</p>}
              </div>

              {/* Status Update */}
              <div>
                <h4 style={{ fontSize: 'var(--text-title-sm)', fontWeight: 600, marginBottom: 'var(--space-2)' }}>Cập nhật trạng thái</h4>
                <select
                  value={newStatus}
                  onChange={e => setNewStatus(e.target.value)}
                  style={{ width: '100%', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', fontSize: 'var(--text-body-md)', fontFamily: 'var(--font-family-body)', background: 'var(--surface-container-lowest)', cursor: 'pointer' }}
                >
                  {statusOptions.map(s => <option key={s} value={s}>{statusLabels[s]}</option>)}
                </select>
              </div>

              <div className={styles.modalActions}>
                <Button variant="secondary" onClick={() => setSelectedOrder(null)}>Đóng</Button>
                <Button onClick={handleUpdateStatus}>Cập nhật</Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
