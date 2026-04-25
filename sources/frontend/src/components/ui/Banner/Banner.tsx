'use client';

/**
 * Banner — validation banner (UI-SPEC Surface 1).
 *
 * Appears above forms when a VALIDATION_ERROR response is received (or when
 * client-side pre-submit validation finds 1+ errors). Reuses the canonical
 * error-tint recipe from Input.module.css so the codebase has a single
 * "magic literal" for the error background.
 *
 * Copy is locked to UI-SPEC §Copywriting Contract:
 *   - default heading: "Vui lòng kiểm tra các trường bị lỗi"
 *   - optional subtext when count > 3: "{count} trường cần được sửa trước khi tiếp tục."
 *
 * Consumers override the heading via `children`.
 */

import React from 'react';
import styles from './Banner.module.css';

interface BannerProps {
  tone?: 'error'; // only 'error' used in Phase 4; reserved for future tones
  count?: number; // when > 3, renders the subtext
  children?: React.ReactNode; // default heading fallback
}

export default function Banner({ tone = 'error', count, children }: BannerProps) {
  const showSubtext = typeof count === 'number' && count > 3;
  return (
    <div
      className={`${styles.banner} ${styles[`tone-${tone}`]}`}
      role="alert"
      aria-live="assertive"
    >
      <svg
        className={styles.icon}
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        aria-hidden="true"
      >
        <path d="M12 9v4M12 17h.01M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      </svg>
      <div className={styles.body}>
        <div className={`${styles.heading} text-body-lg`}>
          {children ?? 'Vui lòng kiểm tra các trường bị lỗi'}
        </div>
        {showSubtext && (
          <div className={`${styles.subtext} text-body-md`}>
            {count} trường cần được sửa trước khi tiếp tục.
          </div>
        )}
      </div>
    </div>
  );
}
