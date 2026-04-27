'use client';

import { useState, type FormEvent } from 'react';
import styles from './page.module.css';
import { changeMyPassword } from '@/services/users';
import { isApiError } from '@/services/errors';

/**
 * Phase 9 / Plan 09-04 (AUTH-07 frontend).
 * Page mới /profile/settings — Phase 10 sẽ extend với fullName/phone/avatar.
 * Phase 9 chỉ ship password change form (3 fields).
 *
 * Validation client-side (CONTEXT.md Claude's Discretion):
 * - newPassword min 8 + ít nhất 1 letter + 1 number
 * - confirmPassword === newPassword
 *
 * Error mapping (09-03-SUMMARY: backend trả field `code`, không phải `errorCode`):
 * - 422 code AUTH_INVALID_PASSWORD → field-level error tại "Mật khẩu hiện tại"
 * - 400 (validation backend) → form-level generic error
 *
 * D-10: KHÔNG force logout sau success — chỉ inline message + reset form.
 * Toast lib defer sang Phase 10 — dùng inline success message.
 */
export default function SettingsPage() {
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
      // 422 + code "AUTH_INVALID_PASSWORD" → field-level error tại "Mật khẩu hiện tại"
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
