'use client';

import React, { Suspense, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import FilterSidebar, { type FilterValue } from '@/components/ui/FilterSidebar/FilterSidebar';
import { listProducts, listCategories, listBrands } from '@/services/products';
import type { Product, Category } from '@/types';

type SortOption = 'newest' | 'price_asc' | 'price_desc' | 'popular' | 'rating';

function ProductsPageContent() {
  const searchParams = useSearchParams();
  const initialCategorySlug = searchParams.get('category');

  const [categories, setCategories] = useState<Category[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [sortBy, setSortBy] = useState<SortOption>('newest');
  const [filterBrands, setFilterBrands] = useState<string[]>([]);
  const [filterPriceMin, setFilterPriceMin] = useState<number | undefined>(undefined);
  const [filterPriceMax, setFilterPriceMax] = useState<number | undefined>(undefined);
  const [availableBrands, setAvailableBrands] = useState<string[]>([]);
  const [brandsLoading, setBrandsLoading] = useState(true);
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);

  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  // Load categories once (best-effort; failure here does NOT block the grid).
  useEffect(() => {
    let alive = true;
    listCategories()
      .then((resp) => {
        if (!alive) return;
        setCategories(resp?.content ?? []);
        if (initialCategorySlug) {
          const match = resp?.content?.find((c) => c.slug === initialCategorySlug);
          if (match) setSelectedCategory(match.id);
        }
      })
      .catch(() => {
        // Categories failure is non-fatal — grid + price filters still usable.
      });
    return () => {
      alive = false;
    };
  }, [initialCategorySlug]);

  // Phase 14 / SEARCH-01 — fetch danh sách brand DISTINCT (non-fatal fail).
  useEffect(() => {
    let alive = true;
    setBrandsLoading(true);
    listBrands()
      .then((list) => {
        if (!alive) return;
        setAvailableBrands(list ?? []);
      })
      .catch(() => {
        // Non-fatal — brand facet shows "Chưa có thương hiệu nào"
      })
      .finally(() => {
        if (alive) setBrandsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const sortParam =
        sortBy === 'price_asc'
          ? 'price,asc'
          : sortBy === 'price_desc'
          ? 'price,desc'
          : sortBy === 'newest'
          ? 'createdAt,desc'
          : sortBy === 'rating'
          ? 'rating,desc'
          : sortBy === 'popular'
          ? 'reviewCount,desc'
          : undefined;
      const resp = await listProducts({
        page: 0,
        size: 24,
        sort: sortParam,
        categoryId: selectedCategory ?? undefined,
        keyword: searchQuery.trim() || undefined,
        brands: filterBrands.length > 0 ? filterBrands : undefined,
        priceMin: filterPriceMin,
        priceMax: filterPriceMax,
      });
      setProducts(resp?.content ?? []);
    } catch {
      // Any ApiError (incl. 5xx / network) → RetrySection per D-10. No auto-retry.
      setFailed(true);
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, [sortBy, selectedCategory, searchQuery, filterBrands, filterPriceMin, filterPriceMax]);

  useEffect(() => {
    load();
  }, [load]);

  // D-10: "Xóa bộ lọc" trong FilterSidebar chỉ reset brand+price; KHÔNG đụng categories/keyword/sort.
  const clearFilters = () => {
    setFilterBrands([]);
    setFilterPriceMin(undefined);
    setFilterPriceMax(undefined);
  };

  // Header "Xóa tất cả" — reset toàn bộ filter (categories + search + sort + brand + price).
  const clearAll = () => {
    setSelectedCategory(null);
    setSearchQuery('');
    setSortBy('newest');
    clearFilters();
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
              <button className={styles.clearBtn} onClick={clearAll}>Xóa tất cả</button>
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
                {categories.map((cat) => (
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

            {/* Phase 14 — FilterSidebar (brand + price) */}
            <FilterSidebar
              brands={availableBrands}
              loading={brandsLoading}
              value={{ brands: filterBrands, priceMin: filterPriceMin, priceMax: filterPriceMax }}
              onChange={(next: FilterValue) => {
                setFilterBrands(next.brands);
                setFilterPriceMin(next.priceMin);
                setFilterPriceMax(next.priceMax);
              }}
            />

            {/* Mobile close */}
            <div className={styles.mobileFilterClose}>
              <Button fullWidth onClick={() => setIsMobileFilterOpen(false)}>
                Xem {products.length} sản phẩm
              </Button>
            </div>
          </aside>

          {/* Products Grid */}
          <div className={styles.main}>
            {/* Toolbar */}
            <div className={styles.toolbar}>
              <span className={styles.resultCount}>
                {products.length} sản phẩm
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

            {/* States: loading → skeleton; failed → RetrySection; empty → empty-state; list → grid */}
            {loading ? (
              <div className={styles.productsGrid}>
                {[...Array(8)].map((_, i) => (
                  <div key={i} className={`${styles.skeletonCard} skeleton`} style={{ height: 360 }} />
                ))}
              </div>
            ) : failed ? (
              <RetrySection onRetry={() => load()} loading={loading} />
            ) : products.length > 0 ? (
              <div className={styles.productsGrid}>
                {products.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            ) : (
              <div className={styles.emptyState}>
                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1.5">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <h3>Không tìm thấy sản phẩm phù hợp với bộ lọc</h3>
                <p>Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá</p>
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
