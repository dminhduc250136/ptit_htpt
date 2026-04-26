'use client';

import React, { useCallback, useEffect, useState } from 'react';
import styles from '../products/page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import { listAdminUsers, patchAdminUser, deleteAdminUser, type AdminUserPatchBody } from '@/services/users';
import type { User } from '@/types';

const thStyle = {
  padding: 'var(--space-3) var(--space-4)',
  fontSize: 'var(--text-label-md)',
  fontWeight: 700,
  textTransform: 'uppercase' as const,
  letterSpacing: '0.02em',
  color: 'var(--on-surface-variant)',
  textAlign: 'left' as const,
};

export default function AdminUsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [editTarget, setEditTarget] = useState<User | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [editForm, setEditForm] = useState<AdminUserPatchBody>({ fullName: '', phone: '', roles: 'USER' });
  const { showToast } = useToast();

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const resp = await listAdminUsers();
      setUsers(resp?.content ?? []);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openEditModal = (user: User) => {
    setEditForm({
      fullName: user.fullName ?? '',
      phone: user.phone ?? '',
      roles: user.roles ?? 'USER',
    });
    setEditTarget(user);
  };

  const handleSaveUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editTarget) return;
    setSaving(true);
    try {
      await patchAdminUser(editTarget.id, {
        fullName: editForm.fullName || undefined,
        phone: editForm.phone || undefined,
        roles: editForm.roles || undefined,
      });
      showToast('Thông tin tài khoản đã được cập nhật', 'success');
      setEditTarget(null);
      await load();
    } catch {
      showToast('Không thể cập nhật tài khoản. Vui lòng thử lại', 'error');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteAdminUser(deleteTarget);
      showToast('Tài khoản đã được xóa', 'success');
      setDeleteTarget(null);
      await load();
    } catch {
      showToast('Không thể xóa tài khoản. Vui lòng thử lại', 'error');
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý tài khoản</h1>
        <span style={{ fontSize: 'var(--text-body-sm)', color: 'var(--on-surface-variant)' }}>
          {users.length} tài khoản
        </span>
      </div>

      <div className={styles.tableWrapper}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: 'var(--surface-container-low)' }}>
              <th style={thStyle}>Họ tên</th>
              <th style={thStyle}>Email</th>
              <th style={thStyle}>Điện thoại</th>
              <th style={thStyle}>Vai trò</th>
              <th style={thStyle}>Ngày tạo</th>
              <th style={thStyle}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {/* Loading skeleton */}
            {loading && [...Array(5)].map((_, i) => (
              <tr key={i}>
                <td colSpan={6}>
                  <div className="skeleton" style={{ height: 60, borderRadius: 'var(--radius-md)' }} />
                </td>
              </tr>
            ))}
            {/* Error */}
            {!loading && failed && (
              <tr>
                <td colSpan={6}>
                  <RetrySection onRetry={load} loading={loading} />
                </td>
              </tr>
            )}
            {/* Empty */}
            {!loading && !failed && users.length === 0 && (
              <tr>
                <td colSpan={6}>
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-7) 0' }}>
                    <h3 style={{ fontSize: 'var(--text-title-lg)', color: 'var(--on-surface)' }}>Chưa có tài khoản nào</h3>
                    <p style={{ fontSize: 'var(--text-body-md)', color: 'var(--on-surface-variant)' }}>Khi có tài khoản đăng ký, chúng sẽ hiển thị tại đây</p>
                  </div>
                </td>
              </tr>
            )}
            {/* Data rows */}
            {!loading && !failed && users.map(u => (
              <tr key={u.id} style={{ borderBottom: '1px solid rgba(195,198,214,0.08)' }}>
                {/* Họ tên: fullName fallback username per D-09 */}
                <td className={styles.tdBold} style={{ padding: 'var(--space-3) var(--space-4)' }}>
                  {u.fullName && u.fullName.trim() ? u.fullName : u.username}
                </td>
                <td style={{ padding: 'var(--space-3) var(--space-4)' }}>{u.email}</td>
                {/* Điện thoại: phone fallback "—" per D-09 */}
                <td style={{ padding: 'var(--space-3) var(--space-4)' }}>
                  {u.phone && u.phone.trim() ? u.phone : '—'}
                </td>
                {/* Vai trò: ADMIN → hot badge, USER → default badge */}
                <td style={{ padding: 'var(--space-3) var(--space-4)' }}>
                  <Badge variant={u.roles === 'ADMIN' ? 'hot' : 'default'}>
                    {u.roles === 'ADMIN' ? 'Admin' : 'Khách hàng'}
                  </Badge>
                </td>
                <td className={styles.tdMuted} style={{ padding: 'var(--space-3) var(--space-4)' }}>
                  {u.createdAt ? new Date(u.createdAt).toLocaleDateString('vi-VN') : '—'}
                </td>
                <td style={{ padding: 'var(--space-3) var(--space-4)', display: 'flex', gap: 'var(--space-2)' }}>
                  <button className={styles.actionBtn} aria-label="Chỉnh sửa tài khoản"
                    onClick={() => openEditModal(u)}>✏️</button>
                  {/* Ẩn nút xóa cho ADMIN user — T-07-06-02 */}
                  {u.roles !== 'ADMIN' && (
                    <button className={styles.actionBtn} aria-label="Xóa tài khoản"
                      onClick={() => setDeleteTarget(u.id)}>🗑️</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Edit modal — D-10 UserEditModal */}
      {editTarget && (
        <div className={styles.overlay} onClick={() => setEditTarget(null)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>Chỉnh sửa tài khoản</h3>
              <button className={styles.closeBtn} onClick={() => setEditTarget(null)}>✕</button>
            </div>
            <form className={styles.modalForm} onSubmit={handleSaveUser}>
              <Input
                label="Họ và tên"
                placeholder="Nguyễn Văn A"
                value={editForm.fullName ?? ''}
                onChange={e => setEditForm(p => ({ ...p, fullName: e.target.value }))}
                fullWidth
              />
              <Input
                label="Số điện thoại"
                placeholder="0901 234 567"
                value={editForm.phone ?? ''}
                onChange={e => setEditForm(p => ({ ...p, phone: e.target.value }))}
                fullWidth
              />
              <div>
                <label style={{ fontSize: 'var(--text-body-md)', marginBottom: 'var(--space-2)', display: 'block' }}>
                  Vai trò
                </label>
                <select
                  value={editForm.roles ?? 'USER'}
                  onChange={e => setEditForm(p => ({ ...p, roles: e.target.value }))}
                  style={{ width: '100%', padding: 'var(--space-3)', borderRadius: 'var(--radius-lg)', border: '1.5px solid rgba(195,198,214,0.2)', fontSize: 'var(--text-body-md)', fontFamily: 'var(--font-family-body)', background: 'var(--surface-container-lowest)', cursor: 'pointer' }}
                >
                  <option value="USER">Khách hàng</option>
                  <option value="ADMIN">Quản trị viên</option>
                </select>
              </div>
              <div className={styles.modalActions}>
                <Button variant="secondary" type="button" onClick={() => setEditTarget(null)}>Hủy</Button>
                <Button type="submit" loading={saving}>Lưu thay đổi</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete confirm modal */}
      {deleteTarget && (
        <div className={styles.overlay} onClick={() => setDeleteTarget(null)}>
          <div className={styles.confirmModal} onClick={e => e.stopPropagation()}>
            <h3 className={styles.confirmTitle}>Xác nhận xóa tài khoản</h3>
            <p className={styles.confirmDesc}>Bạn có chắc chắn muốn xóa tài khoản này? Hành động này không thể hoàn tác.</p>
            <div className={styles.confirmActions}>
              <Button variant="secondary" onClick={() => setDeleteTarget(null)}>Hủy</Button>
              <Button variant="danger" onClick={handleDelete}>Xóa tài khoản</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
