'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { formatPrice } from '@/services/api';
import { getOrderById } from '@/services/orders';
import { isApiError } from '@/services/errors';
import { statusMap, paymentMethodMap, paymentStatusMap } from '@/lib/orderLabels';
import { useEnrichedItems } from '@/lib/useEnrichedItems';
import type { Order } from '@/types';

export default function OrderDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const [order, setOrder] = useState<Order | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  const enriched = useEnrichedItems(order?.items);

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const data = await getOrderById(id);
      setOrder(data);
    } catch (err) {
      if (isApiError(err) && err.code === 'NOT_FOUND') {
        setOrder(null);
        setFailed(false); // 404 → empty state, không phải error
      } else {
        setFailed(true);
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  // Loading state: skeleton theo UI-SPEC §Loading States
  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.container} style={{ padding: 'var(--space-5) 0' }}>
          <div className={styles.skeletonRow} style={{ width: '200px', marginBottom: 'var(--space-4)' }} />
          <div className={styles.skeletonRow} />
          <div className={styles.skeletonRow} />
          <div className={styles.skeletonRow} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-4)', marginTop: 'var(--space-4)' }}>
            <div className={styles.skeletonCard} />
            <div className={styles.skeletonCard} />
          </div>
        </div>
      </div>
    );
  }

  // Error state: RetrySection component
  if (failed) {
    return (
      <div className={styles.page}>
        <div className={styles.container} style={{ padding: 'var(--space-5) 0' }}>
          <RetrySection onRetry={load} loading={loading} />
        </div>
      </div>
    );
  }

  // Empty / Not found state (UI-SPEC §Empty States §Order detail)
  if (!order) {
    return (
      <div className={styles.notFound}>
        <h2>Không tìm thấy đơn hàng</h2>
        <p style={{ color: 'var(--on-surface-variant)', marginBottom: 'var(--space-4)' }}>
          Mã đơn #{id} không tồn tại hoặc bạn không có quyền xem.
        </p>
        <Button href="/profile">Xem danh sách đơn hàng</Button>
      </div>
    );
  }

  // Normalize backend fields → UI fields (T-08-03-03/04: null-safe)
  const orderStatus = order.orderStatus ?? order.status ?? 'PENDING';
  const totalAmount = order.totalAmount ?? order.total ?? 0;
  const shippingFee = order.shippingFee ?? 0;
  const subtotal = order.subtotal ?? (totalAmount - shippingFee);
  const orderCode = order.orderCode ?? order.id;
  const addr = order.shippingAddress;

  const steps = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED'];
  const currentStep = steps.indexOf(orderStatus);

  return (
    <div className={styles.page}>
      {/* Page Header */}
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <Link href="/profile" className={styles.backBtn}>← Quay lại</Link>
          <div className={styles.headerRow}>
            <div>
              <h1 className={styles.pageTitle}>Đơn hàng #{orderCode}</h1>
              <p className={styles.pageSubtitle}>
                Đặt ngày {new Date(order.createdAt).toLocaleDateString('vi-VN')}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {/* Order Status Tracker */}
        {!['CANCELLED', 'RETURNED'].includes(orderStatus) && (
          <div className={styles.tracker}>
            {steps.map((step, i) => (
              <div key={step} className={`${styles.trackerStep} ${i <= currentStep ? styles.trackerActive : ''}`}>
                <div className={styles.trackerDot}>{i <= currentStep ? '✓' : i + 1}</div>
                <span className={styles.trackerLabel}>{statusMap[step]?.label}</span>
                {i < steps.length - 1 && (
                  <div className={`${styles.trackerLine} ${i < currentStep ? styles.trackerLineActive : ''}`} />
                )}
              </div>
            ))}
          </div>
        )}

        <div className={styles.grid}>
          {/* Left: Items + Price Breakdown */}
          <div className={styles.section}>
            <h3 className={styles.sectionTitle}>Sản phẩm</h3>

            {/* Items Table — UI-SPEC §Order Items Table Layout */}
            {enriched.length === 0 ? (
              <p style={{ color: 'var(--on-surface-variant)' }}>Đơn hàng không có sản phẩm</p>
            ) : (
              <table className={styles.itemsTable}>
                <thead>
                  <tr className={styles.tableHeader}>
                    <th className={styles.tableHeaderCell}>Sản phẩm</th>
                    <th className={styles.tableHeaderCell}>Số lượng</th>
                    <th className={styles.tableHeaderCell}>Đơn giá</th>
                    <th className={styles.tableHeaderCell}>Thành tiền</th>
                  </tr>
                </thead>
                <tbody>
                  {enriched.map((item) => {
                    const lineTotal = item.lineTotal ?? item.subtotal ?? 0;
                    const unitPrice = item.unitPrice ?? item.price ?? 0;
                    return (
                      <tr key={item.id} className={styles.tableRow}>
                        <td className={styles.tableCell}>
                          <div className={styles.itemCellInner}>
                            {item.thumbnailUrl ? (
                              <Image
                                src={item.thumbnailUrl}
                                width={64}
                                height={64}
                                alt={item.productName}
                                className={styles.itemThumb}
                              />
                            ) : (
                              <div className={styles.itemThumbPlaceholder} aria-hidden="true">📦</div>
                            )}
                            <div>
                              <div className={styles.itemName}>{item.productName}</div>
                              <div className={styles.itemBrand}>{item.brand ?? '—'}</div>
                            </div>
                          </div>
                        </td>
                        <td className={styles.tableCell}>{item.quantity}</td>
                        <td className={styles.tableCell}>{formatPrice(unitPrice)}</td>
                        <td className={`${styles.tableCell} ${styles.lineTotalCell}`}>
                          {formatPrice(lineTotal)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}

            {/* Price Breakdown */}
            <div className={styles.priceBreakdown}>
              <div className={styles.priceRow}>
                <span>Tạm tính</span>
                <span>{formatPrice(subtotal)}</span>
              </div>
              <div className={styles.priceRow}>
                <span>Phí vận chuyển</span>
                <span>{shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}</span>
              </div>
              {(order.discount ?? 0) > 0 && (
                <div className={styles.priceRow}>
                  <span>Giảm giá</span>
                  <span>-{formatPrice(order.discount!)}</span>
                </div>
              )}
              {/* Phase 20 / COUP-05 (D-23): coupon snapshot block — chỉ render khi có coupon */}
              {order.couponCode && (
                <>
                  <div className={styles.priceRow}>
                    <span>Mã giảm giá</span>
                    <span><strong>{order.couponCode}</strong></span>
                  </div>
                  <div className={styles.priceRow} style={{ color: 'var(--success, #10b981)' }}>
                    <span>Giảm giá</span>
                    <span>-{formatPrice(order.discountAmount ?? 0)}</span>
                  </div>
                </>
              )}
              <div className={styles.totalRow}>
                <span>Tổng cộng</span>
                <span className={styles.totalPrice}>{formatPrice(totalAmount)}</span>
              </div>
            </div>
          </div>

          {/* Right: Info Cards */}
          <div className={styles.infoColumn}>
            {addr && (
              <div className={styles.infoCard}>
                <h4 className={styles.infoCardTitle}>Địa chỉ giao hàng</h4>
                <p>{[addr.street, addr.ward, addr.district, addr.city].filter(Boolean).join(', ')}</p>
              </div>
            )}
            <div className={styles.infoCard}>
              <h4 className={styles.infoCardTitle}>Thanh toán</h4>
              <p>{paymentMethodMap[order.paymentMethod] ?? order.paymentMethod}</p>
              {order.paymentStatus && (
                <p className={styles.paymentStatus}>{paymentStatusMap[order.paymentStatus]}</p>
              )}
            </div>
            {order.note && (
              <div className={styles.infoCard}>
                <h4 className={styles.infoCardTitle}>Ghi chú</h4>
                <p>{order.note}</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
