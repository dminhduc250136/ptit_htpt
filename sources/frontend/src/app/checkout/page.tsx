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
  useCart,
  useUpdateCartItem,
  useRemoveCartItem,
  useClearCart,
} from '@/hooks/useCart';
import { createOrder } from '@/services/orders';
import { validateCoupon } from '@/services/coupons';
import { listAddresses } from '@/services/users';
import { isApiError } from '@/services/errors';
import { formatPrice } from '@/services/api';
import { useAuth } from '@/providers/AuthProvider';
import AddressPicker from '@/components/ui/AddressPicker/AddressPicker';
import { useApplyCoupon } from '@/hooks/useApplyCoupon';
import { formatCouponError, isCouponError } from '@/lib/couponErrorMessages';
import type { SavedAddress, CouponPreview } from '@/types';

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

  // Phase 18: fetch cart async qua React Query (cả guest localStorage + user DB)
  const { data: cartItems = [], isLoading: cartLoading } = useCart();
  const updateMutation = useUpdateCartItem();
  const removeMutation = useRemoveCartItem();
  const clearMutation = useClearCart();

  const hydrated = !cartLoading;

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

  // --- AddressPicker state (D-01, D-02, D-03, ACCT-06) ---
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([]);
  const [addressesLoading, setAddressesLoading] = useState(false);
  // pickerVisible: hiển thị AddressPicker chỉ khi fetch thành công (silent fail per D-Discretion)
  const [pickerVisible, setPickerVisible] = useState(false);

  // Fetch saved addresses khi user logged-in
  useEffect(() => {
    if (!user) return;
    let alive = true;
    setAddressesLoading(true);
    listAddresses()
      .then((addresses) => {
        if (!alive) return;
        setSavedAddresses(addresses);
        setPickerVisible(true); // chỉ show khi fetch thành công
      })
      .catch(() => {
        // Silent fail per D-Discretion: ẩn picker, không toast
        if (!alive) return;
        setPickerVisible(false);
      })
      .finally(() => {
        if (alive) setAddressesLoading(false);
      });
    return () => { alive = false; };
  }, [user]);

  const handleAddressSelect = (address: SavedAddress) => {
    setForm((prev) => ({
      ...prev,
      fullName: address.fullName,
      phone: address.phone,
      street: address.street,
      ward: address.ward,
      district: address.district,
      city: address.city,
    }));
  };

  // --- Error recovery state (Shared Pattern 5: error-dispatcher contract) ---
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [bannerVisible, setBannerVisible] = useState(false);
  const [stockModal, setStockModal] = useState<StockConflictItem[] | null>(null);
  const [paymentModal, setPaymentModal] = useState(false);

  const update = (k: string, v: string) => setForm((p) => ({ ...p, [k]: v }));
  const subtotal = cartItems.reduce((s, i) => s + i.price * i.quantity, 0);
  const shippingFee = subtotal >= 1000000 ? 0 : 30000;
  const total = subtotal + shippingFee;

  // === COUPON STATE (D-17, D-18) ===
  const [couponInput, setCouponInput] = useState('');
  const [appliedCoupon, setAppliedCoupon] = useState<CouponPreview | null>(null);
  const applyCouponMutation = useApplyCoupon(user?.id);

  const handleApplyCoupon = async () => {
    const code = couponInput.trim().toUpperCase();
    if (!code) {
      showToast('Vui lòng nhập mã giảm giá', 'error');
      return;
    }
    try {
      const preview = await applyCouponMutation.mutateAsync({ code, cartTotal: subtotal });
      setAppliedCoupon(preview);
      setCouponInput('');
      showToast('Áp dụng mã thành công', 'success');
    } catch (err) {
      if (isApiError(err) && isCouponError(err.code)) {
        const msg = formatCouponError(err.code, err.details) ?? 'Không thể áp dụng mã giảm giá';
        showToast(msg, 'error');
      } else {
        showToast('Không thể áp dụng mã giảm giá, vui lòng thử lại', 'error');
      }
    }
  };

  const handleRemoveCoupon = () => {
    setAppliedCoupon(null);
    setCouponInput('');
  };

  // D-18: Auto re-validate khi cart đổi (subtotal thay đổi) sau khi đã apply coupon.
  // Nếu fail (ví dụ subtotal mới < minOrder) → clear coupon + toast.
  useEffect(() => {
    if (!appliedCoupon || cartItems.length === 0) return;
    let alive = true;
    validateCoupon({ code: appliedCoupon.code, cartTotal: subtotal }, user?.id)
      .then((preview) => {
        if (!alive) return;
        setAppliedCoupon(preview);
      })
      .catch((err) => {
        if (!alive) return;
        setAppliedCoupon(null);
        if (isApiError(err) && isCouponError(err.code)) {
          showToast('Mã giảm giá không còn áp dụng được', 'error');
        }
      });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subtotal]);

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
        couponCode: appliedCoupon?.code,   // D-19: undefined nếu chưa apply coupon
      }, user?.id);                     // Phase 4-06: userId → X-User-Id header (Phase 5: JWT-claim derivation)

      // Phase 18: clear cart qua mutation (cả guest localStorage + user DB)
      try {
        await clearMutation.mutateAsync();
      } catch (clearErr) {
        console.error('[checkout] cart clear failed (non-blocking):', clearErr);
      }
      router.push('/profile/orders/' + order.id);
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
        default: {
          if (isCouponError(err.code)) {
            // D-19: BE atomic redeem fail (coupon vừa bị disable / race-lose / expired between preview & submit)
            const msg = formatCouponError(err.code, err.details) ?? 'Mã giảm giá không khả dụng';
            showToast(msg, 'error');
            // KHÔNG clear cart — user có thể bỏ mã + retry
            setAppliedCoupon(null);
          } else {
            showToast('Đã có lỗi, vui lòng thử lại', 'error');
          }
          break;
        }
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
              {/* D-01, D-02, D-03: AddressPicker snap-fill — chỉ show khi fetch thành công (silent fail) */}
              {pickerVisible && (
                <>
                  <AddressPicker
                    addresses={savedAddresses}
                    loading={addressesLoading}
                    onSelect={handleAddressSelect}
                  />
                  <div className={styles.pickerDivider}>— hoặc điền thủ công —</div>
                </>
              )}
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
              {/* === COUPON SECTION (D-17) === */}
              <div className={styles.couponSection}>
                <h4 className={styles.couponTitle}>Mã giảm giá</h4>
                {!appliedCoupon ? (
                  <div className={styles.couponRow}>
                    <Input
                      placeholder="Nhập mã giảm giá"
                      value={couponInput}
                      onChange={(e) => setCouponInput(e.target.value.toUpperCase())}
                      fullWidth
                    />
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={handleApplyCoupon}
                      loading={applyCouponMutation.isPending}
                    >
                      Áp dụng
                    </Button>
                  </div>
                ) : (
                  <div className={styles.couponChip}>
                    <span className={styles.couponChipCode}>{appliedCoupon.code}</span>
                    <span className={styles.couponChipDiscount}>
                      -{formatPrice(appliedCoupon.discountAmount)}
                    </span>
                    <button
                      type="button"
                      className={styles.couponChipRemove}
                      onClick={handleRemoveCoupon}
                      aria-label="Bỏ mã giảm giá"
                    >
                      Bỏ
                    </button>
                  </div>
                )}
              </div>

              {/* === SUMMARY ROWS (D-20: 3 dòng — Tạm tính / Phí vận chuyển / Giảm giá khi có coupon) === */}
              <div className={styles.summaryRows}>
                <div className={styles.summaryRow}>
                  <span>Tạm tính</span>
                  <span>{formatPrice(subtotal)}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span>Phí vận chuyển</span>
                  <span className={shippingFee === 0 ? styles.free : ''}>{shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}</span>
                </div>
                {appliedCoupon && (
                  <div className={`${styles.summaryRow} ${styles.discountRow}`}>
                    <span>Giảm giá ({appliedCoupon.code})</span>
                    <span>-{formatPrice(appliedCoupon.discountAmount)}</span>
                  </div>
                )}
              </div>
              <div className={styles.totalRow}>
                <span>Tổng cộng</span>
                <span className={styles.totalPrice}>
                  {formatPrice(Math.max(0, total - (appliedCoupon?.discountAmount ?? 0)))}
                </span>
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
          onClick: async () => {
            if (!stockModal) return;
            await Promise.all(
              stockModal.map((item) =>
                updateMutation.mutateAsync({ productId: item.productId, qty: item.availableQuantity })
              )
            );
            setStockModal(null);
          },
        }}
        secondaryAction={{
          label: 'Xóa khỏi giỏ',
          variant: 'danger',
          onClick: async () => {
            if (!stockModal) return;
            await Promise.all(
              stockModal.map((item) => removeMutation.mutateAsync(item.productId))
            );
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
