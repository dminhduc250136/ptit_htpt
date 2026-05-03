'use client';

/**
 * SuggestReplyModal — admin-only modal for AI-generated reply suggestions.
 *
 * Displays loading → editable textarea + "Sao chép" button. Disclaimer mandates
 * admin review before sending (D-07: NO auto-send, manual confirm only).
 *
 * a11y: role="dialog" + aria-modal + ESC closes + backdrop click closes.
 * T-22-01 mitigation: textarea uses controlled `value` only — never
 * dangerouslySetInnerHTML, so AI output cannot inject markup.
 */

import { useEffect, useState } from 'react';
import styles from './SuggestReplyModal.module.css';

interface Props {
  open: boolean;
  onClose: () => void;
  loading: boolean;
  initialText: string;
  error: string | null;
  onCopy: (text: string) => void;
}

export default function SuggestReplyModal({
  open,
  onClose,
  loading,
  initialText,
  error,
  onCopy,
}: Props) {
  const [text, setText] = useState(initialText);

  useEffect(() => {
    setText(initialText);
  }, [initialText]);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className={styles.backdrop} role="presentation" onClick={onClose}>
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-label="Gợi ý phản hồi AI"
        onClick={(e) => e.stopPropagation()}
      >
        <header className={styles.header}>
          <strong>Gợi ý phản hồi (AI)</strong>
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
          <p className={styles.disclaimer}>
            ⚠️ Vui lòng kiểm tra kỹ nội dung trước khi gửi cho khách. AI có thể
            nhầm lẫn — admin tự chịu trách nhiệm review thủ công.
          </p>
          {loading && <p className={styles.status}>Đang sinh gợi ý...</p>}
          {error && (
            <p className={styles.error} role="alert">
              Lỗi: {error}
            </p>
          )}
          {!loading && !error && (
            <textarea
              className={styles.textarea}
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={10}
              aria-label="Nội dung gợi ý phản hồi"
              data-testid="suggest-reply-textarea"
            />
          )}
        </div>
        <footer className={styles.footer}>
          <button type="button" onClick={onClose} className={styles.btnSecondary}>
            Đóng
          </button>
          <button
            type="button"
            disabled={loading || !!error || !text.trim()}
            onClick={() => onCopy(text)}
            data-testid="suggest-reply-copy"
            className={styles.btnPrimary}
          >
            Sao chép
          </button>
        </footer>
      </div>
    </div>
  );
}
