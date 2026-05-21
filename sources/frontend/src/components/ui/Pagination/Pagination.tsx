'use client';

/**
 * Pagination — thanh phân trang nút số (Trước / 1 2 3 … / Sau).
 *
 * Dùng cho danh sách có totalPages từ PaginatedResponse. Hiển thị tối đa 1 cửa
 * sổ trang quanh trang hiện tại + trang đầu/cuối, chèn dấu "…" khi cách quãng.
 * Trang đánh số 0-based ở props (khớp Spring Pageable) nhưng hiển thị 1-based.
 */

import React from 'react';
import styles from './Pagination.module.css';

interface PaginationProps {
  /** Trang hiện tại — 0-based (khớp backend Spring Pageable). */
  page: number;
  /** Tổng số trang. */
  totalPages: number;
  /** Gọi khi user chọn trang khác — nhận index 0-based. */
  onPageChange: (page: number) => void;
}

/** Sinh danh sách item hiển thị: số trang (1-based) hoặc 'gap' cho dấu "…". */
function buildPages(current1: number, total: number): (number | 'gap')[] {
  // Ít trang → hiện hết, không cần "…".
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1);
  }
  const pages: (number | 'gap')[] = [1];
  const start = Math.max(2, current1 - 1);
  const end = Math.min(total - 1, current1 + 1);
  if (start > 2) pages.push('gap');
  for (let p = start; p <= end; p++) pages.push(p);
  if (end < total - 1) pages.push('gap');
  pages.push(total);
  return pages;
}

export default function Pagination({ page, totalPages, onPageChange }: PaginationProps) {
  // Không có gì để phân trang.
  if (totalPages <= 1) return null;

  const current1 = page + 1; // 1-based để hiển thị
  const items = buildPages(current1, totalPages);

  return (
    <nav className={styles.pagination} aria-label="Phân trang">
      <button
        className={styles.navBtn}
        onClick={() => onPageChange(page - 1)}
        disabled={page <= 0}
        aria-label="Trang trước"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="15 18 9 12 15 6" />
        </svg>
        <span className={styles.navLabel}>Trước</span>
      </button>

      <ul className={styles.pageList}>
        {items.map((item, idx) =>
          item === 'gap' ? (
            <li key={`gap-${idx}`} className={styles.gap} aria-hidden="true">
              …
            </li>
          ) : (
            <li key={item}>
              <button
                className={`${styles.pageBtn} ${item === current1 ? styles.pageBtnActive : ''}`}
                onClick={() => onPageChange(item - 1)}
                aria-label={`Trang ${item}`}
                aria-current={item === current1 ? 'page' : undefined}
              >
                {item}
              </button>
            </li>
          )
        )}
      </ul>

      <button
        className={styles.navBtn}
        onClick={() => onPageChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="Trang sau"
      >
        <span className={styles.navLabel}>Sau</span>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>
    </nav>
  );
}
