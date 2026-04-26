'use client';

import React, { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import Modal from '@/components/ui/Modal/Modal';
import { useToast } from '@/components/ui/Toast/Toast';
import {
  readCart,
  clearCart,
  removeFromCart,
  updateQuantity,
  type CartItem,
} from '@/services/cart';
import { createOrder } from '@/services/orders';
import { isApiError } from '@/services/errors';
import { formatPrice } from '@/services/api';
import { useAuth } from '@/providers/AuthProvider';

interface StockConflictItem {
  productId: string;
  name: string;
  availableQuantity: number;
  requestedQuantity: number;
}

export default function CheckoutPage() {
  const { showToast } = useToast();
  const { user } = useAuth();
  const router = useRouter();

  // Hydrate cart via lazy initializer (SSR-safe, avoids set-state-in-effect lint).
  const [cartItems, setCartItems] = useState<CartItem[]>(() =>
    typeof window === 'undefined' ? [] : readCart(),
  );
  const [hydrated, setHydrated] = useState<boolean>(() => typeof window !== 'undefined');

  useEffect(() => {
    const onChange = () => setCartItems(readCart());
    window.addEventListener('cart:change', onChange);
    if (!hydrated) setHydrated(true);
    return () => window.removeEventListener('cart:change', onChange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const [form, setForm] = useState({
    fullName: '',
    phone: '',
    email: '',
    street: '',
    ward: '',
    district: '',
    city: '',
    note: '',
    paymentMethod: 'COD' as 'COD' | 'BANK_TRANSFER' | 'E_WALLET',
  });
  const [loading, setLoading] = useState(false);

  // --- Error recovery state (Shared Pattern 5: error-dispatcher contract) ---
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [bannerVisible, setBannerVisible] = useState(false);
  const [stockModal, setStockModal] = useState<StockConflictItem[] | null>(null);
  const [paymentModal, setPaymentModal] = useState(false);

  const update = (k: string, v: string) => setForm((p) => ({ ...p, [k]: v }));
  const subtotal = cartItems.reduce((s, i) => s + i.price * i.quantity, 0);
  const shippingFee = subtotal >= 1000000 ? 0 : 30000;
  const total = subtotal + shippingFee;

  async function submitOrder() {
    setLoading(true);
    setBannerVisible(false);
    setFieldErrors({});
    try {
      const order = await createOrder({
        items: cartItems.map((i) => ({
          productId: i.productId,
          productName: i.name,           // D-06: cart item có field 'name' — truyền làm productName snapshot
          quantity: i.quantity,
          unitPrice: i.price,            // Phase 4-06: cart price snapshot → backend CreateOrderCommand totalAmount
        })),
        shippingAddress: {
          street: form.street,
          ward: form.ward,
          district: form.district,
          city: form.city,
        },
        paymentMethod: form.paymentMethod,
        note: form.note || undefined,
      }, user?.id);                     // Phase 4-06: userId → X-User-Id header (Phase 5: JWT-claim derivation)
      clearCart();
      router.push('/account/orders/' + order.id);
    } catch (err) {
      if (!isApiError(err)) {
        // Network / unexpected — D-10 toast. No auto-retry.
        showToast('Đã có lỗi, vui lòng thử lại', 'error');
        return;
      }
      switch (err.code) {
        case 'VALIDATION_ERROR': {
          const byField: Record<string, string> = {};
          for (const f of err.fieldErrors) byField[f.field] = f.message;
          setFieldErrors(byField);
          setBannerVisible(true);
          break;
        }
        case 'CONFLICT': {
          // Pitfall 4 / Q3: discriminate stock-shortage vs payment-failure by
          // details.domainCode === 'STOCK_SHORTAGE' OR presence of details.items[].
          const d = err.details as
            | { domainCode?: string; items?: StockConflictItem[] }
            | undefined;
          if (d?.domainCode === 'STOCK_SHORTAGE' || Array.isArray(d?.items)) {
            setStockModal((d?.items as StockConflictItem[]) ?? []);
          } else {
            // Payment-origin CONFLICT (Q4 / A5).
            setPaymentModal(true);
          }
          break;
        }
        case 'UNAUTHORIZED':
          // http.ts already redirected; no-op here.
          break;
        case 'FORBIDDEN':
          showToast('Bạn không có quyền', 'error');
          break;
        case 'NOT_FOUND':
          showToast(err.message || 'Không tìm thấy', 'error');
          break;
        default:
          showToast('Đã có lỗi, vui lòng thử lại', 'error');
      }
    } finally {
      setLoading(false);
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (cartItems.length === 0) {
      showToast('Giỏ hàng trống', 'error');
      return;
    }
    await submitOrder();
  };

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Thanh toán</h1>
          <p className={styles.pageSubtitle}>Hoàn tất đơn hàng của bạn</p>
        </div>
      </div>

      <form className={`${styles.container} ${styles.content}`} onSubmit={handleSubmit}>
        {bannerVisible && <Banner count={Object.keys(fieldErrors).length} />}

        <div className={styles.layout}>
          {/* Left Column — Form */}
          <div className={styles.formColumn}>
            {/* Shipping Info */}
            <div className={styles.formSection}>
              <h3 className={styles.formSectionTitle}>Thông tin giao hàng</h3>
              <div className={styles.formGrid}>
                <Input label="Họ và tên" value={form.fullName} onChange={(e) => update('fullName', e.target.value)} error={fieldErrors.fullName} fullWidth />
                <Input label="Số điện thoại" value={form.phone} onChange={(e) => update('phone', e.target.value)} error={fieldErrors.phone} fullWidth />
                <Input label="Email" type="email" value={form.email} onChange={(e) => update('email', e.target.value)} error={fieldErrors.email} fullWidth />
                <Input label="Địa chỉ" value={form.street} onChange={(e) => update('street', e.target.value)} error={fieldErrors.street ?? fieldErrors['shippingAddress.street']} fullWidth />
                <Input label="Phường/Xã" value={form.ward} onChange={(e) => update('ward', e.target.value)} error={fieldErrors.ward ?? fieldErrors['shippingAddress.ward']} fullWidth />
                <Input label="Quận/Huyện" value={form.district} onChange={(e) => update('district', e.target.value)} error={fieldErrors.district ?? fieldErrors['shippingAddress.district']} fullWidth />
                <Input label="Tỉnh/Thành phố" value={form.city} onChange={(e) => update('city', e.target.value)} error={fieldErrors.city ?? fieldErrors['shippingAddress.city']} fullWidth />
              </div>
              <div style={{ marginTop: 'var(--space-3)' }}>
                <Input label="Ghi chú" placeholder="Ghi chú cho đơn hàng (không bắt buộc)" value={form.note} onChange={(e) => update('note', e.target.value)} error={fieldErrors.note} fullWidth helperText="Ví dụ: giao giờ hành chính, gọi trước khi giao" />
              </div>
            </div>

            {/* Payment Method */}
            <div className={styles.formSection}>
              <h3 className={styles.formSectionTitle}>Phương thức thanh toán</h3>
              <div className={styles.paymentOptions}>
                {(
                  [
                    { value: 'COD', label: 'Thanh toán khi nhận hàng (COD)', icon: '💵' },
                    { value: 'BANK_TRANSFER', label: 'Chuyển khoản ngân hàng', icon: '🏦' },
                    { value: 'E_WALLET', label: 'Ví điện tử (MoMo, ZaloPay)', icon: '📱' },
                  ] as const
                ).map((m) => (
                  <label key={m.value} className={`${styles.paymentOption} ${form.paymentMethod === m.value ? styles.paymentActive : ''}`}>
                    <input
                      type="radio"
                      name="payment"
                      value={m.value}
                      checked={form.paymentMethod === m.value}
                      onChange={() => update('paymentMethod', m.value)}
                    />
                    <span className={styles.paymentIcon}>{m.icon}</span>
                    <span>{m.label}</span>
                  </label>
                ))}
              </div>
              {fieldErrors.paymentMethod && (
                <p style={{ color: 'var(--error)', marginTop: 'var(--space-2)', fontSize: 'var(--text-body-sm)' }}>
                  {fieldErrors.paymentMethod}
                </p>
              )}
            </div>
          </div>

          {/* Right Column — Summary */}
          <aside className={styles.summaryColumn}>
            <div className={styles.summary}>
              <h3 className={styles.summaryTitle}>Đơn hàng của bạn</h3>
              <div className={styles.summaryItems}>
                {hydrated && cartItems.length === 0 && (
                  <p style={{ color: 'var(--on-surface-variant)' }}>
                    Giỏ hàng trống. <Link href="/products">Tiếp tục mua sắm</Link>
                  </p>
                )}
                {cartItems.map((item) => (
                  <div key={item.productId} className={styles.summaryItem}>
                    <div className={styles.summaryItemImg}>
                      <Image src={item.thumbnailUrl?.trim() ? item.thumbnailUrl : '/placeholder.png'} alt={item.name} fill sizes="60px" style={{ objectFit: 'cover' }} />
                    </div>
                    <div className={styles.summaryItemInfo}>
                      <span className={styles.summaryItemName}>{item.name}</span>
                      <span className={styles.summaryItemQty}>x{item.quantity}</span>
                    </div>
                    <span className={styles.summaryItemPrice}>{formatPrice(item.price * item.quantity)}</span>
                  </div>
                ))}
              </div>
              <div className={styles.summaryRows}>
                <div className={styles.summaryRow}>
                  <span>Tạm tính</span>
                  <span>{formatPrice(subtotal)}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span>Phí vận chuyển</span>
                  <span className={shippingFee === 0 ? styles.free : ''}>{shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}</span>
                </div>
              </div>
              <div className={styles.totalRow}>
                <span>Tổng cộng</span>
                <span className={styles.totalPrice}>{formatPrice(total)}</span>
              </div>
              <Button type="submit" size="lg" fullWidth loading={loading} disabled={cartItems.length === 0}>
                Đặt hàng
              </Button>
              <Link href="/cart" className={styles.backLink}>
                ← Quay lại giỏ hàng
              </Link>
            </div>
          </aside>
        </div>
      </form>

      {/* Stock-shortage conflict modal */}
      <Modal
        open={!!stockModal}
        onClose={() => setStockModal(null)}
        title="Một số sản phẩm không đủ hàng"
        primaryAction={{
          label: 'Cập nhật số lượng',
          onClick: () => {
            stockModal?.forEach((item) => {
              updateQuantity(item.productId, item.availableQuantity);
            });
            setStockModal(null);
          },
        }}
        secondaryAction={{
          label: 'Xóa khỏi giỏ',
          variant: 'danger',
          onClick: () => {
            stockModal?.forEach((item) => {
              removeFromCart(item.productId);
            });
            setStockModal(null);
          },
        }}
      >
        <p style={{ marginBottom: 'var(--space-3)' }}>
          Vui lòng điều chỉnh giỏ hàng trước khi tiếp tục thanh toán:
        </p>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {stockModal?.map((item) => (
            <li
              key={item.productId}
              style={{
                padding: 'var(--space-2) 0',
                borderTop: '1px solid var(--outline-variant)',
              }}
            >
              <strong>{item.name}</strong> — chỉ còn {item.availableQuantity} sản phẩm (bạn đã chọn {item.requestedQuantity})
            </li>
          ))}
        </ul>
      </Modal>

      {/* Payment-failure conflict modal */}
      <Modal
        open={paymentModal}
        onClose={() => setPaymentModal(false)}
        title="Thanh toán thất bại"
        primaryAction={{
          label: 'Thử lại',
          loading,
          onClick: () => {
            setPaymentModal(false);
            submitOrder();
          },
        }}
        secondaryAction={{
          label: 'Đổi phương thức thanh toán',
          variant: 'secondary',
          onClick: () => {
            setPaymentModal(false);
            // User picks a different payment method in the form and resubmits.
          },
        }}
      >
        Giao dịch không thành công. Bạn có thể thử lại hoặc chọn phương thức thanh toán khác.
      </Modal>
    </div>
  );
}
