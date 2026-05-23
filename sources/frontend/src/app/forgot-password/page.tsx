'use client';

import React, { Suspense, useState } from 'react';
import Link from 'next/link';
import styles from '../login/page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import { forgotPassword } from '@/services/auth';

/**
 * Phase 27 / Plan 27-05 (MAIL-02): Trang quên mật khẩu.
 *
 * - Form 1 Input email + Button submit.
 * - Tái dụng login/page.module.css (KHÔNG tạo file CSS mới).
 * - States: idle/loading/success/error.
 * - Success: ẩn form, hiện panel "Kiểm tra hộp thư của bạn".
 * - API luôn 200 (anti-enumeration T-27-10) → không có error state từ API.
 * - Suspense wrapper theo pattern login/page.tsx.
 */
function ForgotPasswordPageContent() {
  const [email, setEmail] = useState('');
  const [emailError, setEmailError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    // Client-side validation
    if (!email.trim()) {
      setEmailError('Vui lòng nhập email');
      return;
    }
    if (!/\S+@\S+\.\S+/.test(email.trim())) {
      setEmailError('Email không hợp lệ');
      return;
    }
    setEmailError(null);
    setApiError(null);
    setLoading(true);
    try {
      await forgotPassword(email.trim());
      // API luôn 200 dù email không tồn tại (anti-enumeration)
      setSuccess(true);
    } catch {
      setApiError('Có lỗi xảy ra. Vui lòng thử lại sau.');
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className={styles.page}>
        <div className={styles.formContainer}>
          <div className={styles.formHeader}>
            <h1 className={styles.formTitle}>Kiểm tra hộp thư của bạn</h1>
            <p className={styles.formSubtitle}>
              Chúng tôi đã gửi link đặt lại mật khẩu tới <strong>{email}</strong>.
              Link có hiệu lực trong 1 giờ.
            </p>
          </div>
          <p style={{ fontSize: 'var(--text-body-sm)', color: 'var(--text-secondary)', textAlign: 'center', marginTop: 'var(--space-4)' }}>
            Không thấy email? Kiểm tra thư mục spam hoặc thử lại.
          </p>
          <p className={styles.switchAuth} style={{ marginTop: 'var(--space-5)' }}>
            <Link href="/login" className={styles.switchLink}>Quay lại đăng nhập</Link>
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <div className={styles.formContainer}>
        <div className={styles.formHeader}>
          <h1 className={styles.formTitle}>Quên mật khẩu?</h1>
          <p className={styles.formSubtitle}>
            Nhập email đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu
          </p>
        </div>

        {apiError && <Banner count={1}>{apiError}</Banner>}

        <form className={styles.form} onSubmit={handleSubmit} aria-busy={loading}>
          <Input
            label="Email"
            type="email"
            placeholder="your@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={emailError ?? undefined}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                <polyline points="22,6 12,13 2,6" />
              </svg>
            }
          />

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={loading}>
            Gửi link đặt lại mật khẩu
          </Button>
        </form>

        <p className={styles.switchAuth}>
          Nhớ mật khẩu rồi?{' '}
          <Link href="/login" className={styles.switchLink}>Đăng nhập</Link>
        </p>
      </div>
    </div>
  );
}

export default function ForgotPasswordPage() {
  return (
    <Suspense fallback={<div className={styles.page} />}>
      <ForgotPasswordPageContent />
    </Suspense>
  );
}
