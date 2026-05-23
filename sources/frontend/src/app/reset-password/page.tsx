'use client';

import React, { Suspense, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import styles from '../login/page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import { resetPassword } from '@/services/auth';
import { ApiError } from '@/services/errors';

/**
 * Phase 27 / Plan 27-05 (MAIL-02): Trang đặt lại mật khẩu.
 *
 * - Suspense + useSearchParams đọc ?token.
 * - Không token → status card lỗi (không render form).
 * - Form 2 password field: mật khẩu mới + xác nhận.
 * - Validate: độ dài >= 6 + 2 field khớp nhau.
 * - Submit → resetPassword(token, newPassword).
 * - Token hết hạn (400/410) → status card lỗi.
 * - Success → panel thành công + CTA đăng nhập.
 * - Tái dụng login/page.module.css.
 *
 * UI-SPEC Copywriting Contract: /reset-password.
 */

type ResetState = 'form' | 'success' | 'token-invalid';

function ResetPasswordContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token');

  const [formState, setFormState] = useState<ResetState>(token ? 'form' : 'token-invalid');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [errors, setErrors] = useState<{ newPassword?: string; confirmPassword?: string }>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: typeof errors = {};
    if (!newPassword) newErrors.newPassword = 'Vui lòng nhập mật khẩu mới';
    else if (newPassword.length < 6) newErrors.newPassword = 'Mật khẩu ít nhất 6 ký tự';
    if (newPassword !== confirmPassword) newErrors.confirmPassword = 'Mật khẩu không khớp';

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});
    setApiError(null);
    setLoading(true);
    try {
      await resetPassword(token!, newPassword);
      setFormState('success');
    } catch (err: unknown) {
      if (err instanceof ApiError && (err.status === 400 || err.status === 410)) {
        setFormState('token-invalid');
      } else {
        setApiError('Có lỗi xảy ra. Vui lòng thử lại sau.');
      }
    } finally {
      setLoading(false);
    }
  };

  // Token không hợp lệ / hết hạn
  if (formState === 'token-invalid') {
    return (
      <div className={styles.page}>
        <div className={styles.formContainer}>
          <div className={styles.formHeader}>
            <h1 className={styles.formTitle}>Link đặt lại mật khẩu không hợp lệ</h1>
            <p className={styles.formSubtitle}>
              Link đã hết hạn hoặc đã được sử dụng. Vui lòng yêu cầu đặt lại mật khẩu mới.
            </p>
          </div>
          <Link href="/forgot-password">
            <Button size="lg" fullWidth style={{ marginTop: 'var(--space-4)' }}>
              Quên mật khẩu
            </Button>
          </Link>
          <p className={styles.switchAuth}>
            <Link href="/login" className={styles.switchLink}>Quay lại đăng nhập</Link>
          </p>
        </div>
      </div>
    );
  }

  // Thành công
  if (formState === 'success') {
    return (
      <div className={styles.page}>
        <div className={styles.formContainer}>
          <div className={styles.formHeader}>
            <h1 className={styles.formTitle}>Mật khẩu đã được đặt lại!</h1>
            <p className={styles.formSubtitle}>Bạn có thể đăng nhập bằng mật khẩu mới.</p>
          </div>
          <Link href="/login">
            <Button size="lg" fullWidth style={{ marginTop: 'var(--space-4)' }}>
              Đăng nhập ngay
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  // Form chính
  const errorCount = Object.keys(errors).length;
  return (
    <div className={styles.page}>
      <div className={styles.formContainer}>
        <div className={styles.formHeader}>
          <h1 className={styles.formTitle}>Đặt lại mật khẩu</h1>
          <p className={styles.formSubtitle}>Nhập mật khẩu mới cho tài khoản của bạn</p>
        </div>

        {apiError && <Banner count={1}>{apiError}</Banner>}
        {errorCount > 0 && <Banner count={errorCount} />}

        <form className={styles.form} onSubmit={handleSubmit} aria-busy={loading}>
          <Input
            label="Mật khẩu mới"
            type="password"
            placeholder="Ít nhất 6 ký tự"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            error={errors.newPassword}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            }
          />

          <Input
            label="Xác nhận mật khẩu"
            type="password"
            placeholder="Nhập lại mật khẩu mới"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            error={errors.confirmPassword}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            }
          />

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={loading}>
            Đặt lại mật khẩu
          </Button>
        </form>

        <p className={styles.switchAuth}>
          <Link href="/login" className={styles.switchLink}>Quay lại đăng nhập</Link>
        </p>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<div className={styles.page} />}>
      <ResetPasswordContent />
    </Suspense>
  );
}
