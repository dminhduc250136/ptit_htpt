'use client';

/**
 * Search page — rewired to services/products.listProducts({ keyword }) in Phase 4-03.
 *
 * Pre-rewire (Wave 1/2): hardcoded fixture array + client-side filter.
 * Post-rewire: real backend call through typed HTTP tier; RetrySection on 5xx/network
 * failures per D-10; empty-state when results are zero; loading skeleton between
 * keyword changes.
 *
 * Debounce: 350ms. Prevents flooding /api/products/products on every keystroke while
 * staying snappy. Replaces the old useMemo-on-fixture pattern.
 */

import React, { Suspense, useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import ProductCard from '@/components/ui/ProductCard/ProductCard';
import Input from '@/components/ui/Input/Input';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { listProducts } from '@/services/products';
import type { Product } from '@/types';

function SearchPageContent() {
  const searchParams = useSearchParams();
  const initialQuery = searchParams.get('q') || '';

  const [query, setQuery] = useState(initialQuery);
  const [debouncedQuery, setDebouncedQuery] = useState(initialQuery);
  const [results, setResults] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  // Debounce the keyword (350ms) so typing does not flood the backend.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query), 350);
    return () => clearTimeout(t);
  }, [query]);

  const load = useCallback(async (keyword: string) => {
    const trimmed = keyword.trim();
    if (!trimmed) {
      setResults([]);
      setLoading(false);
      setFailed(false);
      return;
    }
    setLoading(true);
    setFailed(false);
    try {
      const resp = await listProducts({ size: 24, keyword: trimmed });
      setResults(resp?.content ?? []);
    } catch {
      // Any ApiError (incl. 5xx / network) → RetrySection per D-10. No auto-retry.
      setFailed(true);
      setResults([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(debouncedQuery);
  }, [debouncedQuery, load]);

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
        {!debouncedQuery.trim() ? (
          <div className={styles.emptyState}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <h3>Nhập từ khóa để tìm kiếm</h3>
            <p>Tìm sản phẩm theo tên, thương hiệu hoặc danh mục</p>
          </div>
        ) : loading ? (
          <div className={styles.grid}>
            {[...Array(8)].map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 360, borderRadius: 'var(--radius-lg)' }} />
            ))}
          </div>
        ) : failed ? (
          <RetrySection onRetry={() => load(debouncedQuery)} loading={loading} />
        ) : results.length === 0 ? (
          <div className={styles.emptyState}>
            <svg width="80" height="80" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <h3>Không tìm thấy kết quả cho &quot;{debouncedQuery}&quot;</h3>
            <p>Thử sử dụng từ khóa khác hoặc kiểm tra lại chính tả</p>
          </div>
        ) : (
          <>
            <p className={styles.resultCount}>Tìm thấy <strong>{results.length}</strong> sản phẩm cho &quot;{debouncedQuery}&quot;</p>
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
