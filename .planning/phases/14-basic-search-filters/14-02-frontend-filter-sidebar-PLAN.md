---
phase: 14-basic-search-filters
plan: 02
type: execute
wave: 1
depends_on: []
files_modified:
  - sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx
  - sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css
autonomous: true
requirements:
  - SEARCH-01
  - SEARCH-02
must_haves:
  truths:
    - "Component FilterSidebar render brand checkboxes (alphabetical, scroll khi >6) + 2 price input + 4 preset chip + reset button"
    - "Check/uncheck brand → onChange call NGAY với brands array mới (D-02 instant)"
    - "Type vào price input → onChange call sau 400ms debounce (D-02)"
    - "Click preset chip → fill min+max + onChange NGAY"
    - "Click chip đang active → toggle off (clear cả min+max) + onChange NGAY"
    - "Validate priceMin > priceMax onBlur → inline error đỏ + KHÔNG fire onChange (D-05, SC-3)"
    - "Click 'Xóa bộ lọc' → reset tất cả filter state về default + onChange với value default"
    - "VND format thousand-separator dấu chấm khi onBlur (1000000 → 1.000.000), strip về số khi onFocus"
  artifacts:
    - path: "sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx"
      provides: "FilterSidebar component (controlled, props: brands, value, onChange, loading?)"
      min_lines: 120
      contains: "export default function FilterSidebar"
    - path: "sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css"
      provides: "Token-only CSS — sticky sidebar, brand list scroll, preset chips, error state"
      contains: "var(--primary)"
  key_links:
    - from: "FilterSidebar brand checkbox onChange"
      to: "props.onChange callback"
      via: "instant call no debounce"
      pattern: "onChange\\(\\s*\\{[^}]*brands"
    - from: "FilterSidebar price input"
      to: "props.onChange callback"
      via: "useEffect setTimeout 400ms"
      pattern: "setTimeout\\([^,]+,\\s*400"
    - from: "Preset chip click"
      to: "fill min+max + onChange"
      via: "button onClick handler"
      pattern: "aria-pressed"
---

<objective>
Build standalone, controlled `FilterSidebar` component (Brand facet + Price facet + Reset button) — chưa wire vào page. Component nhận `brands: string[]` (available list), `value: { brands, priceMin, priceMax }`, gọi `onChange` khi user tương tác. Tuân thủ UI-SPEC: token-only CSS, copy tiếng Việt, a11y (label/htmlFor, role="alert", aria-pressed).

Purpose: Tách hẳn UI implementation khỏi page wiring → executor có thể tập trung pattern + a11y mà KHÔNG bị phân tâm bởi state migration ở `/products/page.tsx` (Plan 03 lo phần đó).
Output: 2 file mới trong `components/ui/FilterSidebar/`. Build PASS, type-check PASS, có thể import từ bất cứ đâu.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/phases/14-basic-search-filters/14-CONTEXT.md
@.planning/phases/14-basic-search-filters/14-UI-SPEC.md
@.planning/phases/14-basic-search-filters/14-PATTERNS.md
@sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx
@sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.module.css
@sources/frontend/src/app/products/page.module.css
@sources/frontend/src/app/globals.css

<interfaces>
Component contract (downstream Plan 03 consumes):

```typescript
// FilterSidebar.tsx
export interface FilterValue {
  brands: string[];
  priceMin?: number;
  priceMax?: number;
}

export interface FilterSidebarProps {
  brands: string[];                    // available brands (parent fetched)
  value: FilterValue;                  // controlled
  onChange: (next: FilterValue) => void;
  loading?: boolean;                   // optional — show "Đang tải thương hiệu…" placeholder
}

export default function FilterSidebar(props: FilterSidebarProps): JSX.Element;
```

Existing reuse:
```typescript
// components/ui/Button/Button (already exists — variant: 'primary'|'secondary'|'tertiary', size: 'sm'|'md')
import Button from '@/components/ui/Button/Button';
```

Design tokens available (globals.css):
- Spacing: --space-1 (4px) .. --space-6 (24px)
- Color: --primary (#0040a1), --error (#ba1a1a), --surface, --surface-container-low, --surface-container-lowest, --on-surface, --on-primary, --outline-variant
- Typography: --text-title-md (16px), --text-body-md (14px), --text-label-md (13px), --text-label-sm (12px)
- Weight: --weight-regular (400), --weight-medium (500), --weight-semibold (600)
- Radius: --radius-lg, --radius-xl
- Leading: --leading-normal, --leading-tight
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: FilterSidebar.module.css — token-only styles</name>
  <files>sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css</files>
  <read_first>
    - sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.module.css (analog — pattern token-only, input focus state, clear button placement)
    - sources/frontend/src/app/products/page.module.css (lines 49-300 — `.sidebar`, `.filterTitle`, `.filterChip`, `.priceInputs`, `.presetBtn`, `.clearBtn` precedent)
    - sources/frontend/src/app/globals.css (xác nhận token names tồn tại: `--space-1..6`, `--primary`, `--error`, `--text-title-md`, etc.)
  </read_first>
  <action>
    Tạo file `FilterSidebar.module.css` với các selectors sau. KHÔNG hardcode hex/px ngoài 3 ngoại lệ được phép (precedent OrderFilterBar.module.css): `1.5px` border, `0.15s` transition, `40px` input height, `240px` brand list max-height. Mọi giá trị khác PHẢI là `var(--*)`.

    Selectors bắt buộc:

    ```css
    .sidebar {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: var(--space-2);
    }

    .clearBtn {
      /* tertiary Button override nếu cần — color text var(--primary), font-size var(--text-label-md), weight 600 */
      font-size: var(--text-label-md);
      font-weight: var(--weight-semibold);
    }

    .section {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      padding-block: var(--space-4) 0;
    }

    .sectionTitle {
      font-size: var(--text-title-md);
      font-weight: var(--weight-semibold);
      color: var(--on-surface);
      margin: 0 0 var(--space-2) 0;
    }

    .brandList {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      max-height: 240px;
      overflow-y: auto;
    }

    .brandRow {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      cursor: pointer;
      font-size: var(--text-body-md);
      color: var(--on-surface);
    }

    .brandRow input[type="checkbox"] {
      accent-color: var(--primary);
      width: 16px;
      height: 16px;
      cursor: pointer;
    }

    .brandEmpty {
      font-size: var(--text-body-md);
      color: var(--on-surface);
      opacity: 0.6;
    }

    .priceInputs {
      display: flex;
      align-items: center;
      gap: var(--space-3);
    }

    .priceInput {
      flex: 1;
      height: 40px;
      padding: 0 var(--space-2);
      border: 1.5px solid var(--outline-variant);
      border-radius: var(--radius-lg);
      background: var(--surface-container-lowest);
      color: var(--on-surface);
      font-size: var(--text-body-md);
      font-family: inherit;
      outline: none;
      transition: border-color 0.15s ease;
    }
    .priceInput:focus { border-color: var(--primary); }
    .priceInput.invalid { border-color: var(--error); }

    .priceSeparator {
      color: var(--on-surface);
      opacity: 0.6;
    }

    .presetGrid {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2);
      margin-top: var(--space-2);
    }

    .presetChip {
      padding: var(--space-2) var(--space-3);
      border: 1.5px solid var(--outline-variant);
      border-radius: var(--radius-xl);
      background: var(--surface-container-low);
      color: var(--on-surface);
      font-size: var(--text-label-md);
      font-weight: var(--weight-medium);
      cursor: pointer;
      font-family: inherit;
      transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
    }
    .presetChip[aria-pressed="true"] {
      background: var(--primary);
      color: var(--on-primary);
      border-color: var(--primary);
    }

    .priceError {
      font-size: var(--text-label-sm);
      font-weight: var(--weight-medium);
      color: var(--error);
      margin: var(--space-1) 0 0 0;
    }
    ```

    KHÔNG cần `@media` mobile riêng — parent `/products/page.module.css` đã quản lý drawer toggle (Plan 03 sẽ wire).
  </action>
  <verify>
    <automated>cd sources/frontend && npx tsc --noEmit</automated>
  </verify>
  <acceptance_criteria>
    - File tồn tại tại path đúng
    - File chứa substring `var(--primary)` (grep PASS — accent token reused)
    - File chứa substring `var(--error)` (grep PASS — destructive token reused)
    - File chứa substring `max-height: 240px` (grep PASS — UI-SPEC spacing exception)
    - File chứa substring `aria-pressed="true"` (grep PASS — preset active selector)
    - File KHÔNG chứa hex color regex `#[0-9a-fA-F]{3,6}` (grep returns 0 matches)
    - File KHÔNG chứa raw `px` ngoài 4 exceptions: `1.5px`, `40px`, `240px`, `16px` — grep `[^.0-9]px` audit
    - tsc passes (CSS modules typed)
  </acceptance_criteria>
  <done>
    CSS module ready, all tokens verified, no hardcoded colors. tsc PASS.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: FilterSidebar.tsx — controlled component (Brand + Price + Reset)</name>
  <files>sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx</files>
  <read_first>
    - sources/frontend/src/components/ui/OrderFilterBar/OrderFilterBar.tsx (analog full file — debounce 400ms pattern lines 38-44, clear button lines 114-126)
    - sources/frontend/src/components/ui/Button/Button.tsx (xác nhận props variant/size signatures)
    - sources/frontend/src/app/search/page.tsx (lines 28-38 — inline debounce setTimeout precedent)
    - sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css (Task 1 vừa tạo — biết class names)
  </read_first>
  <behavior>
    - Render <h3 class="sectionTitle">Thương hiệu</h3> với checkbox list từ props.brands
    - Nếu props.brands rỗng + !loading → render "Chưa có thương hiệu nào"; nếu loading → "Đang tải thương hiệu…"
    - Check checkbox brand → state local update → call onChange NGAY (no debounce)
    - Render <h3 class="sectionTitle">Khoảng giá</h3> với 2 input min/max + 4 preset chip
    - Type vào price input → state update → useEffect debounce 400ms → call onChange (chỉ khi không có error)
    - onBlur price input → format VND (toLocaleString('vi-VN')) hiển thị trong input + chạy validate
    - Validate min > max (cả 2 đều set) → setPriceError('Giá tối thiểu phải nhỏ hơn giá tối đa') → KHÔNG call onChange (skip debounce effect)
    - Click preset chip → set min+max theo thresholds (D-04: <5tr=priceMax 4999999, 5-10tr=[5000000,10000000], 10-20tr=[10000000,20000000], >20tr=priceMin 20000001) → call onChange NGAY → set activePreset state
    - Click chip đang active → activePreset=null + clear min+max → call onChange NGAY
    - "Xóa bộ lọc" button → reset tất cả → call onChange với { brands: [], priceMin: undefined, priceMax: undefined }
    - aria: label htmlFor cho mỗi checkbox, role="alert" cho error, aria-pressed cho preset, aria-label cho reset button, role="group" aria-label cho brand list
    - sr-only label cho price input ("Giá từ" / "Giá đến")
  </behavior>
  <action>
    Tạo file `FilterSidebar.tsx` đầu file `'use client';`. Implement theo behavior block trên.

    Imports:
    ```typescript
    'use client';
    import { useEffect, useRef, useState } from 'react';
    import Button from '@/components/ui/Button/Button';
    import styles from './FilterSidebar.module.css';
    ```

    Types export:
    ```typescript
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
    ```

    Preset chips constant (D-04):
    ```typescript
    const PRESETS: Array<{ id: string; label: string; min?: number; max?: number }> = [
      { id: 'lt5', label: '<5tr', max: 4999999 },
      { id: '5to10', label: '5-10tr', min: 5000000, max: 10000000 },
      { id: '10to20', label: '10-20tr', min: 10000000, max: 20000000 },
      { id: 'gt20', label: '>20tr', min: 20000001 },
    ];
    ```

    Helper VND format (inline — KHÔNG import lib mới):
    ```typescript
    const formatVnd = (n: number | undefined) => n === undefined ? '' : n.toLocaleString('vi-VN');
    const parseVnd = (s: string) => {
      const digits = s.replace(/[^\d]/g, '');
      return digits === '' ? undefined : Number(digits);
    };
    ```

    State setup:
    ```typescript
    const [selectedBrands, setSelectedBrands] = useState<string[]>(value.brands);
    const [priceMinDraft, setPriceMinDraft] = useState<string>(formatVnd(value.priceMin));
    const [priceMaxDraft, setPriceMaxDraft] = useState<string>(formatVnd(value.priceMax));
    const [priceError, setPriceError] = useState<string | null>(null);
    const [activePreset, setActivePreset] = useState<string | null>(null);
    const isInitialMount = useRef(true);  // skip debounce on first render
    ```

    Sync khi props.value change từ ngoài (parent reset):
    ```typescript
    useEffect(() => {
      setSelectedBrands(value.brands);
      setPriceMinDraft(formatVnd(value.priceMin));
      setPriceMaxDraft(formatVnd(value.priceMax));
      if (value.brands.length === 0 && value.priceMin === undefined && value.priceMax === undefined) {
        setPriceError(null);
        setActivePreset(null);
      }
    }, [value.brands, value.priceMin, value.priceMax]);
    ```

    Brand handler (instant — D-02):
    ```typescript
    function handleBrandToggle(brand: string, checked: boolean) {
      const next = checked ? [...selectedBrands, brand] : selectedBrands.filter(b => b !== brand);
      setSelectedBrands(next);
      onChange({ brands: next, priceMin: parseVnd(priceMinDraft), priceMax: parseVnd(priceMaxDraft) });
    }
    ```

    Price debounce effect (400ms — D-02):
    ```typescript
    useEffect(() => {
      if (isInitialMount.current) { isInitialMount.current = false; return; }
      const t = setTimeout(() => {
        const min = parseVnd(priceMinDraft);
        const max = parseVnd(priceMaxDraft);
        if (min !== undefined && max !== undefined && min > max) return;  // D-05 — skip onChange
        setActivePreset(null);  // user typing manual → clear preset highlight
        onChange({ brands: selectedBrands, priceMin: min, priceMax: max });
      }, 400);
      return () => clearTimeout(t);
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [priceMinDraft, priceMaxDraft]);
    ```

    onBlur price (format + validate):
    ```typescript
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
      // Strip format khi focus để dễ edit
      const digits = draft.replace(/[^\d]/g, '');
      setter(digits);
    }
    ```

    Preset handler (D-04):
    ```typescript
    function handlePresetClick(p: typeof PRESETS[0]) {
      if (activePreset === p.id) {
        // toggle off
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
    ```

    Reset handler (D-10):
    ```typescript
    function handleClear() {
      setSelectedBrands([]);
      setPriceMinDraft('');
      setPriceMaxDraft('');
      setPriceError(null);
      setActivePreset(null);
      onChange({ brands: [], priceMin: undefined, priceMax: undefined });
    }
    ```

    JSX skeleton (a11y — UI-SPEC):
    ```tsx
    return (
      <div className={styles.sidebar}>
        <div className={styles.header}>
          <Button variant="tertiary" size="sm" onClick={handleClear}
                  aria-label="Xóa bộ lọc thương hiệu và giá" className={styles.clearBtn}>
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
            <div role="group" aria-label="Danh sách thương hiệu" className={styles.brandList}>
              {brands.map(b => (
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
            <label htmlFor="price-min" className="sr-only">Giá từ</label>
            <input
              id="price-min"
              className={`${styles.priceInput} ${priceError ? styles.invalid : ''}`}
              type="text"
              inputMode="numeric"
              placeholder="Từ"
              value={priceMinDraft}
              onChange={(e) => setPriceMinDraft(e.target.value)}
              onBlur={handlePriceBlur}
              onFocus={() => handlePriceFocus(setPriceMinDraft, priceMinDraft)}
            />
            <span className={styles.priceSeparator}>—</span>
            <label htmlFor="price-max" className="sr-only">Giá đến</label>
            <input
              id="price-max"
              className={`${styles.priceInput} ${priceError ? styles.invalid : ''}`}
              type="text"
              inputMode="numeric"
              placeholder="Đến"
              value={priceMaxDraft}
              onChange={(e) => setPriceMaxDraft(e.target.value)}
              onBlur={handlePriceBlur}
              onFocus={() => handlePriceFocus(setPriceMaxDraft, priceMaxDraft)}
            />
          </div>
          <div className={styles.presetGrid}>
            {PRESETS.map(p => (
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
          {priceError && <p role="alert" className={styles.priceError}>{priceError}</p>}
        </section>
      </div>
    );
    ```

    KHÔNG tạo file test (project chưa có test infra cho components — verify qua tsc + manual UAT Plan 03).
  </action>
  <verify>
    <automated>cd sources/frontend && npx tsc --noEmit</automated>
  </verify>
  <acceptance_criteria>
    - File chứa substring `'use client';` (line 1)
    - File chứa substring `export interface FilterValue` (grep PASS)
    - File chứa substring `export interface FilterSidebarProps` (grep PASS)
    - File chứa substring `export default function FilterSidebar` (grep PASS)
    - File chứa substring `setTimeout(` với context 400 trong vòng 3 dòng (grep `setTimeout` + verify 400 nearby)
    - File chứa substring `aria-pressed=` (grep PASS — preset chip a11y)
    - File chứa substring `role="alert"` (grep PASS — error a11y)
    - File chứa substring `aria-label="Xóa bộ lọc thương hiệu và giá"` (grep PASS — exact UI-SPEC copy)
    - File chứa substring `Giá tối thiểu phải nhỏ hơn giá tối đa` (grep PASS — exact UI-SPEC error copy)
    - File chứa substring `Đang tải thương hiệu…` (grep PASS — loading copy)
    - File chứa substring `Chưa có thương hiệu nào` (grep PASS — empty brands copy)
    - File chứa substring `toLocaleString('vi-VN')` (grep PASS — VND format)
    - File chứa 4 preset labels: `<5tr`, `5-10tr`, `10-20tr`, `>20tr` (grep all 4 PASS)
    - tsc PASS exit 0 (no type errors)
  </acceptance_criteria>
  <done>
    Component compile clean, all UI-SPEC copy strings + a11y attributes + debounce/instant logic in place. Ready cho Plan 03 import.
  </done>
</task>

</tasks>

<verification>
- `cd sources/frontend && npx tsc --noEmit` → PASS exit 0
- `cd sources/frontend && npx next lint --file src/components/ui/FilterSidebar/FilterSidebar.tsx` → no errors (warnings OK)
- Grep audit:
  - `grep -c "var(--" sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css` → ≥ 20 (token-only enforced)
  - `grep -E "#[0-9a-fA-F]{3,6}" sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css` → 0 matches
  - All UI-SPEC copy strings present trong .tsx
- File structure: `components/ui/FilterSidebar/{FilterSidebar.tsx, FilterSidebar.module.css}` đúng convention codebase
</verification>

<success_criteria>
- D-02 instant brand + 400ms debounce price + instant preset — VERIFIED grep + code inspection
- D-04 4 preset chips với thresholds đúng (4999999, [5M,10M], [10M,20M], 20000001) — VERIFIED grep PRESETS array
- D-05 client-side validate min>max + KHÔNG fire onChange + inline error — VERIFIED grep `if (min > max) return` trong debounce effect
- D-10 reset button KHÔNG đụng tới categories (component không nhận categories prop) — VERIFIED bằng FilterValue interface chỉ có brands/priceMin/priceMax
- UI-SPEC copy 100% match: "Thương hiệu", "Khoảng giá", "Từ", "Đến", "Xóa bộ lọc", "Giá tối thiểu phải nhỏ hơn giá tối đa", "Chưa có thương hiệu nào", "Đang tải thương hiệu…", 4 preset labels — VERIFIED grep
- A11y: label/htmlFor checkbox, sr-only label price, aria-pressed preset, role="alert" error, aria-label reset, role="group" brand list — VERIFIED grep all 6
- KHÔNG dependency mới (`package.json` không thay đổi) — VERIFIED git diff sources/frontend/package.json empty
</success_criteria>

<output>
Sau khi complete, tạo `.planning/phases/14-basic-search-filters/14-02-SUMMARY.md` ghi:
- Files created (paths + line counts)
- tsc kết quả
- Lint kết quả
- Lưu ý nếu có deviation từ UI-SPEC (e.g. nếu phải tách `BrandFacet`/`PriceFacet` files)
- Ready signal cho Plan 03 wire vào page
</output>
