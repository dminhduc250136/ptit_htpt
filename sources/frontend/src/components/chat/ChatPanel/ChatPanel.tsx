'use client';

/**
 * ChatPanel — modal-style chat surface anchored bottom-right (desktop) or fullscreen (mobile).
 *
 * Phase 22 Plan 05, decisions D-10..D-13.
 * - ESC closes (mirrors Modal a11y pattern from components/ui/Modal)
 * - body scroll lock while open
 * - auto-scroll list to bottom on new tokens / messages
 * - empty state: 3 quick-reply chips (D-11)
 * - error banner with "Thử lại" (D-13)
 * - assistant streaming cursor blink on last bubble (D-12)
 *
 * Threat T-22-07 (mid-stream resource leak): if user closes panel while streaming,
 * the unmount + AbortController in useChat fires and closes the reader.
 */
import { useEffect, useRef } from 'react';
import styles from './ChatPanel.module.css';
import MessageBubble from '../MessageBubble/MessageBubble';
import ChatComposer from '../ChatComposer/ChatComposer';
import QuickReplyChips from '../QuickReplyChips/QuickReplyChips';
import SessionsSidebar from '../SessionsSidebar/SessionsSidebar';
import { useChat } from '../useChat';

interface Props {
  open: boolean;
  onClose: () => void;
}

export default function ChatPanel({ open, onClose }: Props) {
  const chat = useChat(open);
  const listRef = useRef<HTMLDivElement>(null);

  // ESC closes; lock body scroll while open (mobile fullscreen).
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, onClose]);

  // Abort any in-flight stream when the panel closes (T-22-07).
  useEffect(() => {
    if (!open) chat.abortStream();
  }, [open, chat]);

  // Auto-scroll to last bubble while streaming.
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [chat.messages]);

  if (!open) return null;
  const showQuickChips = chat.messages.length === 0 && !chat.isStreaming;

  return (
    <div className={styles.backdrop} onClick={onClose} role="presentation">
      <div
        className={styles.panel}
        role="dialog"
        aria-modal="true"
        aria-label="Trợ lý mua sắm"
        onClick={(e) => e.stopPropagation()}
      >
        <header className={styles.header}>
          <strong className={styles.title}>Trợ lý mua sắm</strong>
          <button
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            className={styles.closeBtn}
          >
            ✕
          </button>
        </header>
        <div className={styles.body}>
          <SessionsSidebar
            sessions={chat.sessions}
            activeId={chat.activeSessionId}
            onPick={chat.openSession}
            onNew={chat.newSession}
          />
          <div className={styles.main}>
            <div className={styles.list} ref={listRef} aria-live="polite">
              {showQuickChips && (
                <div className={styles.empty}>
                  <p className={styles.emptyText}>
                    Chào bạn! Hỏi mình bất cứ điều gì về sản phẩm.
                  </p>
                  <QuickReplyChips onPick={chat.sendMessage} />
                </div>
              )}
              {chat.messages.map((m, i) => (
                <MessageBubble
                  key={i}
                  role={m.role}
                  content={m.content}
                  isStreaming={
                    chat.isStreaming &&
                    i === chat.messages.length - 1 &&
                    m.role === 'assistant'
                  }
                />
              ))}
              {chat.isStreaming && (
                <div className={styles.typing} aria-live="polite">
                  Đang trả lời…
                </div>
              )}
              {chat.lastError && (
                <div className={styles.error} role="alert">
                  <span>Lỗi: {chat.lastError}</span>
                  <button
                    type="button"
                    onClick={chat.retryLast}
                    disabled={chat.isStreaming}
                    className={styles.retryBtn}
                  >
                    Thử lại
                  </button>
                </div>
              )}
            </div>
            <ChatComposer disabled={chat.isStreaming} onSend={chat.sendMessage} />
          </div>
        </div>
      </div>
    </div>
  );
}
