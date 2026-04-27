'use client';

import { useState, useEffect, type FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import styles from './page.module.css';
import { changeMyPassword, getMe, patchMe } from '@/services/users';
import { isApiError } from '@/services/errors';
import { useToast } from '@/components/ui/Toast/Toast';
import { useAuth } from '@/providers/AuthProvider';

/**
 * Phase 9 / Plan 09-04 (AUTH-07 frontend) — Password change.
 * Phase 10 / Plan 10-03 (ACCT-03 frontend) — Profile Info (fullName, phone) + Avatar placeholder.
 *
 * Layout: 3 sections theo D-01 (dọc, single-page scroll, không tab):
 *   1. Profile Info (Phase 10) — fullName + phone editable, email read-only
 *   2. Avatar (Phase 10) — initials placeholder, ACCT-04 defer per D-08
 *   3. Security (Phase 9) — password change form, intact
 *
 * Refinement D-07: dùng useAuth().login(updatedUser) thay manual localStorage + router.refresh()
 * — AuthProvider đã có sẵn, viết localStorage 'userProfile' + cross-tab event tự động.
 */

// ============================================================
// Profile Info — zod schema (Phase 10 / ACCT-03)
// ============================================================
const profileSchema = z.object({
  fullName: z.string()
    .trim()
    .min(1, 'Vui lòng nhập họ tên')
    .max(120, 'Họ tên quá dài (tối đa 120 ký tự)'),
  phone: z.string()
    .regex(/^\+?[0-9\s-]{7,20}$/, 'Số điện thoại không hợp lệ')
    .or(z.literal('')),  // empty string OK (nullable optional)
});
type ProfileFormData = z.infer<typeof profileSchema>;

export default function SettingsPage() {
  // ============================================================
  // Phase 10: Profile Info hooks
  // ============================================================
  const { user, login } = useAuth();
  const { showToast } = useToast();

  const {
    register,
    handleSubmit: rhfHandleSubmit,
    formState: { errors, isSubmitting, isDirty },
    reset,
    setError,
  } = useForm<ProfileFormData>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: '', phone: '' },
  });

  const [profileEmail, setProfileEmail] = useState<string>('');

  useEffect(() => {
    let alive = true;
    getMe()
      .then(me => {
        if (!alive) return;
        reset({ fullName: me.fullName ?? '', phone: me.phone ?? '' });
        setProfileEmail(me.email ?? '');
      })
      .catch(() => { if (alive) showToast('Không tải được thông tin', 'error'); });
    return () => { alive = false; };
  }, [reset, showToast]);

  const onSubmitProfile = rhfHandleSubmit(async (data: ProfileFormData) => {
    try {
      const updated = await patchMe({ fullName: data.fullName, phone: data.phone || undefined });
      // Refinement D-07: useAuth().login() thay manual localStorage — AuthProvider handles write + cross-tab
      if (user) login({ ...user, name: updated.fullName ?? user.name });
      showToast('Đã cập nhật', 'success');
      reset({ fullName: updated.fullName ?? '', phone: updated.phone ?? '' });
    } catch (err) {
      if (isApiError(err) && err.fieldErrors?.length) {
        err.fieldErrors.forEach(fe => {
          if (fe.field === 'fullName' || fe.field === 'phone') {
            setError(fe.field, { message: fe.message });
          }
        });
      } else if (isApiError(err)) {
        showToast(err.message ?? 'Có lỗi xảy ra', 'error');
      } else {
        showToast('Có lỗi xảy ra, vui lòng thử lại', 'error');
      }
    }
  });

  // ============================================================
  // Phase 9: Password change state (INTACT — không sửa)
  // ============================================================
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [oldError, setOldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  function validate(): string | null {
    if (!oldPassword) return 'Vui lòng nhập mật khẩu hiện tại';
    if (newPassword.length < 8) return 'Mật khẩu mới phải có ít nhất 8 ký tự';
    if (!/[A-Za-z]/.test(newPassword)) return 'Mật khẩu mới phải có ít nhất 1 chữ cái';
    if (!/\d/.test(newPassword)) return 'Mật khẩu mới phải có ít nhất 1 số';
    if (newPassword !== confirmPassword) return 'Xác nhận mật khẩu không khớp';
    return null;
  }

  const isValid = validate() === null;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setOldError(null);
    setFormError(null);
    setSuccessMsg(null);
    const v = validate();
    if (v) {
      setFormError(v);
      return;
    }
    setSubmitting(true);
    try {
      await changeMyPassword({ oldPassword, newPassword });
      setSuccessMsg('Đã đổi mật khẩu');
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err) {
      // Error shape (Plan 09-03 backend): ApiError với .code (không phải .errorCode)
      // 422 + code AUTH_INVALID_PASSWORD → field-level error tại "Mật khẩu hiện tại"
      if (isApiError(err) && err.status === 422 && err.code === 'AUTH_INVALID_PASSWORD') {
        setOldError('Mật khẩu hiện tại không đúng');
      } else if (isApiError(err)) {
        setFormError(err.message ?? 'Có lỗi xảy ra, vui lòng thử lại');
      } else {
        setFormError('Có lỗi xảy ra, vui lòng thử lại');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Cài đặt tài khoản</h1>

      {/* Section 1: Profile Info — NEW Phase 10 / ACCT-03 */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Thông tin cá nhân</h2>
        <form onSubmit={onSubmitProfile} className={styles.form} noValidate>
          <div className={styles.field}>
            <label htmlFor="fullName" className={styles.label}>Họ và tên</label>
            <input id="fullName" className={styles.input} {...register('fullName')} />
            {errors.fullName && <p className={styles.fieldError}>{errors.fullName.message}</p>}
          </div>
          <div className={styles.field}>
            <label htmlFor="phone" className={styles.label}>Số điện thoại</label>
            <input id="phone" className={styles.input} {...register('phone')} />
            {errors.phone && <p className={styles.fieldError}>{errors.phone.message}</p>}
          </div>
          <div className={styles.field}>
            <label htmlFor="email" className={styles.label}>Email</label>
            <input
              id="email"
              className={`${styles.input} ${styles.readonly}`}
              value={profileEmail}
              disabled
              readOnly
            />
          </div>
          <button
            type="submit"
            className={styles.submit}
            disabled={isSubmitting || !isDirty}
          >
            {isSubmitting ? 'Đang lưu...' : 'Lưu thay đổi'}
          </button>
        </form>
      </section>

      {/* Section 2: Avatar — placeholder, ACCT-04 defer per D-08 */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Ảnh đại diện</h2>
        <div className={styles.avatarPlaceholder} aria-label="Ảnh đại diện (chữ cái đầu)">
          {(profileEmail || 'U').charAt(0).toUpperCase()}
        </div>
        <p className={styles.comingSoon}>Tính năng tải ảnh đại diện sẽ có trong bản cập nhật sau.</p>
      </section>

      {/* Section 3: Security — EXISTING Phase 9 password form (giữ nguyên) */}
      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Đổi mật khẩu</h2>
        <form onSubmit={handleSubmit} className={styles.form} noValidate>
          <div className={styles.field}>
            <label htmlFor="oldPassword" className={styles.label}>Mật khẩu hiện tại</label>
            <input
              id="oldPassword"
              type="password"
              className={styles.input}
              value={oldPassword}
              onChange={(e) => { setOldPassword(e.target.value); setOldError(null); }}
              autoComplete="current-password"
              required
            />
            {oldError && <p className={styles.fieldError} data-testid="oldPasswordError">{oldError}</p>}
          </div>
          <div className={styles.field}>
            <label htmlFor="newPassword" className={styles.label}>Mật khẩu mới</label>
            <input
              id="newPassword"
              type="password"
              className={styles.input}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              required
              minLength={8}
            />
            <p className={styles.hint}>Tối thiểu 8 ký tự, gồm ít nhất 1 chữ cái + 1 số.</p>
          </div>
          <div className={styles.field}>
            <label htmlFor="confirmPassword" className={styles.label}>Xác nhận mật khẩu mới</label>
            <input
              id="confirmPassword"
              type="password"
              className={styles.input}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              required
            />
          </div>
          {formError && <p className={styles.formError} data-testid="formError">{formError}</p>}
          {successMsg && <p className={styles.success} data-testid="successMsg" role="status">{successMsg}</p>}
          <button
            type="submit"
            className={styles.submit}
            disabled={submitting || !isValid}
            data-testid="submitPassword"
          >
            {submitting ? 'Đang đổi...' : 'Đổi mật khẩu'}
          </button>
        </form>
      </section>
    </div>
  );
}
