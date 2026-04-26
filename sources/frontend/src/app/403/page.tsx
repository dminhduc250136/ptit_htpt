'use client';

import Link from 'next/link';
import Button from '@/components/ui/Button/Button';
import styles from './page.module.css';

export default function ForbiddenPage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.title}>Không có quyền truy cập</h1>
        <p className={styles.body}>Bạn không có quyền xem trang này.</p>
        <Link href="/" className={styles.link}>
          <Button variant="primary" size="lg" fullWidth>Về trang chủ</Button>
        </Link>
      </div>
    </div>
  );
}
