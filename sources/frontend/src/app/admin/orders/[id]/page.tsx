'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';
import { useParams, useRouter } from 'next/navigation';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { getAdminOrderById, updateOrderState } from '@/services/orders';
import type { Order } from '@/types';
import { paymentMethodMap, statusMap } from '@/lib/orderLabels';
import { useEnrichedItems } from '@/lib/useEnrichedItems';
import SuggestReplyModal from '@/components/chat/SuggestReplyModal/SuggestReplyModal';
import { fetchSuggestReply } from '@/services/admin-chat';

const STATUS_OPTIONS = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'];

export default function AdminOrderDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;
  const router = useRouter();
  const { showToast } = useToast();

  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [newStatus, setNewStatus] = useState('');
  const [saving, setSaving] = useState(false);
  const [suggestOpen, setSuggestOpen] = useState(false);
  const [suggestLoading, setSuggestLoading] = useState(false);
  const [suggestText, setSuggestText] = useState('');
  const [suggestError, setSuggestError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setFailed(false);
    try {
      const data = await getAdminOrderById(id);
      setOrder(data);
      setNewStatus(data.status ?? data.orderStatus ?? 'PENDING');
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const currentStatus = order?.status ?? order?.orderStatus ?? 'PENDING';

  const handleUpdateStatus = async () => {
    if (!order || newStatus === currentStatus) return;
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

  const handleSuggestReply = async () => {
    if (!order) return;
    setSuggestOpen(true);
    setSuggestLoading(true);
    setSuggestError(null);
    setSuggestText('');
    try {
      const data = await fetchSuggestReply(order.id);
      setSuggestText(data.text);
    } catch (e) {
      setSuggestError(e instanceof Error ? e.message : 'Không thể sinh gợi ý');
    } finally {
      setSuggestLoading(false);
    }
  };

  const handleCopySuggestion = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      showToast('Đã sao chép gợi ý vào clipboard', 'success');
    } catch {
      showToast('Trình duyệt không cho phép sao chép tự động — chọn và Ctrl+C thủ công', 'error');
    }
  };

  const enriched = useEnrichedItems(order?.items);

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
          <p style={labelStyle}>Trạng thái: <Badge variant={statusMap[currentStatus]?.variant ?? 'default'}>{statusMap[currentStatus]?.label ?? currentStatus}</Badge></p>
        </div>

        {/* Right: Shipping info */}
        <div style={cardStyle}>
          <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Thông tin giao hàng</h3>
          <p style={labelStyle}>
            Địa chỉ:{' '}
            <strong>
              {order.shippingAddress
                ? [order.shippingAddress.street, order.shippingAddress.ward,
                   order.shippingAddress.district, order.shippingAddress.city]
                    .filter(Boolean).join(', ')
                : '—'}
            </strong>
          </p>
          <p style={labelStyle}>
            Thanh toán:{' '}
            <strong>{paymentMethodMap[order.paymentMethod] ?? order.paymentMethod ?? '—'}</strong>
          </p>
          {order.note && <p style={labelStyle}>Ghi chú: <strong>{order.note}</strong></p>}
        </div>
      </div>

      {/* Line items card */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Sản phẩm</h3>
        {enriched.length === 0 ? (
          <p style={{ color: 'var(--on-surface-variant)' }}>Đơn hàng không có sản phẩm</p>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left',   padding: 'var(--space-2)' }}>Sản phẩm</th>
                <th style={{ textAlign: 'center', padding: 'var(--space-2)' }}>Số lượng</th>
                <th style={{ textAlign: 'right',  padding: 'var(--space-2)' }}>Đơn giá</th>
                <th style={{ textAlign: 'right',  padding: 'var(--space-2)' }}>Thành tiền</th>
              </tr>
            </thead>
            <tbody>
              {enriched.map((it) => {
                const unitPrice = it.unitPrice ?? it.price ?? 0;
                const lineTotal = it.lineTotal ?? it.subtotal ?? 0;
                return (
                  <tr key={it.id} style={{ borderTop: '1px solid rgba(195,198,214,0.15)' }}>
                    <td style={{ padding: 'var(--space-2)' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
                        {it.thumbnailUrl ? (
                          <Image
                            src={it.thumbnailUrl}
                            width={64}
                            height={64}
                            alt={it.productName}
                            style={{ borderRadius: 'var(--radius-md)', objectFit: 'cover', flexShrink: 0 }}
                          />
                        ) : (
                          <div
                            style={{
                              width: 64, height: 64,
                              borderRadius: 'var(--radius-md)',
                              background: 'var(--surface-container-high)',
                              display: 'flex', alignItems: 'center', justifyContent: 'center',
                              color: 'var(--on-surface-variant)',
                              flexShrink: 0,
                            }}
                          >
                            📦
                          </div>
                        )}
                        <div>
                          <div style={{ fontWeight: 500 }}>{it.productName}</div>
                          <div style={{ fontSize: 'var(--text-body-sm)', color: 'var(--on-surface-variant)' }}>
                            {it.brand ?? '—'}
                          </div>
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
        <div style={{ borderTop: '1px solid rgba(195,198,214,0.15)', marginTop: 'var(--space-3)', paddingTop: 'var(--space-3)', textAlign: 'right' }}>
          <span style={{ fontWeight: 700, fontSize: 'var(--text-title-sm)' }}>Tổng cộng: </span>
          <span style={{ color: 'var(--primary)', fontWeight: 700 }}>
            {(order.totalAmount ?? order.total ?? 0).toLocaleString('vi-VN')}₫
          </span>
        </div>
      </div>

      {/* AI suggest reply card */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>
          Phản hồi khách hàng
        </h3>
        <p style={{ color: 'var(--on-surface-variant)', marginBottom: 'var(--space-3)', fontSize: 'var(--text-body-sm)' }}>
          AI sẽ đề xuất nội dung phản hồi khách dựa trên thông tin đơn hàng. Bạn cần kiểm tra và gửi thủ công.
        </p>
        <Button
          onClick={handleSuggestReply}
          disabled={suggestLoading}
          loading={suggestLoading}
          data-testid="suggest-reply-button"
        >
          AI gợi ý phản hồi
        </Button>
      </div>

      {/* Status update card */}
      <div style={cardStyle}>
        <h3 style={{ fontSize: 'var(--text-title-lg)', fontWeight: 700, marginBottom: 'var(--space-3)' }}>Cập nhật trạng thái</h3>
        <select
          value={newStatus}
          onChange={e => setNewStatus(e.target.value)}
          style={{ width: '100%', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', fontSize: 'var(--text-body-md)', fontFamily: 'var(--font-family-body)', background: 'var(--surface-container-lowest)', cursor: 'pointer', marginBottom: 'var(--space-3)' }}
        >
          {STATUS_OPTIONS.map(s => <option key={s} value={s}>{statusMap[s]?.label ?? s}</option>)}
        </select>
        <Button
          onClick={handleUpdateStatus}
          disabled={newStatus === currentStatus}
          loading={saving}
        >
          Cập nhật trạng thái
        </Button>
      </div>

      <SuggestReplyModal
        open={suggestOpen}
        onClose={() => setSuggestOpen(false)}
        loading={suggestLoading}
        initialText={suggestText}
        error={suggestError}
        onCopy={handleCopySuggestion}
      />
    </div>
  );
}
