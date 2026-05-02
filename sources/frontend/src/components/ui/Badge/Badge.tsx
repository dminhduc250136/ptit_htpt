import React from 'react';
import styles from './Badge.module.css';

export type BadgeVariant =
  | 'default'
  | 'sale'
  | 'new'
  | 'hot'
  | 'out-of-stock'
  | 'success'
  | 'warning'
  | 'danger';

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  className?: string;
}

export default function Badge({
  children,
  variant = 'default',
  className = '',
}: BadgeProps) {
  return (
    <span className={`${styles.badge} ${styles[variant]} ${className}`}>
      {children}
    </span>
  );
}
