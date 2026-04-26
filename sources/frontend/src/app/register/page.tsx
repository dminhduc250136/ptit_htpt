'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styles from '../login/page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import { register } from '@/services/auth';
import { ApiError } from '@/services/errors';
import { useAuth } from '@/providers/AuthProvider';

export default function RegisterPage() {
  const router = useRouter();
  const { login: authLogin } = useAuth();

  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const update = (field: string, value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: Record<string, string> = {};
    if (!form.username.trim()) newErrors.username = 'Vui lòng nhập tên đăng nhập';
    else if (form.username.trim().length < 3) newErrors.username = 'Tên đăng nhập ít nhất 3 ký tự';
    if (!form.email.trim()) newErrors.email = 'Vui lòng nhập email';
    else if (!/\S+@\S+\.\S+/.test(form.email)) newErrors.email = 'Email không hợp lệ';
    if (!form.password) newErrors.password = 'Vui lòng nhập mật khẩu';
    else if (form.password.length < 6) newErrors.password = 'Mật khẩu ít nhất 6 ký tự';
    if (form.password !== form.confirmPassword)
      newErrors.confirmPassword = 'Mật khẩu không khớp';

    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    setErrors({});
    setApiError(null);
    setLoading(true);
    try {
      const data = await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      });
      // D-04: auth.register() đã gọi setTokens + setUserRole nội bộ
      authLogin({ id: data.user.id, email: data.user.email, name: data.user.username ?? data.user.email });
      router.replace('/');  // D-04: redirect về trang chủ (không qua /login)
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.status === 409) {
          // Per UI-SPEC: 409 → highlight field cụ thể
          const msg = err.message ?? '';
          if (msg.toLowerCase().includes('username')) {
            setErrors({ username: 'Tên đăng nhập này đã được sử dụng' });
          } else if (msg.toLowerCase().includes('email')) {
            setErrors({ email: 'Email này đã được đăng ký. Đăng nhập' });
          } else {
            setApiError(err.message ?? 'Có lỗi xảy ra, vui lòng thử lại');
          }
        } else {
          setApiError('Có lỗi xảy ra, vui lòng thử lại');
        }
      } else {
        setApiError('Có lỗi xảy ra, vui lòng thử lại');
      }
    } finally {
      setLoading(false);
    }
  };

  const errorCount = Object.keys(errors).length;

  return (
    <div className={styles.page}>
      <div className={styles.formContainer}>
        <div className={styles.formHeader}>
          <h1 className={styles.formTitle}>Tạo tài khoản</h1>
          <p className={styles.formSubtitle}>
            Đăng ký để trải nghiệm mua sắm đẳng cấp
          </p>
        </div>

        {apiError && <Banner count={1}>{apiError}</Banner>}
        {errorCount > 0 && <Banner count={errorCount} />}

        <form className={styles.form} onSubmit={handleSubmit}>
          <Input
            label="Tên đăng nhập"
            placeholder="ten_dang_nhap"
            type="text"
            value={form.username}
            onChange={(e) => update('username', e.target.value)}
            error={errors.username}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            }
          />

          <Input
            label="Email"
            type="email"
            placeholder="your@email.com"
            value={form.email}
            onChange={(e) => update('email', e.target.value)}
            error={errors.email}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
                <polyline points="22,6 12,13 2,6" />
              </svg>
            }
          />

          <Input
            label="Mật khẩu"
            type="password"
            placeholder="Ít nhất 6 ký tự"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            error={errors.password}
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
            placeholder="Nhập lại mật khẩu"
            value={form.confirmPassword}
            onChange={(e) => update('confirmPassword', e.target.value)}
            error={errors.confirmPassword}
            fullWidth
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            }
          />

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={loading}>
            Tạo tài khoản
          </Button>
        </form>

        <p className={styles.switchAuth}>
          Đã có tài khoản?{' '}
          <Link href="/login" className={styles.switchLink}>Đăng nhập</Link>
        </p>
      </div>
    </div>
  );
}
