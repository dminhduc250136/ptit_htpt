'use client';

import { useState } from 'react';
import styles from './ReviewSection.module.css';

interface StarWidgetProps {
  value: number;            // 0..5
  onChange: (n: number) => void;
  disabled?: boolean;
}

export default function StarWidget({ value, onChange, disabled }: StarWidgetProps) {
  const [hovered, setHovered] = useState(0);
  return (
    <div role="radiogroup" aria-label="Chọn số sao" className={styles.starWidget}>
      {[1, 2, 3, 4, 5].map((n) => {
        const filled = n <= (hovered || value);
        return (
          <button
            key={n}
            type="button"
            aria-label={`${n} sao`}
            aria-pressed={value === n}
            disabled={disabled}
            onMouseEnter={() => !disabled && setHovered(n)}
            onMouseLeave={() => setHovered(0)}
            onClick={() => !disabled && onChange(n)}
            className={filled ? styles.starFilled : styles.starEmpty}
          >
            ★
          </button>
        );
      })}
    </div>
  );
}
