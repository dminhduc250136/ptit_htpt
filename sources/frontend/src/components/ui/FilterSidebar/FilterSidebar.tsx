'use client';

import { useEffect, useRef, useState } from 'react';
import Button from '@/components/ui/Button/Button';
import styles from './FilterSidebar.module.css';

export interface FilterValue {
  brands: string[];
  priceMin?: number;
  priceMax?: number;
}

export interface FilterSidebarProps {
  brands: string[];
  value: FilterValue;
  onChange: (next: FilterValue) => void;
  loading?: boolean;
}

const PRESETS: Array<{ id: string; label: string; min?: number; max?: number }> = [
  { id: 'lt5', label: '<5tr', max: 4999999 },
  { id: '5to10', label: '5-10tr', min: 5000000, max: 10000000 },
  { id: '10to20', label: '10-20tr', min: 10000000, max: 20000000 },
  { id: 'gt20', label: '>20tr', min: 20000001 },
];

const formatVnd = (n: number | undefined): string =>
  n === undefined ? '' : n.toLocaleString('vi-VN');

const parseVnd = (s: string): number | undefined => {
  const digits = s.replace(/[^\d]/g, '');
  return digits === '' ? undefined : Number(digits);
};

export default function FilterSidebar({
  brands,
  value,
  onChange,
  loading = false,
}: FilterSidebarProps) {
  const [selectedBrands, setSelectedBrands] = useState<string[]>(value.brands);
  const [priceMinDraft, setPriceMinDraft] = useState<string>(formatVnd(value.priceMin));
  const [priceMaxDraft, setPriceMaxDraft] = useState<string>(formatVnd(value.priceMax));
  const [priceError, setPriceError] = useState<string | null>(null);
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const isInitialMount = useRef(true);

  // Sync khi parent reset value (vd. clear from outside)
  useEffect(() => {
    setSelectedBrands(value.brands);
    setPriceMinDraft(formatVnd(value.priceMin));
    setPriceMaxDraft(formatVnd(value.priceMax));
    if (
      value.brands.length === 0 &&
      value.priceMin === undefined &&
      value.priceMax === undefined
    ) {
      setPriceError(null);
      setActivePreset(null);
    }
  }, [value.brands, value.priceMin, value.priceMax]);

  // Debounce 400ms cho price input (D-02)
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    const t = setTimeout(() => {
      const min = parseVnd(priceMinDraft);
      const max = parseVnd(priceMaxDraft);
      if (min !== undefined && max !== undefined && min > max) return; // D-05 skip onChange
      setActivePreset(null);
      onChange({ brands: selectedBrands, priceMin: min, priceMax: max });
    }, 400);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [priceMinDraft, priceMaxDraft]);

  function handleBrandToggle(brand: string, checked: boolean) {
    const next = checked
      ? [...selectedBrands, brand]
      : selectedBrands.filter((b) => b !== brand);
    setSelectedBrands(next);
    onChange({
      brands: next,
      priceMin: parseVnd(priceMinDraft),
      priceMax: parseVnd(priceMaxDraft),
    });
  }

  function handlePriceBlur() {
    const min = parseVnd(priceMinDraft);
    const max = parseVnd(priceMaxDraft);
    setPriceMinDraft(formatVnd(min));
    setPriceMaxDraft(formatVnd(max));
    if (min !== undefined && max !== undefined && min > max) {
      setPriceError('Giá tối thiểu phải nhỏ hơn giá tối đa');
    } else {
      setPriceError(null);
    }
  }

  function handlePriceFocus(setter: (s: string) => void, draft: string) {
    const digits = draft.replace(/[^\d]/g, '');
    setter(digits);
  }

  function handlePresetClick(p: (typeof PRESETS)[number]) {
    if (activePreset === p.id) {
      setActivePreset(null);
      setPriceMinDraft('');
      setPriceMaxDraft('');
      setPriceError(null);
      onChange({ brands: selectedBrands, priceMin: undefined, priceMax: undefined });
    } else {
      setActivePreset(p.id);
      setPriceMinDraft(formatVnd(p.min));
      setPriceMaxDraft(formatVnd(p.max));
      setPriceError(null);
      onChange({ brands: selectedBrands, priceMin: p.min, priceMax: p.max });
    }
  }

  function handleClear() {
    setSelectedBrands([]);
    setPriceMinDraft('');
    setPriceMaxDraft('');
    setPriceError(null);
    setActivePreset(null);
    onChange({ brands: [], priceMin: undefined, priceMax: undefined });
  }

  return (
    <div className={styles.sidebar}>
      <div className={styles.header}>
        <Button
          variant="tertiary"
          size="sm"
          onClick={handleClear}
          aria-label="Xóa bộ lọc thương hiệu và giá"
          className={styles.clearBtn}
        >
          Xóa bộ lọc
        </Button>
      </div>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Thương hiệu</h3>
        {loading && <p className={styles.brandEmpty}>Đang tải thương hiệu…</p>}
        {!loading && brands.length === 0 && (
          <p className={styles.brandEmpty}>Chưa có thương hiệu nào</p>
        )}
        {!loading && brands.length > 0 && (
          <div
            role="group"
            aria-label="Danh sách thương hiệu"
            className={styles.brandList}
          >
            {brands.map((b) => (
              <label key={b} htmlFor={`brand-${b}`} className={styles.brandRow}>
                <input
                  id={`brand-${b}`}
                  type="checkbox"
                  checked={selectedBrands.includes(b)}
                  onChange={(e) => handleBrandToggle(b, e.target.checked)}
                />
                {b}
              </label>
            ))}
          </div>
        )}
      </section>

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>Khoảng giá</h3>
        <div className={styles.priceInputs}>
          <label htmlFor="price-min" className="sr-only">
            Giá từ
          </label>
          <input
            id="price-min"
            className={`${styles.priceInput} ${priceError ? styles.invalid : ''}`}
            type="text"
            inputMode="numeric"
            size={1}
            placeholder="Từ"
            value={priceMinDraft}
            onChange={(e) => setPriceMinDraft(e.target.value)}
            onBlur={handlePriceBlur}
            onFocus={() => handlePriceFocus(setPriceMinDraft, priceMinDraft)}
          />
          <span className={styles.priceSeparator}>—</span>
          <label htmlFor="price-max" className="sr-only">
            Giá đến
          </label>
          <input
            id="price-max"
            className={`${styles.priceInput} ${priceError ? styles.invalid : ''}`}
            type="text"
            inputMode="numeric"
            size={1}
            placeholder="Đến"
            value={priceMaxDraft}
            onChange={(e) => setPriceMaxDraft(e.target.value)}
            onBlur={handlePriceBlur}
            onFocus={() => handlePriceFocus(setPriceMaxDraft, priceMaxDraft)}
          />
        </div>
        <div className={styles.presetGrid}>
          {PRESETS.map((p) => (
            <button
              key={p.id}
              type="button"
              aria-pressed={activePreset === p.id}
              className={styles.presetChip}
              onClick={() => handlePresetClick(p)}
            >
              {p.label}
            </button>
          ))}
        </div>
        {priceError && (
          <p role="alert" className={styles.priceError}>
            {priceError}
          </p>
        )}
      </section>
    </div>
  );
}
