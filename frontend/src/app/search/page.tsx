'use client';

import React, { Suspense, useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import Input from '@/components/ui/Input/Input';
import { mockProducts } from '@/mock-data/products';

function SearchPageContent() {
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get('q') || '';
  const [query, setQuery] = useState(initialQuery);

  const results = useMemo(() => {
    if (!query.trim()) return [];
    const q = query.toLowerCase();
    return mockProducts.filter(p =>
      p.name.toLowerCase().includes(q) ||
      p.shortDescription.toLowerCase().includes(q) ||
      p.category.name.toLowerCase().includes(q) ||
      p.brand?.toLowerCase().includes(q)
    );
  }, [query]);

  return (
    <div className={styles.page}>
      <div className={styles.searchHeader}>
        <div className={styles.container}>
          <h1 className={styles.title}>Tìm kiếm</h1>
          <div className={styles.searchBox}>
            <Input
              placeholder="Nhập tên sản phẩm, thương hiệu, danh mục..."
              value={query}
              onChange={e => setQuery(e.target.value)}
              fullWidth
              icon={<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>}
            />
          </div>
        </div>
      </div>

      <div className={`${styles.container} ${styles.content}`}>
        {!query.trim() ? (
          <div className={styles.emptyState}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <h3>Nhập từ khóa để tìm kiếm</h3>
            <p>Tìm sản phẩm theo tên, thương hiệu hoặc danh mục</p>
          </div>
        ) : results.length === 0 ? (
          <div className={styles.emptyState}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <h3>Không tìm thấy kết quả cho &quot;{query}&quot;</h3>
            <p>Thử sử dụng từ khóa khác hoặc kiểm tra lại chính tả</p>
          </div>
        ) : (
          <>
            <p className={styles.resultCount}>Tìm thấy <strong>{results.length}</strong> sản phẩm cho &quot;{query}&quot;</p>
            <div className={styles.grid}>
              {results.map(p => <ProductCard key={p.id} product={p} />)}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<div className={styles.page} />}>
      <SearchPageContent />
    </Suspense>
  );
}
