'use client';

/**
 * ChatComposer — textarea + send button. Disabled while streaming (D-12).
 * Hard cap 2000 chars per D-25.
 *
 * Phase 22 Plan 05.
 */
import { useState, type FormEvent, type KeyboardEvent } from 'react';
import styles from './ChatComposer.module.css';

interface Props {
  disabled: boolean;
  onSend: (text: string) => void;
}

export default function ChatComposer({ disabled, onSend }: Props) {
  const [value, setValue] = useState('');

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (disabled) return;
    const t = value.trim();
    if (!t) return;
    onSend(t);
    setValue('');
  };

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      submit(e as unknown as FormEvent);
    }
  };

  return (
    <form onSubmit={submit} className={styles.form}>
      <textarea
        value={value}
        onChange={(e) => setValue(e.target.value.slice(0, 2000))}
        onKeyDown={onKey}
        disabled={disabled}
        maxLength={2000}
        placeholder={disabled ? 'Đang trả lời…' : 'Nhập tin nhắn (Enter để gửi)'}
        className={styles.textarea}
        rows={2}
        aria-label="Soạn tin nhắn"
      />
      <button
        type="submit"
        disabled={disabled || !value.trim()}
        className={styles.send}
        aria-label="Gửi"
      >
        Gửi
      </button>
    </form>
  );
}
