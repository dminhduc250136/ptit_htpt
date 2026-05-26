'use client';

/**
 * SessionsSidebar — list of past chat sessions + "new chat" button.
 *
 * Phase 22 Plan 05.
 */
import styles from './SessionsSidebar.module.css';
import type { ChatSessionDto } from '@/services/chat';

interface Props {
  sessions: ChatSessionDto[];
  activeId: number | null;
  onPick: (id: number) => void;
  onNew: () => void;
}

export default function SessionsSidebar({ sessions, activeId, onPick, onNew }: Props) {
  return (
    <aside className={styles.sidebar} aria-label="Lịch sử chat">
      <button type="button" className={styles.newBtn} onClick={onNew}>
        + Đoạn chat mới
      </button>
      <ul className={styles.list}>
        {sessions.map((s) => (
          <li key={s.id}>
            <button
              type="button"
              className={`${styles.item} ${s.id === activeId ? styles.active : ''}`}
              onClick={() => onPick(s.id)}
              title={s.title}
            >
              {s.title || 'Đoạn chat'}
            </button>
          </li>
        ))}
        {sessions.length === 0 && (
          <li className={styles.empty}>Chưa có đoạn chat nào</li>
        )}
      </ul>
    </aside>
  );
}
