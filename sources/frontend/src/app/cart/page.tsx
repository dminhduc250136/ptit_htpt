'use client';

import React, { useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import {
  readCart,
  removeFromCart,
  updateQuantity,
  type CartItem,
} from '@/services/cart';
import { formatPrice } from '@/services/api';

export default function CartPage() {
  // Hydrate cart synchronously on first client render via lazy initializer.
  // `typeof window` guard keeps SSR safe (Pitfall 2). Same pattern as AuthProvider —
  // avoids the react-hooks/set-state-in-effect lint trigger.
  const [cartItems, setCartItems] = useState<CartItem[]>(() =>
    typeof window === 'undefined' ? [] : readCart(),
  );
  const [hydrated, setHydrated] = useState<boolean>(() => typeof window !== 'undefined');

  useEffect(() => {
    // Subscribe to cart:change so cross-page updates (remove/update) reflect here.
    const onChange = () => setCartItems(readCart());
    window.addEventListener('cart:change', onChange);
    // Ensure hydrated flag is true once effect runs on the client (defensive for SSR).
    if (!hydrated) setHydrated(true);
    return () => window.removeEventListener('cart:change', onChange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const subtotal = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const shippingFee = subtotal >= 1000000 ? 0 : 30000;
  const total = subtotal + shippingFee;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Giỏ hàng</h1>
          <p className={styles.pageSubtitle}>
            {hydrated ? `${cartItems.length} sản phẩm trong giỏ hàng` : 'Đang tải...'}
          </p>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {hydrated && cartItems.length === 0 ? (
          <div className={styles.emptyCart}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
            <h2>Giỏ hàng trống</h2>
            <p>Bạn chưa có sản phẩm nào trong giỏ hàng</p>
            <Button href="/products" size="lg">Tiếp tục mua sắm</Button>
          </div>
        ) : (
          <div className={styles.layout}>
            {/* Cart Items */}
            <div className={styles.cartItems}>
              {cartItems.map((item) => (
                <div key={item.productId} className={styles.cartItem}>
                  <Link href={`/products/${item.productId}`} className={styles.itemImage}>
                    <Image
                      src={item.thumbnailUrl?.trim() ? item.thumbnailUrl : '/placeholder.png'}
                      alt={item.name}
                      fill
                      sizes="120px"
                      className={styles.itemImg}
                    />
                  </Link>

                  <div className={styles.itemInfo}>
                    <div className={styles.itemTop}>
                      <div>
                        <Link href={`/products/${item.productId}`} className={styles.itemName}>
                          {item.name}
                        </Link>
                      </div>
                      <button
                        className={styles.removeBtn}
                        onClick={() => removeFromCart(item.productId)}
                        aria-label="Xóa sản phẩm"
                      >
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                          <line x1="18" y1="6" x2="6" y2="18" />
                          <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                      </button>
                    </div>

                    <div className={styles.itemBottom}>
                      <div className={styles.quantitySelector}>
                        <button
                          className={styles.qtyBtn}
                          onClick={() => updateQuantity(item.productId, item.quantity - 1)}
                          disabled={item.quantity <= 1}
                        >
                          −
                        </button>
                        <span className={styles.qtyValue}>{item.quantity}</span>
                        <button
                          className={styles.qtyBtn}
                          onClick={() => updateQuantity(item.productId, item.quantity + 1)}
                        >
                          +
                        </button>
                      </div>
                      <div className={styles.itemPrice}>
                        <span className={styles.priceTotal}>{formatPrice(item.price * item.quantity)}</span>
                        {item.quantity > 1 && (
                          <span className={styles.priceUnit}>{formatPrice(item.price)} / sản phẩm</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {/* Order Summary */}
            <aside className={styles.summary}>
              <h3 className={styles.summaryTitle}>Tóm tắt đơn hàng</h3>

              <div className={styles.summaryRows}>
                <div className={styles.summaryRow}>
                  <span>Tạm tính ({cartItems.reduce((s, i) => s + i.quantity, 0)} SP)</span>
                  <span>{formatPrice(subtotal)}</span>
                </div>
                <div className={styles.summaryRow}>
                  <span>Phí vận chuyển</span>
                  <span className={shippingFee === 0 ? styles.freeShip : ''}>
                    {shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee)}
                  </span>
                </div>
                {shippingFee > 0 && (
                  <p className={styles.shippingNote}>
                    Miễn phí vận chuyển cho đơn từ {formatPrice(1000000)}
                  </p>
                )}
              </div>

              <div className={styles.totalRow}>
                <span>Tổng cộng</span>
                <span className={styles.totalPrice}>{formatPrice(total)}</span>
              </div>

              <Button href="/checkout" size="lg" fullWidth>
                Tiến hành thanh toán
              </Button>
              <Link href="/products" className={styles.continueLink}>
                ← Tiếp tục mua sắm
              </Link>
            </aside>
          </div>
        )}
      </div>
    </div>
  );
}
