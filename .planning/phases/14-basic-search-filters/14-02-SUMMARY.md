---
phase: 14-basic-search-filters
plan: 02
subsystem: frontend
tags: [search, filter, ui, component, a11y]
requires: []
provides:
  - "FilterSidebar component (controlled, brands + price + reset)"
  - "FilterValue / FilterSidebarProps types exported cho Plan 03 import"
affects:
  - "sources/frontend/src/components/ui/FilterSidebar/ (new directory)"
tech_stack_added: []
patterns_used:
  - "OrderFilterBar token-only CSS (40px input height, 1.5px border, 0.15s transition)"
  - "search/page.tsx inline debounce setTimeout 400ms (useEffect cleanup clearTimeout)"
  - "Button component variant=tertiary size=sm aria-label spread"
key_files_created:
  - "sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css (127 lines)"
  - "sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx (229 lines)"
key_files_modified: []
decisions:
  - "Inline VND format helper (toLocaleString vi-VN) — KHÔNG import lib mới"
  - "isInitialMount ref guard — skip debounce trigger trên first render để tránh phantom onChange call lúc mount"
  - "useEffect sync props.value vào local state — cho phép parent reset filter từ ngoài (Plan 03 reset cross-facet)"
metrics:
  duration_minutes: 8
  completed_at: "2026-05-02"
  tasks_completed: 2
  files_created: 2
  files_modified: 0
---

# Phase 14 Plan 02: Frontend FilterSidebar Component Summary

Standalone controlled `FilterSidebar` (Brand checkbox + Price input + 4 preset chip + Reset) — token-only CSS, full a11y, debounce 400ms cho price + instant cho brand/preset/reset. Sẵn sàng cho Plan 03 wire vào `/products/page.tsx`.

## Files Created

| File | Lines | Mục đích |
|------|-------|----------|
| `sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css` | 127 | Token-only styles (43 var(--*), 4 px exceptions: 1.5px/16px/40px/240px, 0 hex) |
| `sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx` | 229 | Controlled component — `FilterValue` / `FilterSidebarProps` exports |

## Tasks Executed

| # | Task | Commit | Status |
|---|------|--------|--------|
| 1 | FilterSidebar.module.css token-only styles | `a5aa5b8` | ✅ |
| 2 | FilterSidebar.tsx controlled component | `aa4a7b7` | ✅ |

## Verification Results

- **tsc:** `cd sources/frontend && npx tsc --noEmit` → PASS exit 0 (cả 2 task)
- **eslint:** `npx eslint src/components/ui/FilterSidebar/` → clean (0 errors, 0 warnings)
- **Token audit CSS:**
  - `grep -c "var(--"` → 43 (≥ 20 yêu cầu) ✅
  - `grep -E "#[0-9a-fA-F]{3,6}"` → 0 matches ✅
  - `grep "px"` → 6 dòng, đều thuộc 4 exceptions cho phép (1.5px border ×2, 16px checkbox ×2, 40px input height, 240px brand list max-height) ✅
- **UI-SPEC copy strings:** verified grep PASS:
  - `Thương hiệu`, `Khoảng giá`, `Từ`, `Đến`, `Xóa bộ lọc`
  - `Giá tối thiểu phải nhỏ hơn giá tối đa` (error message)
  - `Đang tải thương hiệu…` / `Chưa có thương hiệu nào` (loading + empty)
  - 4 preset labels: `<5tr`, `5-10tr`, `10-20tr`, `>20tr`
- **A11y attributes:** verified grep PASS — `role="alert"`, `role="group"`, `aria-pressed=`, `aria-label="Xóa bộ lọc thương hiệu và giá"`, `htmlFor` cho mỗi checkbox + price label, `sr-only` label `Giá từ`/`Giá đến`
- **Debounce contract:** `setTimeout(...)` ở line 69, `, 400);` ở line 75 trong cùng useEffect → 400ms debounce confirmed
- **Preset thresholds (D-04):** `4999999`, `5000000-10000000`, `10000000-20000000`, `20000001` — VERIFIED constants block lines 21-24
- **Skip onChange khi error (D-05):** `if (min > max) return;` in debounce effect line 73 — VERIFIED
- **No new dependency:** không thay đổi `package.json` ✅

## Deviations from Plan

Không có deviation. Plan execute đúng như written:
- 2 file đúng paths
- Behavior đúng spec (instant brand/preset/reset, debounce 400ms price, validate onBlur)
- Token-only CSS đúng 4 px exceptions cho phép
- A11y full compliance

## Success Criteria Status

| Criterion | Status |
|-----------|--------|
| D-02 instant brand + 400ms debounce price + instant preset | ✅ |
| D-04 4 preset chip thresholds đúng | ✅ |
| D-05 client-side validate min>max + skip onChange + inline error | ✅ |
| D-10 reset không đụng categories (FilterValue chỉ brands/priceMin/priceMax) | ✅ |
| UI-SPEC copy 100% match | ✅ |
| A11y: 6 attributes verified | ✅ |
| Không dependency mới | ✅ |

## Ready Signal cho Plan 03

Component import path:
```typescript
import FilterSidebar, { type FilterValue } from '@/components/ui/FilterSidebar/FilterSidebar';
```

Contract Plan 03 cần tuân thủ:
- Truyền `brands: string[]` (parent fetch list từ API)
- Truyền `value: FilterValue` controlled từ URL state
- `onChange` callback nhận `FilterValue` — instant cho brand/preset/reset, debounced 400ms cho price typing
- `loading?: boolean` để hiển thị "Đang tải thương hiệu…" khi đang fetch brands list

## Self-Check: PASSED

- ✅ FOUND: sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.module.css
- ✅ FOUND: sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx
- ✅ FOUND commit a5aa5b8 (CSS task)
- ✅ FOUND commit aa4a7b7 (TSX task)
