'use client';

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import styles from './Toast.module.css';

interface ToastItem { id: number; message: string; type: 'success' | 'error' | 'info' | 'warning'; }

const ToastContext = createContext<{ showToast: (message: string, type?: 'success' | 'error' | 'info' | 'warning') => void }>({
  showToast: () => {},
});

export const useToast = () => useContext(ToastContext);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const showToast = useCallback((message: string, type: 'success' | 'error' | 'info' | 'warning' = 'success') => {
    const id = Date.now();
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 3500);
  }, []);

  /**
   * Phase 18 / D-14: lắng nghe CustomEvent 'cart:merge-failed' do AuthProvider.login()
   * dispatch khi mergeGuestCartToServer() thất bại. Hiển thị toast warning cho user.
   * Pattern: AuthProvider không thể gọi useToast() trực tiếp vì nằm NGOÀI ToastProvider
   * trong cây component — dùng window event để bridge.
   */
  useEffect(() => {
    function onCartMergeFailed(e: Event) {
      const detail = (e as CustomEvent<{ message: string } | undefined>).detail;
      const message = detail?.message ?? 'Không đồng bộ được giỏ hàng cũ — vui lòng kiểm tra lại';
      showToast(message, 'warning');
    }
    window.addEventListener('cart:merge-failed', onCartMergeFailed);
    return () => window.removeEventListener('cart:merge-failed', onCartMergeFailed);
  }, [showToast]);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className={styles.container}>
        {toasts.map(t => (
          <div key={t.id} className={`${styles.toast} ${styles[t.type]}`}>
            <span className={styles.icon}>
              {t.type === 'success' ? '✓' : t.type === 'error' ? '✗' : t.type === 'warning' ? '⚠' : 'ℹ'}
            </span>
            <span className={styles.message}>{t.message}</span>
            <button className={styles.close} aria-label="Đóng thông báo" onClick={() => setToasts(prev => prev.filter(x => x.id !== t.id))}>✕</button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
