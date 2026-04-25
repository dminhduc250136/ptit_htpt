'use client';

import React from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './ProductCard.module.css';
import Badge from '@/components/ui/Badge/Badge';
import { Product } from '@/types';
import { formatPrice } from '@/services/api';

interface ProductCardProps {
  product: Product;
  className?: string;
}

function getTagVariant(tag: string): 'sale' | 'new' | 'hot' | 'default' {
  const lower = tag.toLowerCase();
  if (lower.includes('sale') || lower.includes('giảm')) return 'sale';
  if (lower.includes('mới') || lower.includes('new')) return 'new';
  if (lower.includes('bán chạy') || lower.includes('hot') || lower.includes('best')) return 'hot';
  return 'default';
}

export default function ProductCard({ product, className = '' }: ProductCardProps) {
  const hasDiscount = product.originalPrice && product.discount;

  return (
    <div className={`${styles.card} ${className}`}>
      {/* Stretched link for card navigation */}
      <Link href={`/products/${product.slug}`} className={styles.cardLink} aria-label={product.name} />

      {/* Image Section */}
      <div className={styles.imageWrapper}>
        <Image
          src={product.thumbnailUrl?.trim() ? product.thumbnailUrl : '/placeholder.png'}
          alt={product.name}
          fill
          sizes="(max-width: 768px) 50vw, (max-width: 1200px) 33vw, 25vw"
          className={styles.image}
        />

        {/* Tags */}
        {product.tags && product.tags.length > 0 && (
          <div className={styles.tags}>
            {product.tags.map((tag) => (
              <Badge key={tag} variant={getTagVariant(tag)}>
                {tag}
              </Badge>
            ))}
          </div>
        )}

        {/* Quick Actions (hover) */}
        <div className={styles.quickActions}>
          <button className={styles.quickBtn} aria-label="Thêm vào yêu thích">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" />
            </svg>
          </button>
          <button className={styles.quickBtn} aria-label="Thêm vào giỏ hàng">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
          </button>
        </div>
      </div>

      {/* Content Section */}
      <div className={styles.content}>
        {product.category?.name && (
          <span className={styles.category}>{product.category.name}</span>
        )}
        <h3 className={styles.name}>{product.name}</h3>
        <p className={styles.description}>{product.shortDescription}</p>

        <div className={styles.priceRow}>
          <span className={styles.price}>{formatPrice(product.price)}</span>
          {hasDiscount && (
            <>
              <span className={styles.originalPrice}>{formatPrice(product.originalPrice!)}</span>
              <Badge variant="sale">-{product.discount}%</Badge>
            </>
          )}
        </div>

        {/* Rating */}
        <div className={styles.rating}>
          <div className={styles.stars}>
            {[...Array(5)].map((_, i) => (
              <svg
                key={i}
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill={i < Math.floor(product.rating ?? 0) ? 'var(--secondary-container)' : 'none'}
                stroke={i < Math.floor(product.rating ?? 0) ? 'var(--secondary-container)' : 'var(--outline-variant)'}
                strokeWidth="2"
              >
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
            ))}
          </div>
          <span className={styles.reviewCount}>({product.reviewCount ?? 0})</span>
        </div>
      </div>
    </div>
  );
}
