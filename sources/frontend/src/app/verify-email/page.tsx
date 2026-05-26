'use client';

import React, { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';

/**
 * Phase 27 / Plan 27-05 (MAIL-02): Trang xác minh email.
 *
 * - Status card (không phải form) — CSS riêng verify-email/page.module.css.
 * - Suspense + useSearchParams đọc ?token (Next.js App Router requirement).
 * - State machine: loading | success | expired | invalid.
 * - Không có token → invalid ngay (không gọi API).
 * - 200 → success, 410 → expired, khác → invalid.
 * - CTA: "Gửi lại email xác minh" → /register (endpoint resend chưa có trong phase này).
 *
 * UI-SPEC: icon circle 64px, one-off color #16a34a cho success.
 */

type VerifyState = 'loading' | 'success' | 'expired' | 'invalid';

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');
  const [state, setState] = useState<VerifyState>(token ? 'loading' : 'invalid');

  useEffect(() => {
    if (!token) return;
    fetch(`/api/users/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then((res) => {
        if (res.ok) {
          setState('success');
        } else if (res.status === 410) {
          setState('expired');
        } else {
          setState('invalid');
        }
      })
      .catch(() => setState('invalid'));
  }, [token]);

  if (state === 'loading') {
    return (
      <div className={styles.page}>
        <div className={styles.statusCard}>
          <div className={styles.spinner} aria-label="Đang xử lý" />
        </div>
      </div>
    );
  }

  if (state === 'success') {
    return (
      <div className={styles.page}>
        <div className={styles.statusCard}>
          <div className={`${styles.iconCircle} ${styles.iconCircleSuccess}`}>
            {/* Check circle — one-off color #16a34a (UI-SPEC) */}
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
          </div>
          <h1 className={styles.heading}>Email đã được xác minh!</h1>
          <p className={styles.body}>Tài khoản của bạn đã được kích hoạt thành công.</p>
          <div className={styles.cta}>
            <Link href="/login">
              <Button size="lg" fullWidth>Đến trang đăng nhập</Button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  if (state === 'expired') {
    return (
      <div className={styles.page}>
        <div className={styles.statusCard}>
          <div className={`${styles.iconCircle} ${styles.iconCircleError}`}>
            {/* Clock icon */}
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
          </div>
          <h1 className={styles.heading}>Link xác minh đã hết hạn</h1>
          <p className={styles.body}>
            Link xác minh có hiệu lực 24 giờ. Vui lòng yêu cầu gửi lại.
          </p>
          <div className={styles.cta}>
            <Link href="/register">
              <Button size="lg" fullWidth>Gửi lại email xác minh</Button>
            </Link>
          </div>
        </div>
      </div>
    );
  }

  // state === 'invalid'
  return (
    <div className={styles.page}>
      <div className={styles.statusCard}>
        <div className={`${styles.iconCircle} ${styles.iconCircleError}`}>
          {/* X circle icon */}
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="10" />
            <line x1="15" y1="9" x2="9" y2="15" />
            <line x1="9" y1="9" x2="15" y2="15" />
          </svg>
        </div>
        <h1 className={styles.heading}>Link không hợp lệ</h1>
        <p className={styles.body}>
          Link xác minh không đúng hoặc đã được sử dụng.
        </p>
        <div className={styles.cta}>
          <Link href="/">
            <Button size="lg" fullWidth>Quay lại trang chủ</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense fallback={<div className={styles.page}><div className={styles.statusCard}><div className={styles.spinner} /></div></div>}>
      <VerifyEmailContent />
    </Suspense>
  );
}
