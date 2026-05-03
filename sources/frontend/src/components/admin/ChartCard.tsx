/**
 * Phase 19 / Plan 19-04 — Generic 3-state chart card wrapper (D-14).
 * Mirror KpiCard pattern (admin/page.tsx). Loading skeleton / success / error + retry.
 */
'use client';
import { ReactNode } from 'react';
import styles from './ChartCard.module.css';

export type CardState<T> = {
  status: 'loading' | 'success' | 'error';
  data?: T;
  error?: string;
};

interface Props<T> {
  title: string;
  state: CardState<T>;
  renderChart: (data: T) => ReactNode;
  onRetry: () => void;
}

export function ChartCard<T>({ title, state, renderChart, onRetry }: Props<T>) {
  return (
    <div className={styles.chartCard}>
      <h3 className={styles.chartTitle}>{title}</h3>
      <div className={styles.chartBody}>
        {state.status === 'loading' && (
          <div className={styles.skeleton} aria-label="Đang tải biểu đồ" />
        )}
        {state.status === 'success' && state.data !== undefined && renderChart(state.data)}
        {state.status === 'error' && (
          <div className={styles.errorRow}>
            <span style={{ color: 'var(--on-surface-variant)' }}>--</span>
            <button
              type="button"
              onClick={onRetry}
              aria-label={`Tải lại ${title}`}
              title={state.error}
            >
              ⟳
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
