'use client';

import React, { Suspense, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import { mockProducts, mockCategories } from '@/mock-data/products';

type SortOption = 'newest' | 'price_asc' | 'price_desc' | 'popular' | 'rating';

function ProductsPageContent() {
  const searchParams = useSearchParams();
  const categorySlug = searchParams.get('category');
  const initialCategory = categorySlug
    ? mockCategories.find(c => c.slug === categorySlug)?.id ?? null
    : null;

  const [selectedCategory, setSelectedCategory] = useState<string | null>(initialCategory);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('newest');
  const [priceRange, setPriceRange] = useState<[number, number]>([0, 10000000]);
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);

  const filteredProducts = useMemo(() => {
    let result = [...mockProducts];

    // Filter by category
    if (selectedCategory) {
      result = result.filter(p => p.category.id === selectedCategory);
    }

    // Filter by search
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      result = result.filter(p =>
        p.name.toLowerCase().includes(q) ||
        p.shortDescription.toLowerCase().includes(q)
      );
    }

    // Filter by price range
    result = result.filter(p => p.price >= priceRange[0] && p.price <= priceRange[1]);

    // Sort
    switch (sortBy) {
      case 'price_asc':
        result.sort((a, b) => a.price - b.price);
        break;
      case 'price_desc':
        result.sort((a, b) => b.price - a.price);
        break;
      case 'newest':
        result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        break;
      case 'rating':
        result.sort((a, b) => b.rating - a.rating);
        break;
      case 'popular':
        result.sort((a, b) => b.reviewCount - a.reviewCount);
        break;
    }

    return result;
  }, [selectedCategory, searchQuery, sortBy, priceRange]);

  const clearFilters = () => {
    setSelectedCategory(null);
    setSearchQuery('');
    setSortBy('newest');
    setPriceRange([0, 10000000]);
  };

  return (
    <div className={styles.page}>
      {/* Page Header */}
      <div className={styles.pageHeader}>
        <div className={styles.container}>
          <h1 className={styles.pageTitle}>Sản phẩm</h1>
          <p className={styles.pageSubtitle}>
            Khám phá bộ sưu tập được tuyển chọn kỹ lưỡng từ những nghệ nhân hàng đầu
          </p>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {/* Mobile Filter Toggle */}
        <button
          className={styles.mobileFilterToggle}
          onClick={() => setIsMobileFilterOpen(!isMobileFilterOpen)}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <line x1="4" y1="21" x2="4" y2="14" /><line x1="4" y1="10" x2="4" y2="3" />
            <line x1="12" y1="21" x2="12" y2="12" /><line x1="12" y1="8" x2="12" y2="3" />
            <line x1="20" y1="21" x2="20" y2="16" /><line x1="20" y1="12" x2="20" y2="3" />
            <line x1="1" y1="14" x2="7" y2="14" /><line x1="9" y1="8" x2="15" y2="8" />
            <line x1="17" y1="16" x2="23" y2="16" />
          </svg>
          Bộ lọc
        </button>

        <div className={styles.layout}>
          {/* Sidebar Filters */}
          <aside className={`${styles.sidebar} ${isMobileFilterOpen ? styles.sidebarOpen : ''}`}>
            <div className={styles.sidebarHeader}>
              <h3 className={styles.sidebarTitle}>Bộ lọc</h3>
              <button className={styles.clearBtn} onClick={clearFilters}>Xóa tất cả</button>
            </div>

            {/* Search */}
            <div className={styles.filterGroup}>
              <Input
                placeholder="Tìm kiếm sản phẩm..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                fullWidth
                icon={
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                }
              />
            </div>

            {/* Categories */}
            <div className={styles.filterGroup}>
              <h4 className={styles.filterTitle}>Danh mục</h4>
              <div className={styles.filterOptions}>
                <button
                  className={`${styles.filterChip} ${!selectedCategory ? styles.filterChipActive : ''}`}
                  onClick={() => setSelectedCategory(null)}
                >
                  Tất cả
                </button>
                {mockCategories.map(cat => (
                  <button
                    key={cat.id}
                    className={`${styles.filterChip} ${selectedCategory === cat.id ? styles.filterChipActive : ''}`}
                    onClick={() => setSelectedCategory(cat.id)}
                  >
                    {cat.name}
                  </button>
                ))}
              </div>
            </div>

            {/* Price Range */}
            <div className={styles.filterGroup}>
              <h4 className={styles.filterTitle}>Khoảng giá</h4>
              <div className={styles.priceInputs}>
                <input
                  type="number"
                  className={styles.priceInput}
                  placeholder="Từ"
                  value={priceRange[0] || ''}
                  onChange={(e) => setPriceRange([Number(e.target.value) || 0, priceRange[1]])}
                />
                <span className={styles.priceSeparator}>—</span>
                <input
                  type="number"
                  className={styles.priceInput}
                  placeholder="Đến"
                  value={priceRange[1] === 10000000 ? '' : priceRange[1]}
                  onChange={(e) => setPriceRange([priceRange[0], Number(e.target.value) || 10000000])}
                />
              </div>
              <div className={styles.pricePresets}>
                <button className={styles.presetBtn} onClick={() => setPriceRange([0, 500000])}>Dưới 500k</button>
                <button className={styles.presetBtn} onClick={() => setPriceRange([500000, 1000000])}>500k - 1tr</button>
                <button className={styles.presetBtn} onClick={() => setPriceRange([1000000, 5000000])}>1tr - 5tr</button>
                <button className={styles.presetBtn} onClick={() => setPriceRange([5000000, 10000000])}>Trên 5tr</button>
              </div>
            </div>

            {/* Mobile close */}
            <div className={styles.mobileFilterClose}>
              <Button fullWidth onClick={() => setIsMobileFilterOpen(false)}>
                Xem {filteredProducts.length} sản phẩm
              </Button>
            </div>
          </aside>

          {/* Products Grid */}
          <div className={styles.main}>
            {/* Toolbar */}
            <div className={styles.toolbar}>
              <span className={styles.resultCount}>
                {filteredProducts.length} sản phẩm
              </span>
              <select
                className={styles.sortSelect}
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as SortOption)}
              >
                <option value="newest">Mới nhất</option>
                <option value="popular">Phổ biến</option>
                <option value="rating">Đánh giá cao</option>
                <option value="price_asc">Giá thấp → cao</option>
                <option value="price_desc">Giá cao → thấp</option>
              </select>
            </div>

            {/* Grid */}
            {filteredProducts.length > 0 ? (
              <div className={styles.productsGrid}>
                {filteredProducts.map(product => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <div className={styles.emptyState}>
                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1.5">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <h3>Không tìm thấy sản phẩm</h3>
                <p>Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm</p>
                <Button variant="secondary" onClick={clearFilters}>Xóa bộ lọc</Button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ProductsPage() {
  return (
    <Suspense fallback={<div className={styles.page} />}>
      <ProductsPageContent />
    </Suspense>
  );
}
