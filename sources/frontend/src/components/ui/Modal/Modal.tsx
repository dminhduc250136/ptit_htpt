'use client';

/**
 * Modal — shared modal shell (UI-SPEC Surface 2).
 *
 * Used by checkout page for stock-shortage + payment-failure conflict modals.
 * Copy is supplied by caller (UI-SPEC §Copywriting Contract locks the exact
 * Vietnamese strings per variant).
 *
 * Accessibility contract:
 *   - role="dialog" + aria-modal="true"
 *   - aria-labelledby (title) + aria-describedby (body)
 *   - Esc closes
 *   - Backdrop click closes
 *   - Body scroll locked while open
 *   - First focusable element focused on open
 */

import React, { useEffect, useId, useRef } from 'react';
import styles from './Modal.module.css';
import Button from '../Button/Button';

interface ModalAction {
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary' | 'danger';
  loading?: boolean;
}

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: React.ReactNode;
  children: React.ReactNode;
  primaryAction: ModalAction;
  secondaryAction?: ModalAction;
}

export default function Modal({
  open,
  onClose,
  title,
  children,
  primaryAction,
  secondaryAction,
}: ModalProps) {
  const titleId = useId();
  const bodyId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);

  // Body scroll lock + Esc handler + initial focus
  useEffect(() => {
    if (!open) return;
    const prevOverflow = document.documentElement.style.overflow;
    document.documentElement.style.overflow = 'hidden';

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);

    // Focus first focusable (close button) on open
    const focusable = dialogRef.current?.querySelector<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
    );
    focusable?.focus();

    return () => {
      document.documentElement.style.overflow = prevOverflow;
      window.removeEventListener('keydown', onKey);
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className={styles.backdrop} onClick={onClose} aria-hidden="true">
      <div
        ref={dialogRef}
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={bodyId}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          className={styles.close}
          type="button"
          aria-label="Đóng"
          onClick={onClose}
        >
          ✕
        </button>
        <h2 id={titleId} className={`${styles.title} text-title-lg`}>
          {title}
        </h2>
        <div id={bodyId} className={`${styles.body} text-body-lg`}>
          {children}
        </div>
        <div className={styles.footer}>
          {secondaryAction && (
            <Button
              variant={secondaryAction.variant ?? 'secondary'}
              size="md"
              loading={secondaryAction.loading}
              onClick={secondaryAction.onClick}
            >
              {secondaryAction.label}
            </Button>
          )}
          <Button
            variant={primaryAction.variant ?? 'primary'}
            size="md"
            loading={primaryAction.loading}
            onClick={primaryAction.onClick}
          >
            {primaryAction.label}
          </Button>
        </div>
      </div>
    </div>
  );
}
