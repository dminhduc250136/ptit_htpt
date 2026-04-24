'use client';

// NOTE: auth backend endpoints /auth/register etc. not yet implemented —
// see 04-WAVE-STATUS.md. Mock flow preserved per user decision (Wave 2 deviation
// approved): we still populate localStorage tokens + auth_present cookie + the
// AuthProvider state so middleware admits the user post-register.
// Swap mock submit for `services/auth.register()` once backend exposes /api/users/auth/register.

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styles from '../login/page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Banner from '@/components/ui/Banner/Banner';
import { setTokens } from '@/services/token';
import { useAuth } from '@/providers/AuthProvider';

export default function RegisterPage() {
  const router = useRouter();
  const { login: authLogin } = useAuth();

  const [form, setForm] = useState({
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);

  const update = (field: string, value: string) =>
    setForm((prev) => ({ ...prev, [field]: value }));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const newErrors: Record<string, string> = {};
    if (!form.fullName.trim()) newErrors.fullName = 'Vui lòng nhập họ tên';
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
    setLoading(true);
    // Mock register: backend /auth/register not shipped yet. Simulate a short
    // delay, then auto-login the user so the rest of the flow (cart/checkout)
    // can be walked through without a separate sign-in step.
    await new Promise((r) => setTimeout(r, 800));
    setTokens('mock-access-token', 'mock-refresh-token');
    authLogin({
      id: 'mock-user',
      email: form.email,
      name: form.fullName,
    });
    setLoading(false);
    router.replace('/');
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

        {errorCount > 0 && <Banner count={errorCount} />}

        <form className={styles.form} onSubmit={handleSubmit}>
          <Input
            label="Họ và tên"
            placeholder="Nguyễn Văn A"
            value={form.fullName}
            onChange={(e) => update('fullName', e.target.value)}
            error={errors.fullName}
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
            label="Số điện thoại"
            type="tel"
            placeholder="0912 345 678"
            value={form.phone}
            onChange={(e) => update('phone', e.target.value)}
            fullWidth
            helperText="Không bắt buộc"
            icon={
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72" />
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

          <Button type="submit" size="lg" fullWidth loading={loading}>
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
