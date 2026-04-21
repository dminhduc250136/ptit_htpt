'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import { mockProducts } from '@/mock-data/products';
import { formatPrice } from '@/services/api';

export default function ProductDetailPage({ params }: { params: { slug: string } }) {
  const { slug } = params;
  const product = mockProducts.find(p => p.slug === slug);

  const [selectedImage, setSelectedImage] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [activeTab, setActiveTab] = useState<'description' | 'specs' | 'reviews'>('description');

  if (!product) {
    return (
      <div className={styles.notFound}>
        <h2>Sản phẩm không tồn tại</h2>
        <p>Sản phẩm bạn đang tìm kiếm không tồn tại hoặc đã bị xóa.</p>
        <Button href="/products">Quay lại cửa hàng</Button>
      </div>
    );
  }

  const hasDiscount = product.originalPrice && product.discount;
  const relatedProducts = mockProducts
    .filter(p => p.category.id === product.category.id && p.id !== product.id)
    .slice(0, 4);

  return (
    <div className={styles.page}>
      {/* Breadcrumb */}
      <div className={styles.breadcrumb}>
        <div className={styles.container}>
          <Link href="/" className={styles.breadcrumbLink}>Trang chủ</Link>
          <span className={styles.breadcrumbSep}>/</span>
          <Link href="/products" className={styles.breadcrumbLink}>Sản phẩm</Link>
          <span className={styles.breadcrumbSep}>/</span>
          <Link href={`/products?category=${product.category.slug}`} className={styles.breadcrumbLink}>
            {product.category.name}
          </Link>
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
                  src={product.images[selectedImage]}
                  alt={product.name}
                  fill
                  sizes="(max-width: 768px) 100vw, 50vw"
                  className={styles.mainImg}
                  priority
                />
                {product.tags && product.tags.length > 0 && (
                  <div className={styles.imageTags}>
                    {product.tags.map(tag => (
                      <Badge key={tag} variant={tag.toLowerCase().includes('sale') ? 'sale' : tag.toLowerCase().includes('mới') || tag.toLowerCase().includes('new') ? 'new' : tag.toLowerCase().includes('bán chạy') || tag.toLowerCase().includes('best') ? 'hot' : 'default'}>
                        {tag}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
              {product.images.length > 1 && (
                <div className={styles.thumbnails}>
                  {product.images.map((img, i) => (
                    <button
                      key={i}
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
              <span className={styles.categoryLabel}>{product.category.name}</span>
              {product.brand && <span className={styles.brandLabel}>{product.brand}</span>}
              <h1 className={styles.productName}>{product.name}</h1>

              {/* Rating */}
              <div className={styles.rating}>
                <div className={styles.stars}>
                  {[...Array(5)].map((_, i) => (
                    <svg key={i} width="18" height="18" viewBox="0 0 24 24"
                      fill={i < Math.floor(product.rating) ? 'var(--secondary-container)' : 'none'}
                      stroke={i < Math.floor(product.rating) ? 'var(--secondary-container)' : 'var(--outline-variant)'}
                      strokeWidth="2">
                      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
                    </svg>
                  ))}
                </div>
                <span className={styles.ratingText}>{product.rating}</span>
                <span className={styles.reviewCount}>({product.reviewCount} đánh giá)</span>
              </div>

              <p className={styles.shortDesc}>{product.shortDescription}</p>

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

              {/* Stock */}
              <div className={styles.stockInfo}>
                {product.stock > 0 ? (
                  <span className={styles.inStock}>✓ Còn hàng ({product.stock} sản phẩm)</span>
                ) : (
                  <span className={styles.outOfStock}>✗ Hết hàng</span>
                )}
              </div>

              {/* Quantity & Add to Cart */}
              <div className={styles.actions}>
                <div className={styles.quantitySelector}>
                  <button
                    className={styles.qtyBtn}
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    disabled={quantity <= 1}
                  >−</button>
                  <span className={styles.qtyValue}>{quantity}</span>
                  <button
                    className={styles.qtyBtn}
                    onClick={() => setQuantity(Math.min(product.stock, quantity + 1))}
                    disabled={quantity >= product.stock}
                  >+</button>
                </div>
                <Button size="lg" fullWidth disabled={product.stock === 0}>
                  Thêm vào giỏ hàng
                </Button>
              </div>

              <Button variant="secondary" size="lg" fullWidth>
                ♡ Thêm vào yêu thích
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
            >Đánh giá ({product.reviewCount})</button>
          </div>

          <div className={styles.tabContent}>
            {activeTab === 'description' && (
              <div className={styles.descriptionContent}>
                <p>{product.description}</p>
              </div>
            )}
            {activeTab === 'specs' && product.specifications && (
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
            {activeTab === 'reviews' && (
              <div className={styles.reviewsContent}>
                <div className={styles.reviewPlaceholder}>
                  <p>Chưa có đánh giá nào. Hãy là người đầu tiên đánh giá sản phẩm này!</p>
                  <Button variant="secondary">Viết đánh giá</Button>
                </div>
              </div>
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
              {relatedProducts.map(p => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  );
}
