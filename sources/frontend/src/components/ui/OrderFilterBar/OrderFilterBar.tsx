'use client';

import { useEffect, useState } from 'react';
import Button from '@/components/ui/Button/Button';
import styles from './OrderFilterBar.module.css';

interface OrderFilters {
  status: string;
  from: string;
  to: string;
  q: string;
}

interface OrderFilterBarProps {
  initialStatus?: string;
  initialFrom?: string;
  initialTo?: string;
  initialQ?: string;
  onChange: (filters: OrderFilters) => void;
}

export default function OrderFilterBar({
  initialStatus = 'ALL',
  initialFrom = '',
  initialTo = '',
  initialQ = '',
  onChange,
}: OrderFilterBarProps) {
  const [status, setStatus] = useState(initialStatus);
  const [from, setFrom] = useState(initialFrom);
  const [to, setTo] = useState(initialTo);
  const [q, setQ] = useState(initialQ);

  const hasFilter =
    status !== 'ALL' || from !== '' || to !== '' || q !== '';

  // Debounce 400ms — onChange excluded from deps (caller must wrap in useCallback)
  useEffect(() => {
    const timer = setTimeout(() => {
      onChange({ status, from, to, q });
    }, 400);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, from, to, q]);

  function handleClear() {
    setStatus('ALL');
    setFrom('');
    setTo('');
    setQ('');
  }

  return (
    <div className={styles.filterBar}>
      <div className={styles.filterGroup}>
        <label className={styles.label} htmlFor="order-status-filter">
          Trạng thái
        </label>
        <select
          id="order-status-filter"
          className={styles.select}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="ALL">Tất cả trạng thái</option>
          <option value="PENDING">Chờ xác nhận</option>
          <option value="CONFIRMED">Đã xác nhận</option>
          <option value="SHIPPING">Đang giao</option>
          <option value="DELIVERED">Đã giao</option>
          <option value="CANCELLED">Đã hủy</option>
        </select>
      </div>

      <div className={styles.filterGroup}>
        <label className={styles.label} htmlFor="order-from-filter">
          Từ ngày
        </label>
        <input
          id="order-from-filter"
          type="date"
          className={styles.input}
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
      </div>

      <div className={styles.filterGroup}>
        <label className={styles.label} htmlFor="order-to-filter">
          Đến ngày
        </label>
        <input
          id="order-to-filter"
          type="date"
          className={styles.input}
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />
      </div>

      <div className={styles.filterGroup}>
        <label className={styles.label} htmlFor="order-q-filter">
          Tìm kiếm
        </label>
        <input
          id="order-q-filter"
          type="text"
          className={styles.input}
          placeholder="Tìm theo mã đơn hàng..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
      </div>

      {hasFilter && (
        <div className={styles.filterGroup}>
          <Button
            variant="tertiary"
            size="sm"
            onClick={handleClear}
            aria-label="Xóa tất cả bộ lọc đang áp dụng"
            className={styles.clearBtn}
          >
            Xóa bộ lọc
          </Button>
        </div>
      )}
    </div>
  );
}

export type { OrderFilterBarProps, OrderFilters };
