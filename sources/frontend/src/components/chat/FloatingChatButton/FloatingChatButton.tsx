'use client';

/**
 * FloatingChatButton — global chat entry-point mounted from app/layout.tsx.
 *
 * Phase 22 Plan 05, decision D-09.
 * - Authenticated user → circular FAB; opens ChatPanel.
 * - Guest → pill-shape "Đăng nhập để chat" link to `/login?next=<current>`.
 * - Hidden on `/login` and `/register` to avoid recursive UX.
 */
import { useState } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import styles from './FloatingChatButton.module.css';
import ChatPanel from '../ChatPanel/ChatPanel';
import { useAuth } from '@/providers/AuthProvider';

export default function FloatingChatButton() {
  const [open, setOpen] = useState(false);
  const { isAuthenticated } = useAuth();
  const pathname = usePathname();

  // Hide on auth screens — user is already heading there.
  if (pathname?.startsWith('/login') || pathname?.startsWith('/register')) {
    return null;
  }

  if (!isAuthenticated) {
    const next = encodeURIComponent(pathname ?? '/');
    return (
      <Link
        href={`/login?next=${next}`}
        className={`${styles.button} ${styles.guest}`}
        aria-label="Đăng nhập để chat"
        data-testid="chat-cta-guest"
      >
        <span className={styles.icon} aria-hidden="true">💬</span>
        <span className={styles.label}>Đăng nhập để chat</span>
      </Link>
    );
  }

  return (
    <>
      <button
        type="button"
        className={`${styles.button} ${styles.fab}`}
        onClick={() => setOpen(true)}
        aria-label="Mở chat"
        data-testid="chat-fab"
      >
        <span className={styles.icon} aria-hidden="true">💬</span>
      </button>
      <ChatPanel open={open} onClose={() => setOpen(false)} />
    </>
  );
}
