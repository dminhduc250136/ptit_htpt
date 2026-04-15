'use client';

import React from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import { mockOrders } from '@/mock-data/orders';
import { formatPrice } from '@/services/api';

const statusMap: Record<string, { label: string; variant: 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock' }> = {
  PENDING: { label: 'Chờ xác nhận', variant: 'default' },
  CONFIRMED: { label: 'Đã xác nhận', variant: 'new' },
  SHIPPING: { label: 'Đang giao', variant: 'hot' },
  DELIVERED: { label: 'Đã giao', variant: 'sale' },
  CANCELLED: { label: 'Đã hủy', variant: 'out-of-stock' },
};

const paymentMethodMap: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
};

const paymentStatusMap: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  REFUNDED: 'Đã hoàn tiền',
};

const steps = ['PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED'];

export default function OrderDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const order = mockOrders.find(o => o.id === id);

  if (!order) {
    return (
      <div className={styles.notFound}>
        <h2>Đơn hàng không tồn tại</h2>
        <Button href="/profile">Quay lại tài khoản</Button>
      </div>
    );
  }

  const currentStep = steps.indexOf(order.orderStatus);
  const addr = order.shippingAddress;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <Link href="/profile" className={styles.backBtn}>← Quay lại</Link>
          <div className={styles.headerRow}>
            <div>
              <h1 className={styles.pageTitle}>Đơn hàng {order.orderCode}</h1>
              <p className={styles.pageSubtitle}>Đặt ngày {new Date(order.createdAt).toLocaleDateString('vi-VN')}</p>
            </div>
            <Badge variant={statusMap[order.orderStatus]?.variant || 'default'}>
              {statusMap[order.orderStatus]?.label}
            </Badge>
          </div>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {/* Progress Tracker */}
        {!['CANCELLED', 'RETURNED'].includes(order.orderStatus) && (
          <div className={styles.tracker}>
            {steps.map((step, i) => (
              <div key={step} className={`${styles.trackerStep} ${i <= currentStep ? styles.trackerActive : ''}`}>
                <div className={styles.trackerDot}>{i <= currentStep ? '✓' : i + 1}</div>
                <span className={styles.trackerLabel}>{statusMap[step]?.label}</span>
                {i < steps.length - 1 && <div className={`${styles.trackerLine} ${i < currentStep ? styles.trackerLineActive : ''}`} />}
              </div>
            ))}
          </div>
        )}

        <div className={styles.grid}>
          {/* Order Items */}
          <div className={styles.section}>
            <h3 className={styles.sectionTitle}>Sản phẩm</h3>
            <div className={styles.itemList}>
              {order.items.map(item => (
                <div key={item.id} className={styles.item}>
                  <div className={styles.itemImg}>
                    <Image src={item.productImage} alt={item.productName} fill sizes="80px" style={{ objectFit: 'cover' }} />
                  </div>
                  <div className={styles.itemInfo}>
                    <span className={styles.itemName}>{item.productName}</span>
                    <span className={styles.itemQty}>Số lượng: {item.quantity}</span>
                  </div>
                  <span className={styles.itemPrice}>{formatPrice(item.subtotal)}</span>
                </div>
              ))}
            </div>
            <div className={styles.priceBreakdown}>
              <div className={styles.priceRow}><span>Tạm tính</span><span>{formatPrice(order.subtotal)}</span></div>
              <div className={styles.priceRow}><span>Phí vận chuyển</span><span>{order.shippingFee === 0 ? 'Miễn phí' : formatPrice(order.shippingFee)}</span></div>
              {order.discount > 0 && <div className={styles.priceRow}><span>Giảm giá</span><span>-{formatPrice(order.discount)}</span></div>}
              <div className={styles.totalRow}><span>Tổng cộng</span><span className={styles.totalPrice}>{formatPrice(order.totalAmount)}</span></div>
            </div>
          </div>

          {/* Sidebar Info */}
          <div className={styles.infoColumn}>
            <div className={styles.infoCard}>
              <h4 className={styles.infoCardTitle}>Địa chỉ giao hàng</h4>
              <p>{addr.street}, {addr.ward}, {addr.district}, {addr.city}</p>
            </div>
            <div className={styles.infoCard}>
              <h4 className={styles.infoCardTitle}>Thanh toán</h4>
              <p>{paymentMethodMap[order.paymentMethod]}</p>
              <p className={styles.paymentStatus}>{paymentStatusMap[order.paymentStatus]}</p>
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
