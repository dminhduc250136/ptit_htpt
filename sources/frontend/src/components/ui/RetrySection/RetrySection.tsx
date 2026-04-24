'use client';

/**
 * RetrySection — inline retry CTA (UI-SPEC Surface 3).
 *
 * Used by list pages (products, profile orders, home sections) when a GET
 * failed with 5xx or network error. Per D-10: wrapper must NOT auto-retry
 * mutations; this surface is GET-only.
 *
 * Default copy is locked to UI-SPEC §Copywriting Contract; callers override
 * via `heading` / `body` props when the context calls for a more specific
 * message.
 */

import React from 'react';
import styles from './RetrySection.module.css';
import Button from '../Button/Button';

interface RetrySectionProps {
  onRetry: () => void;
  loading?: boolean;
  heading?: string;
  body?: string;
}

export default function RetrySection({
  onRetry,
  loading,
  heading = 'Không tải được dữ liệu',
  body = 'Đã xảy ra lỗi khi tải. Vui lòng thử lại.',
}: RetrySectionProps) {
  return (
    <div className={styles.wrapper}>
      <svg
        className={styles.icon}
        width="40"
        height="40"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="8" x2="12" y2="12" />
        <line x1="12" y1="16" x2="12.01" y2="16" />
      </svg>
      <h3 className={`${styles.heading} text-title-sm`}>{heading}</h3>
      <p className={`${styles.body} text-body-md`}>{body}</p>
      <Button variant="primary" size="md" loading={loading} onClick={onRetry}>
        Thử lại
      </Button>
    </div>
  );
}
