'use client';

import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import styles from '../../products/page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { getAdminOrderById, updateOrderState } from '@/services/orders';

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

const STATUS_OPTIONS = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'];
const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Chờ xác nhận',
  CONFIRMED: 'Đã xác nhận',
  SHIPPING: 'Đang giao',
  DELIVERED: 'Đã giao',
  CANCELLED: 'Đã hủy',
};
const STATUS_VARIANTS: Record<string, 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock'> = {
  PENDING: 'default',
  CONFIRMED: 'new',
  SHIPPING: 'hot',
  DELIVERED: 'sale',
  CANCELLED: 'out-of-stock',
};

export default function AdminOrderDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const router = useRouter();
  const { showToast } = useToast();

  const [order, setOrder] = useState<AdminOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setFailed(false);
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = await getAdminOrderById(id) as any;
      setOrder(data);
      setNewStatus(data.status);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const handleUpdateStatus = async () => {
    if (!order || newStatus === order.status) return;
    setSaving(true);
    try {
      await updateOrderState(order.id, newStatus);
      showToast('Trạng thái đơn hàng đã được cập nhật', 'success');
      await load();
    } catch {
      showToast('Không thể cập nhật trạng thái. Vui lòng thử lại', 'error');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div className="skeleton" style={{ height: 120, borderRadius: 'var(--radius-xl)' }} />
      <div className="skeleton" style={{ height: 120, borderRadius: 'var(--radius-xl)' }} />
      <div className="skeleton" style={{ height: 200, borderRadius: 'var(--radius-xl)' }} />
    </div>
  );

  if (failed) return <RetrySection onRetry={load} loading={loading} />;

  if (!order) return null;

  const cardStyle: React.CSSProperties = {
    background: 'var(--surface-container-lowest)',
    borderRadius: 'var(--radius-xl)',
    padding: 'var(--space-5)',
  };
  const labelStyle: React.CSSProperties = {
    color: 'var(--on-surface-variant)',
    fontSize: 'var(--text-body-md)',
    marginBottom: 'var(--space-2)',
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {/* Header */}
      <div>
        <button
          onClick={() => router.back()}
          style={{ color: 'var(--on-surface-variant)', fontSize: 'var(--text-body-md)', background: 'none', border: 'none', cursor: 'pointer', marginBottom: 'var(--space-2)' }}
        >
          ← Quay lại danh sách đơn hàng
        </button>
        <h1 style={{ fontSize: 'var(--text-headline-md)', fontWeight: 700 }}>
          Chi tiết đơn hàng {order.id.slice(0, 8)}
        </h1>
      </div>

      {/* Info grid — 2 cols */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)' }}>
        {/* Left: Order info */}
        <div style={cardStyle}>
          <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Thông tin đơn hàng</h3>
          <p style={labelStyle}>Mã đơn: <strong>{order.id.slice(0, 8)}</strong></p>
          <p style={labelStyle}>Khách hàng: <strong>{order.userId.slice(0, 8)}</strong></p>
          <p style={labelStyle}>Ngày đặt: <strong>{new Date(order.createdAt).toLocaleDateString('vi-VN')}</strong></p>
          <p style={labelStyle}>Trạng thái: <Badge variant={STATUS_VARIANTS[order.status] ?? 'default'}>{STATUS_LABELS[order.status] ?? order.status}</Badge></p>
        </div>

        {/* Right: Shipping info */}
        <div style={cardStyle}>
          <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Thông tin giao hàng</h3>
          <p style={labelStyle}>Địa chỉ: <strong>—</strong></p>
          <p style={labelStyle}>Thanh toán: <strong>—</strong></p>
          {order.note && <p style={labelStyle}>Ghi chú: <strong>{order.note}</strong></p>}
        </div>
      </div>

      {/* Line items card */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Sản phẩm</h3>
        <p style={{ color: 'var(--on-surface-variant)' }}>Chi tiết sản phẩm sẽ khả dụng sau khi Phase 8 hoàn thiện</p>
        <div style={{ borderTop: '1px solid rgba(195,198,214,0.15)', marginTop: 'var(--space-3)', paddingTop: 'var(--space-3)' }}>
          <span style={{ fontWeight: 700, fontSize: 'var(--text-title-sm)' }}>Tổng cộng: </span>
          <span style={{ color: 'var(--primary)', fontWeight: 700 }}>
            {(order.totalAmount ?? order.total)?.toLocaleString('vi-VN')}₫
          </span>
        </div>
      </div>

      {/* Status update card */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Cập nhật trạng thái</h3>
        <select
          value={newStatus}
          onChange={e => setNewStatus(e.target.value)}
          style={{ width: '100%', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', fontSize: 'var(--text-body-md)', fontFamily: 'var(--font-family-body)', background: 'var(--surface-container-lowest)', cursor: 'pointer', marginBottom: 'var(--space-3)' }}
        >
          {STATUS_OPTIONS.map(s => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        <Button
          onClick={handleUpdateStatus}
          disabled={newStatus === order.status}
          loading={saving}
        >
          Cập nhật trạng thái
        </Button>
      </div>
    </div>
  );
}
