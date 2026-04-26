'use client';

import React, { useState } from 'react';
import styles from '../products/page.module.css';
import Button from '@/components/ui/Button/Button';
import Badge from '@/components/ui/Badge/Badge';
import type { User } from '@/types';

// TODO Phase 7 (UI-04): wire to listUsers(admin scope) qua gateway
const _stubUsers: User[] = [];

export default function AdminUsersPage() {
  const [users, setUsers] = useState(_stubUsers);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);

  const handleDelete = () => {
    if (deleteTarget) {
      setUsers(prev => prev.filter(u => u.id !== deleteTarget));
      setDeleteTarget(null);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý tài khoản</h1>
        <span className={styles.count}>{users.length} tài khoản</span>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead><tr><th>Họ tên</th><th>Email</th><th>Điện thoại</th><th>Vai trò</th><th>Ngày tạo</th><th>Thao tác</th></tr></thead>
          <tbody>
            {users.map(u => (
              <tr key={u.id}>
                <td className={styles.tdBold}>{u.fullName}</td>
                <td>{u.email}</td>
                <td>{u.phone || '—'}</td>
                <td><Badge variant={u.role === 'ADMIN' ? 'hot' : 'default'}>{u.role === 'ADMIN' ? 'Admin' : 'Khách hàng'}</Badge></td>
                <td className={styles.tdMuted}>{new Date(u.createdAt).toLocaleDateString('vi-VN')}</td>
                <td>
                  <div className={styles.actions}>
                    <button className={styles.actionBtn} title="Chỉnh sửa">✏️</button>
                    {u.role !== 'ADMIN' && <button className={`${styles.actionBtn} ${styles.deleteBtn}`} title="Xóa" onClick={() => setDeleteTarget(u.id)}>🗑️</button>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Popup: Confirm Delete */}
      {deleteTarget && (
        <div className={styles.overlay} onClick={() => setDeleteTarget(null)}>
          <div className={styles.confirmModal} onClick={e => e.stopPropagation()}>
            <div className={styles.confirmIcon}>
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--error)" strokeWidth="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            </div>
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
