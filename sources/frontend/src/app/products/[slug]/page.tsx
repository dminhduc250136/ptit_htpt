'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { useParams, useRouter, notFound } from 'next/navigation';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { getProductBySlug, listProducts } from '@/services/products';
import { addToCart } from '@/services/cart';
import { isApiError } from '@/services/errors';
import { formatPrice } from '@/services/api';
import type { Product } from '@/types';
import ReviewSection from './ReviewSection/ReviewSection';

export default function ProductDetailPage() {
  const params = useParams<{ slug: string }>();
  const slug = params?.slug;
  const router = useRouter();
  const { showToast } = useToast();

  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [relatedProducts, setRelatedProducts] = useState<Product[]>([]);

  const [selectedImage, setSelectedImage] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [activeTab, setActiveTab] = useState<'description' | 'specs' | 'reviews'>('description');
  const [addingToCart, setAddingToCart] = useState(false);

  const load = useCallback(async () => {
    if (!slug) return;
    setLoading(true);
    setFailed(false);
    try {
      const p = await getProductBySlug(slug);
      if (!p) {
        // Slug lookup returned null (no match) → Claude's Discretion: direct
        // resource navigation → 404 page.
        notFound();
        return;
      }
      setProduct(p);
      // Load related products (best-effort; failure does not flip the page state).
      try {
        const resp = await listProducts({
          size: 4,
          categoryId: p.category?.id,
        });
        setRelatedProducts((resp?.content ?? []).filter((x) => x.id !== p.id).slice(0, 4));
      } catch {
        setRelatedProducts([]);
      }
    } catch (err) {
      if (isApiError(err) && err.code === 'NOT_FOUND') {
        notFound();
        return;
      }
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return (
      <div className={styles.page}>
        <div className={styles.container} style={{ padding: 'var(--space-5) 0' }}>
          <div className="skeleton" style={{ height: 400, borderRadius: 'var(--radius-lg)' }} />
        </div>
      </div>
    );
  }

  if (failed) {
    return (
      <div className={styles.page}>
        <div className={styles.container} style={{ padding: 'var(--space-5) 0' }}>
          <RetrySection onRetry={() => load()} loading={loading} />
        </div>
      </div>
    );
  }

  if (!product) {
    // This branch is unreachable because notFound() triggered above, but kept as
    // a type-narrowing safety net.
    return null;
  }

  const hasDiscount = product.originalPrice && product.discount;

  return (
    <div className={styles.page}>
      {/* Breadcrumb — Phase 15 PUB-03 (D-14): Trang chủ / {Brand} / {Name} với fallback */}
      <div className={styles.breadcrumb}>
        <div className={styles.container}>
          <Link href="/" className={styles.breadcrumbLink}>Trang chủ</Link>
          <span className={styles.breadcrumbSep}>/</span>
          {product.brand ? (
            <Link
              href={`/products?brand=${encodeURIComponent(product.brand)}`}
              className={styles.breadcrumbLink}
            >
              {product.brand}
            </Link>
          ) : (
            <Link href="/products" className={styles.breadcrumbLink}>Sản phẩm</Link>
          )}
          <span className={styles.breadcrumbSep}>/</span>
          <span className={styles.breadcrumbCurrent}>{product.name}</span>
        </div>
      </div>

      {/* Product Section */}
      <section className={styles.productSection}>
        <div className={styles.container}>
          <div className={styles.productLayout}>
            {/* Image Gallery */}
            <div className={styles.gallery}>
              <div className={styles.mainImage}>
                <Image
                  src={product.images?.[selectedImage] ?? product.thumbnailUrl}
                  alt={product.name}
                  fill
                  sizes="(max-width: 768px) 100vw, 50vw"
                  className={styles.mainImg}
                  priority
                />
                {product.tags && product.tags.length > 0 && (
                  <div className={styles.imageTags}>
                    {product.tags.map((tag) => (
                      <Badge
                        key={tag}
                        variant={
                          tag.toLowerCase().includes('sale')
                            ? 'sale'
                            : tag.toLowerCase().includes('mới') || tag.toLowerCase().includes('new')
                            ? 'new'
                            : tag.toLowerCase().includes('bán chạy') || tag.toLowerCase().includes('best')
                            ? 'hot'
                            : 'default'
                        }
                      >
                        {tag}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
              {product.images && product.images.length > 1 && (
                <div className={styles.thumbnails}>
                  {product.images.map((img, i) => (
                    <button
                      key={i}
                      type="button"
                      aria-label={`Xem ảnh ${i + 1}`}
                      aria-current={i === selectedImage ? 'true' : undefined}
                      className={`${styles.thumbnail} ${i === selectedImage ? styles.thumbnailActive : ''}`}
                      onClick={() => setSelectedImage(i)}
                    >
                      <Image src={img} alt={`${product.name} - ${i + 1}`} fill sizes="80px" className={styles.thumbImg} />
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Product Info */}
            <div className={styles.info}>
              {product.category && <span className={styles.categoryLabel}>{product.category.name}</span>}
              {product.brand && <span className={styles.brandLabel}>{product.brand}</span>}
              <h1 className={styles.productName}>{product.name}</h1>

              {/* Rating */}
              <div className={styles.rating}>
                <div className={styles.stars}>
                  {[...Array(5)].map((_, i) => (
                    <svg key={i} width="18" height="18" viewBox="0 0 24 24"
                      fill={i < Math.floor(product.avgRating ?? 0) ? 'var(--secondary-container)' : 'none'}
                      stroke={i < Math.floor(product.avgRating ?? 0) ? 'var(--secondary-container)' : 'var(--outline-variant)'}
                      strokeWidth="2">
                      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                    </svg>
                  ))}
                </div>
                <span className={styles.ratingText}>{(product.avgRating ?? 0).toFixed(1)}</span>
                <span className={styles.reviewCount}>({product.reviewCount ?? 0} đánh giá)</span>
              </div>

              {product.shortDescription && <p className={styles.shortDesc}>{product.shortDescription}</p>}

              {/* Price */}
              <div className={styles.priceBlock}>
                <span className={styles.price}>{formatPrice(product.price)}</span>
                {hasDiscount && (
                  <div className={styles.discountRow}>
                    <span className={styles.originalPrice}>{formatPrice(product.originalPrice!)}</span>
                    <Badge variant="sale">Giảm {product.discount}%</Badge>
                  </div>
                )}
              </div>

              {/* Stock — Phase 15 PUB-04 (D-15): 3-tier badge với icon prefix WCAG 1.4.1 */}
              <div className={styles.stockInfo}>
                {(product.stock ?? 0) >= 10 && (
                  <Badge variant="success">✓ Còn hàng</Badge>
                )}
                {(product.stock ?? 0) > 0 && (product.stock ?? 0) < 10 && (
                  <Badge variant="warning">⚠ Sắp hết hàng (còn {product.stock})</Badge>
                )}
                {(product.stock ?? 0) === 0 && (
                  <Badge variant="danger">✗ Hết hàng</Badge>
                )}
              </div>

              {/* Quantity & Add to Cart — Phase 15 PUB-04 (D-16): hide hoàn toàn khi stock=0 */}
              {(product.stock ?? 0) > 0 ? (
                <div className={styles.actions}>
                  <div className={styles.quantitySelector}>
                    <button
                      type="button"
                      className={styles.qtyBtn}
                      onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                      disabled={quantity <= 1}
                    >−</button>
                    <span className={styles.qtyValue}>{quantity}</span>
                    <button
                      type="button"
                      className={styles.qtyBtn}
                      onClick={() => setQuantity((q) => Math.min(product.stock ?? 1, q + 1))}
                      disabled={quantity >= (product.stock ?? 1)}
                    >+</button>
                  </div>
                  <Button
                    size="lg"
                    fullWidth
                    loading={addingToCart}
                    onClick={async () => {
                      setAddingToCart(true);
                      try {
                        addToCart(
                          {
                            id: product.id,
                            name: product.name,
                            thumbnailUrl: product.thumbnailUrl,
                            price: product.price,
                            // BUG-FIX (cart-stock-cap): truyền stock để cart.ts lưu snapshot
                            // và updateQuantity có thể enforce giới hạn trên
                            stock: product.stock ?? 0,
                          },
                          quantity,
                        );
                        showToast('Đã thêm vào giỏ hàng', 'success');
                      } catch {
                        showToast('Không thể thêm vào giỏ hàng', 'error');
                      } finally {
                        setAddingToCart(false);
                      }
                    }}
                  >
                    Thêm vào giỏ hàng
                  </Button>
                </div>
              ) : (
                <p className={styles.outOfStockMessage}>
                  Sản phẩm tạm hết — vui lòng quay lại sau.
                </p>
              )}

              <Button variant="secondary" size="lg" fullWidth onClick={() => router.push('/cart')}>
                ♡ Xem giỏ hàng
              </Button>

              {/* Guarantees */}
              <div className={styles.guarantees}>
                <div className={styles.guaranteeItem}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                  </svg>
                  <span>Bảo hành 24 tháng</span>
                </div>
                <div className={styles.guaranteeItem}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="1" y="3" width="15" height="13" /><polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
                    <circle cx="5.5" cy="18.5" r="2.5" /><circle cx="18.5" cy="18.5" r="2.5" />
                  </svg>
                  <span>Miễn phí vận chuyển</span>
                </div>
                <div className={styles.guaranteeItem}>
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <polyline points="23 4 23 10 17 10" />
                    <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                  </svg>
                  <span>Đổi trả trong 30 ngày</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Tabs Section */}
      <section className={styles.tabsSection}>
        <div className={styles.container}>
          <div className={styles.tabs}>
            <button
              className={`${styles.tab} ${activeTab === 'description' ? styles.tabActive : ''}`}
              onClick={() => setActiveTab('description')}
            >Mô tả</button>
            <button
              className={`${styles.tab} ${activeTab === 'specs' ? styles.tabActive : ''}`}
              onClick={() => setActiveTab('specs')}
            >Thông số</button>
            <button
              className={`${styles.tab} ${activeTab === 'reviews' ? styles.tabActive : ''}`}
              onClick={() => setActiveTab('reviews')}
            >Đánh giá ({product.reviewCount ?? 0})</button>
          </div>

          <div className={styles.tabContent}>
            {activeTab === 'description' && (
              <div className={styles.descriptionContent}>
                <p>{product.description}</p>
              </div>
            )}
            {activeTab === 'specs' && product.specifications && product.specifications.length > 0 && (
              <div className={styles.specsContent}>
                <table className={styles.specsTable}>
                  <tbody>
                    {product.specifications.map((spec, i) => (
                      <tr key={i} className={i % 2 === 0 ? styles.specRowEven : styles.specRowOdd}>
                        <td className={styles.specLabel}>{spec.label}</td>
                        <td className={styles.specValue}>{spec.value}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {activeTab === 'specs' && (!product.specifications || product.specifications.length === 0) && (
              <p style={{ color: 'var(--on-surface-variant)' }}>Chưa có thông số kỹ thuật.</p>
            )}
            {activeTab === 'reviews' && (
              <ReviewSection productId={product.id} slug={slug ?? ''} />
            )}
          </div>
        </div>
      </section>

      {/* Related Products */}
      {relatedProducts.length > 0 && (
        <section className={styles.relatedSection}>
          <div className={styles.container}>
            <h2 className={styles.relatedTitle}>Sản phẩm liên quan</h2>
            <div className={styles.relatedGrid}>
              {relatedProducts.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
