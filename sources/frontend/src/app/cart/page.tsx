'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import { mockProducts } from '@/mock-data/products';
import { formatPrice } from '@/services/api';

interface MockCartItem {
  product: typeof mockProducts[0];
  quantity: number;
}

export default function CartPage() {
  const [cartItems, setCartItems] = useState<MockCartItem[]>([
    { product: mockProducts[0], quantity: 1 },
    { product: mockProducts[1], quantity: 2 },
    { product: mockProducts[3], quantity: 1 },
  ]);

  const updateQuantity = (index: number, qty: number) => {
    if (qty < 1) return;
    setCartItems(prev => prev.map((item, i) =>
      i === index ? { ...item, quantity: Math.min(qty, item.product.stock) } : item
    ));
  };

  const removeItem = (index: number) => {
    setCartItems(prev => prev.filter((_, i) => i !== index));
  };

  const subtotal = cartItems.reduce((sum, item) => sum + item.product.price * item.quantity, 0);
  const shippingFee = subtotal >= 1000000 ? 0 : 30000;
  const total = subtotal + shippingFee;

  return (
    <div className={styles.page}>
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Giỏ hàng</h1>
          <p className={styles.pageSubtitle}>{cartItems.length} sản phẩm trong giỏ hàng</p>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {cartItems.length === 0 ? (
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
              {cartItems.map((item, index) => (
                <div key={item.product.id} className={styles.cartItem}>
                  <Link href={`/products/${item.product.slug}`} className={styles.itemImage}>
                    <Image
                      src={item.product.thumbnailUrl}
                      alt={item.product.name}
                      fill
                      sizes="120px"
                      className={styles.itemImg}
                    />
                  </Link>

                  <div className={styles.itemInfo}>
                    <div className={styles.itemTop}>
                      <div>
                        <span className={styles.itemCategory}>{item.product.category.name}</span>
                        <Link href={`/products/${item.product.slug}`} className={styles.itemName}>
                          {item.product.name}
                        </Link>
                      </div>
                      <button
                        className={styles.removeBtn}
                        onClick={() => removeItem(index)}
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
                        <button className={styles.qtyBtn} onClick={() => updateQuantity(index, item.quantity - 1)} disabled={item.quantity <= 1}>−</button>
                        <span className={styles.qtyValue}>{item.quantity}</span>
                        <button className={styles.qtyBtn} onClick={() => updateQuantity(index, item.quantity + 1)} disabled={item.quantity >= item.product.stock}>+</button>
                      </div>
                      <div className={styles.itemPrice}>
                        <span className={styles.priceTotal}>{formatPrice(item.product.price * item.quantity)}</span>
                        {item.quantity > 1 && (
                          <span className={styles.priceUnit}>{formatPrice(item.product.price)} / sản phẩm</span>
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

              <Button size="lg" fullWidth>
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
