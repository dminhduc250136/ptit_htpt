'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import { mockProducts } from '@/mock-data/products';
import { formatPrice } from '@/services/api';

export default function CheckoutPage() {
  const cartItems = [
    { product: mockProducts[0], quantity: 1 },
    { product: mockProducts[1], quantity: 2 },
  ];

  const [form, setForm] = useState({
    fullName: 'Nguyễn Văn A',
    phone: '0912 345 678',
    email: 'nguyenvana@email.com',
    street: '123 Nguyễn Huệ',
    ward: 'Phường Bến Nghé',
    district: 'Quận 1',
    city: 'TP. Hồ Chí Minh',
    note: '',
    paymentMethod: 'COD' as 'COD' | 'BANK_TRANSFER' | 'E_WALLET',
  });
  const [showSuccess, setShowSuccess] = useState(false);
  const [loading, setLoading] = useState(false);

  const update = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }));
  const subtotal = cartItems.reduce((s, i) => s + i.product.price * i.quantity, 0);
  const shippingFee = subtotal >= 1000000 ? 0 : 30000;
  const total = subtotal + shippingFee;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    await new Promise(r => setTimeout(r, 1500));
    setLoading(false);
    setShowSuccess(true);
  };

  // --- Order Success Modal ---
  if (showSuccess) {
    return (
      <div className={styles.page}>
        <div className={styles.successOverlay}>
          <div className={styles.successModal}>
            <div className={styles.successIcon}>
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="#16a34a" strokeWidth="2">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
            </div>
            <h2 className={styles.successTitle}>Đặt hàng thành công!</h2>
            <p className={styles.successDesc}>
              Mã đơn hàng: <strong>DA-20241108-003</strong><br />
              Cảm ơn bạn đã mua sắm tại The Digital Atélier.
            </p>
            <div className={styles.successActions}>
              <Button href="/profile">Xem đơn hàng</Button>
              <Button href="/products" variant="secondary">Tiếp tục mua sắm</Button>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Thanh toán</h1>
          <p className={styles.pageSubtitle}>Hoàn tất đơn hàng của bạn</p>
        </div>
      </div>

      <form className={`${styles.container} ${styles.content}`} onSubmit={handleSubmit}>
        <div className={styles.layout}>
          {/* Left Column — Form */}
          <div className={styles.formColumn}>
            {/* Shipping Info */}
            <div className={styles.formSection}>
              <h3 className={styles.formSectionTitle}>Thông tin giao hàng</h3>
              <div className={styles.formGrid}>
                <Input label="Họ và tên" value={form.fullName} onChange={e => update('fullName', e.target.value)} fullWidth />
                <Input label="Số điện thoại" value={form.phone} onChange={e => update('phone', e.target.value)} fullWidth />
                <Input label="Email" type="email" value={form.email} onChange={e => update('email', e.target.value)} fullWidth />
                <Input label="Địa chỉ" value={form.street} onChange={e => update('street', e.target.value)} fullWidth />
                <Input label="Phường/Xã" value={form.ward} onChange={e => update('ward', e.target.value)} fullWidth />
                <Input label="Quận/Huyện" value={form.district} onChange={e => update('district', e.target.value)} fullWidth />
                <Input label="Tỉnh/Thành phố" value={form.city} onChange={e => update('city', e.target.value)} fullWidth />
              </div>
              <div style={{ marginTop: 'var(--space-3)' }}>
                <Input label="Ghi chú" placeholder="Ghi chú cho đơn hàng (không bắt buộc)" value={form.note} onChange={e => update('note', e.target.value)} fullWidth helperText="Ví dụ: giao giờ hành chính, gọi trước khi giao" />
              </div>
            </div>

            {/* Payment Method */}
            <div className={styles.formSection}>
              <h3 className={styles.formSectionTitle}>Phương thức thanh toán</h3>
              <div className={styles.paymentOptions}>
                {([
                  { value: 'COD', label: 'Thanh toán khi nhận hàng (COD)', icon: '💵' },
                  { value: 'BANK_TRANSFER', label: 'Chuyển khoản ngân hàng', icon: '🏦' },
                  { value: 'E_WALLET', label: 'Ví điện tử (MoMo, ZaloPay)', icon: '📱' },
                ] as const).map(m => (
                  <label key={m.value} className={`${styles.paymentOption} ${form.paymentMethod === m.value ? styles.paymentActive : ''}`}>
                    <input type="radio" name="payment" value={m.value} checked={form.paymentMethod === m.value} onChange={() => update('paymentMethod', m.value)} />
                    <span className={styles.paymentIcon}>{m.icon}</span>
                    <span>{m.label}</span>
                  </label>
                ))}
              </div>
            </div>
          </div>

          {/* Right Column — Summary */}
          <aside className={styles.summaryColumn}>
            <div className={styles.summary}>
              <h3 className={styles.summaryTitle}>Đơn hàng của bạn</h3>
              <div className={styles.summaryItems}>
                {cartItems.map(item => (
                  <div key={item.product.id} className={styles.summaryItem}>
                    <div className={styles.summaryItemImg}>
                      <Image src={item.product.thumbnailUrl} alt={item.product.name} fill sizes="60px" style={{ objectFit: 'cover' }} />
                    </div>
                    <div className={styles.summaryItemInfo}>
                      <span className={styles.summaryItemName}>{item.product.name}</span>
                      <span className={styles.summaryItemQty}>x{item.quantity}</span>
                    </div>
                    <span className={styles.summaryItemPrice}>{formatPrice(item.product.price * item.quantity)}</span>
                  </div>
                ))}
              </div>
              <div className={styles.summaryRows}>
                <div className={styles.summaryRow}><span>Tạm tính</span><span>{formatPrice(subtotal)}</span></div>
                <div className={styles.summaryRow}><span>Phí vận chuyển</span><span className={shippingFee === 0 ? styles.free : ''}>{shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}</span></div>
              </div>
              <div className={styles.totalRow}>
                <span>Tổng cộng</span><span className={styles.totalPrice}>{formatPrice(total)}</span>
              </div>
              <Button type="submit" size="lg" fullWidth loading={loading}>Đặt hàng</Button>
              <Link href="/cart" className={styles.backLink}>← Quay lại giỏ hàng</Link>
            </div>
          </aside>
        </div>
      </form>
    </div>
  );
}
