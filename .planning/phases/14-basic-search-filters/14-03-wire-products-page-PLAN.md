---
phase: 14-basic-search-filters
plan: 03
type: execute
wave: 2
depends_on: ["14-01", "14-02"]
files_modified:
  - sources/frontend/src/services/products.ts
  - sources/frontend/src/app/products/page.tsx
autonomous: false
requirements:
  - SEARCH-01
  - SEARCH-02
must_haves:
  truths:
    - "Trang /products fetch danh sách brand từ GET /api/products/brands khi mount"
    - "FilterSidebar render trong sidebar dưới block Categories với props: brands, value, onChange wired vào state /products/page.tsx"
    - "Check brand → listProducts gọi với ?brands=X&brands=Y → grid update server-filtered"
    - "Type price min/max → sau 400ms listProducts gọi với ?priceMin=&priceMax= → grid update"
    - "Click 'Xóa bộ lọc' trong FilterSidebar → reset brand+price (Categories KHÔNG bị reset, D-10)"
    - "Filter thay đổi → page reset về 0 (D-11)"
    - "0 results sau filter → empty state copy 'Không tìm thấy sản phẩm phù hợp với bộ lọc' (D-12)"
    - "Client-side `.filter()` cũ trong page.tsx đã loại bỏ — server filter là source of truth (D-06)"
  artifacts:
    - path: "sources/frontend/src/services/products.ts"
      provides: "ListProductsParams extended + listBrands() function"
      contains: "listBrands"
    - path: "sources/frontend/src/app/products/page.tsx"
      provides: "FilterSidebar wired + state migrated + empty state copy updated"
      contains: "FilterSidebar"
  key_links:
    - from: "/products/page.tsx useEffect mount"
      to: "services/products.ts listBrands()"
      via: "fetch /api/products/brands"
      pattern: "listBrands\\("
    - from: "/products/page.tsx FilterSidebar onChange"
      to: "load() useCallback → listProducts({brands, priceMin, priceMax})"
      via: "state setters trigger load via deps"
      pattern: "brands:\\s*filterBrands"
    - from: "services/products.ts listProducts"
      to: "GET /api/products?brands=&priceMin=&priceMax="
      via: "URLSearchParams.append('brands', b) + .set('priceMin', ...)"
      pattern: "qs\\.append\\(['\"]brands['\"]"
---

<objective>
Wire FilterSidebar (Plan 02) vào `/products/page.tsx` + extend services/products.ts cho 3 query params mới (D-08) + thêm `listBrands()` (D-03). Loại bỏ client-side `.filter()` cũ (lines 87-89) — server filter là source of truth (D-06). Cập nhật empty state copy theo D-12. Kết thúc bằng UAT checkpoint để user verify visually.

Purpose: Connect backend (Plan 01) + component (Plan 02) thành flow end-to-end visible cho user. Đây là plan cuối cùng đóng phase 14.
Output: 2 file FE modified. tsc PASS, build PASS, manual UAT confirm.
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
@.planning/phases/14-basic-search-filters/14-01-SUMMARY.md
@.planning/phases/14-basic-search-filters/14-02-SUMMARY.md
@sources/frontend/src/services/products.ts
@sources/frontend/src/app/products/page.tsx

<interfaces>
Consumes (từ Plan 01 backend):
- `GET /api/products/brands` → `ApiResponse<List<String>>` envelope, http.ts unwraps → `string[]`
- `GET /api/products?brands=Dell&brands=HP&priceMin=5000000&priceMax=10000000&...` → `PaginatedResponse<Product>`

Consumes (từ Plan 02 component):
```typescript
import FilterSidebar, { type FilterValue } from '@/components/ui/FilterSidebar/FilterSidebar';

interface FilterSidebarProps {
  brands: string[];
  value: FilterValue;
  onChange: (next: FilterValue) => void;
  loading?: boolean;
}
```

Produces:
```typescript
// services/products.ts — ListProductsParams extended
export interface ListProductsParams {
  page?: number;
  size?: number;
  sort?: string;
  categoryId?: string;
  keyword?: string;
  brands?: string[];        // NEW (D-08)
  priceMin?: number;        // NEW
  priceMax?: number;        // NEW
}

// NEW
export function listBrands(): Promise<string[]>;
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: services/products.ts — extend params + listBrands()</name>
  <files>sources/frontend/src/services/products.ts</files>
  <read_first>
    - sources/frontend/src/services/products.ts (current — lines 28-45 ListProductsParams + listProducts; line 62-64 listCategories template)
    - sources/frontend/src/services/http.ts (xác nhận httpGet unwraps ApiResponse envelope)
  </read_first>
  <action>
    Bước 1 — Mở rộng `ListProductsParams` interface (line 28-34):
    ```typescript
    export interface ListProductsParams {
      page?: number;
      size?: number;
      sort?: string;
      categoryId?: string;
      keyword?: string;
      brands?: string[];        // NEW (D-08) — repeatable param
      priceMin?: number;        // NEW
      priceMax?: number;        // NEW
    }
    ```

    Bước 2 — Mở rộng serialization trong `listProducts` (line 36-45). APPEND vào trước dòng `const suffix`:
    ```typescript
    if (params?.brands && params.brands.length > 0) {
      params.brands.forEach((b) => qs.append('brands', b));
    }
    if (params?.priceMin !== undefined) qs.set('priceMin', String(params.priceMin));
    if (params?.priceMax !== undefined) qs.set('priceMax', String(params.priceMax));
    ```
    QUAN TRỌNG: dùng `qs.append` (KHÔNG `qs.set`) cho brands → URL ra `?brands=Dell&brands=HP` match Spring `@RequestParam List<String>`.

    Bước 3 — Thêm function `listBrands()` SAU `listCategories` (sau line 64), TRƯỚC `// Admin product create/update body` comment block:
    ```typescript
    /**
     * Phase 14 / SEARCH-01 (D-03): danh sách thương hiệu DISTINCT từ catalog.
     * Backend trả ApiResponse<List<String>> — http.ts unwrap envelope.
     */
    export function listBrands(): Promise<string[]> {
      return httpGet<string[]>(`/api/products/brands`);
    }
    ```

    Bước 4 — KHÔNG sửa `listAdminProducts` (line 84-92) — admin list không cần brand/price filter trong scope phase 14.
  </action>
  <verify>
    <automated>cd sources/frontend && npx tsc --noEmit</automated>
  </verify>
  <acceptance_criteria>
    - File chứa substring `brands?: string[];` trong ListProductsParams (grep PASS)
    - File chứa substring `priceMin?: number;` (grep PASS)
    - File chứa substring `priceMax?: number;` (grep PASS)
    - File chứa substring `qs.append('brands'` (grep PASS — repeatable param)
    - File chứa substring `qs.set('priceMin'` (grep PASS)
    - File chứa substring `export function listBrands` (grep PASS)
    - File chứa substring `/api/products/brands` (grep PASS)
    - tsc PASS exit 0
  </acceptance_criteria>
  <done>
    Service module compile clean; ListProductsParams extends 3 fields; listBrands exported; existing functions unchanged.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: app/products/page.tsx — wire FilterSidebar + remove client-side filter</name>
  <files>sources/frontend/src/app/products/page.tsx</files>
  <read_first>
    - sources/frontend/src/app/products/page.tsx (current — full file)
    - sources/frontend/src/components/ui/FilterSidebar/FilterSidebar.tsx (Plan 02 — biết props shape + FilterValue type)
    - sources/frontend/src/services/products.ts (Task 1 vừa update — biết signature listBrands + extended params)
  </read_first>
  <behavior>
    - Mount: fetch listBrands() (non-fatal fail) → setAvailableBrands
    - State `priceRange: [number, number]` thay bằng `filterBrands: string[]`, `filterPriceMin?: number`, `filterPriceMax?: number`, `availableBrands: string[]`
    - listProducts call truyền brands + priceMin + priceMax (undefined nếu chưa set)
    - load deps array thêm 3 filter state mới — filter thay đổi auto trigger load
    - Filter call ALWAYS dùng `page: 0` (D-11 reset on filter change)
    - Block JSX cũ "Khoảng giá" (lines 171-197) DELETE — thay bằng `<FilterSidebar ... />` đặt SAU Categories block, TRƯỚC mobile filter close
    - Client-side `.filter()` (lines 87-89) DELETE — replace `filteredProducts` references bằng `products`
    - clearFilters() — D-10: KHÔNG reset categories/searchQuery/sortBy nữa; chỉ reset brands+price (header "Xóa tất cả" giữ nguyên reset toàn bộ — nhưng nay reset price+brand thay priceRange)
    - Empty state copy update theo D-12: heading "Không tìm thấy sản phẩm phù hợp với bộ lọc" + body "Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá" + button "Xóa bộ lọc"
  </behavior>
  <action>
    Bước 1 — Imports (line 6-11). Thêm:
    ```typescript
    import FilterSidebar, { type FilterValue } from '@/components/ui/FilterSidebar/FilterSidebar';
    import { listProducts, listCategories, listBrands } from '@/services/products';
    ```
    (Hợp nhất với line 10 hiện có — chỉ thêm `listBrands` vào danh sách import.)

    Bước 2 — Replace state declaration (line 19-24). XOÁ:
    ```typescript
    const [priceRange, setPriceRange] = useState<[number, number]>([0, 10000000]);
    ```
    THÊM (đặt cùng nhóm với selectedCategory):
    ```typescript
    const [filterBrands, setFilterBrands] = useState<string[]>([]);
    const [filterPriceMin, setFilterPriceMin] = useState<number | undefined>(undefined);
    const [filterPriceMax, setFilterPriceMax] = useState<number | undefined>(undefined);
    const [availableBrands, setAvailableBrands] = useState<string[]>([]);
    const [brandsLoading, setBrandsLoading] = useState(true);
    ```

    Bước 3 — Thêm useEffect fetch brands (sau effect listCategories, trước `load` useCallback):
    ```typescript
    useEffect(() => {
      let alive = true;
      setBrandsLoading(true);
      listBrands()
        .then((list) => {
          if (!alive) return;
          setAvailableBrands(list ?? []);
        })
        .catch(() => {
          // Non-fatal — brand facet shows "Chưa có thương hiệu nào"
        })
        .finally(() => {
          if (alive) setBrandsLoading(false);
        });
      return () => { alive = false; };
    }, []);
    ```

    Bước 4 — Update `load` callback (line 50-80):
    - Trong listProducts call thêm `page: 0` (D-11) và 3 fields mới:
    ```typescript
    const resp = await listProducts({
      page: 0,
      size: 24,
      sort: sortParam,
      categoryId: selectedCategory ?? undefined,
      keyword: searchQuery.trim() || undefined,
      brands: filterBrands.length > 0 ? filterBrands : undefined,
      priceMin: filterPriceMin,
      priceMax: filterPriceMax,
    });
    ```
    - useCallback deps array (line 80) thêm 3 mới: `[sortBy, selectedCategory, searchQuery, filterBrands, filterPriceMin, filterPriceMax]`

    Bước 5 — XOÁ client-side filter (lines 86-89):
    ```typescript
    // DELETE entire block:
    const filteredProducts = products.filter(
      (p) => p.price >= priceRange[0] && p.price <= priceRange[1],
    );
    ```
    Sau đó replace MỌI `filteredProducts` references trong JSX bằng `products`:
    - Line 202 `Xem {filteredProducts.length} sản phẩm` → `Xem {products.length} sản phẩm`
    - Line 212 `{filteredProducts.length} sản phẩm` → `{products.length} sản phẩm`
    - Line 236 `filteredProducts.length > 0 ?` → `products.length > 0 ?`
    - Line 238 `filteredProducts.map` → `products.map`

    Bước 6 — Update `clearFilters` (lines 91-96). D-10: chỉ reset brand+price (categories/keyword/sort được header "Xóa tất cả" reset riêng):
    ```typescript
    const clearFilters = () => {
      setFilterBrands([]);
      setFilterPriceMin(undefined);
      setFilterPriceMax(undefined);
    };

    const clearAll = () => {
      // Header "Xóa tất cả" — reset toàn bộ filter (categories + search + sort + brand + price)
      setSelectedCategory(null);
      setSearchQuery('');
      setSortBy('newest');
      clearFilters();
    };
    ```
    Update header button line 131:
    ```tsx
    <button className={styles.clearBtn} onClick={clearAll}>Xóa tất cả</button>
    ```

    Bước 7 — XOÁ block "Price Range" (lines 171-197). XOÁ toàn bộ `<div className={styles.filterGroup}>` chứa "Khoảng giá" + priceInputs + pricePresets.

    Bước 8 — INSERT FilterSidebar tại vị trí block vừa xoá (giữa Categories block kết thúc line 169 và Mobile close block line 200):
    ```tsx
    <FilterSidebar
      brands={availableBrands}
      loading={brandsLoading}
      value={{ brands: filterBrands, priceMin: filterPriceMin, priceMax: filterPriceMax }}
      onChange={(next: FilterValue) => {
        setFilterBrands(next.brands);
        setFilterPriceMin(next.priceMin);
        setFilterPriceMax(next.priceMax);
      }}
    />
    ```

    Bước 9 — Update empty state copy (lines 243-251), match exact D-12 + UI-SPEC copy:
    ```tsx
    <div className={styles.emptyState}>
      <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="var(--outline-variant)" strokeWidth="1.5">
        <circle cx="11" cy="11" r="8" />
        <line x1="21" y1="21" x2="16.65" y2="16.65" />
      </svg>
      <h3>Không tìm thấy sản phẩm phù hợp với bộ lọc</h3>
      <p>Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá</p>
      <Button variant="secondary" onClick={clearFilters}>Xóa bộ lọc</Button>
    </div>
    ```

    Bước 10 — Build verify:
    ```
    cd sources/frontend && npx tsc --noEmit
    cd sources/frontend && npm run build
    ```
  </action>
  <verify>
    <automated>cd sources/frontend && npx tsc --noEmit && npm run build</automated>
  </verify>
  <acceptance_criteria>
    - File chứa substring `import FilterSidebar` (grep PASS)
    - File chứa substring `import { listProducts, listCategories, listBrands }` (grep PASS)
    - File chứa substring `<FilterSidebar` (grep PASS — component wired)
    - File chứa substring `availableBrands` (grep PASS)
    - File chứa substring `filterBrands` (grep ≥ 4 matches — state + load + onChange + clearFilters)
    - File chứa substring `filterPriceMin` (grep ≥ 4 matches)
    - File chứa substring `filterPriceMax` (grep ≥ 4 matches)
    - File chứa substring `page: 0` trong listProducts call (grep PASS — D-11)
    - File KHÔNG còn substring `priceRange` (grep returns 0 matches — state cũ removed)
    - File KHÔNG còn substring `filteredProducts` (grep returns 0 matches)
    - File KHÔNG còn substring `products.filter(` (grep returns 0 matches — client-side filter removed)
    - File chứa substring `Không tìm thấy sản phẩm phù hợp với bộ lọc` (grep PASS — D-12 copy)
    - File chứa substring `Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá` (grep PASS)
    - tsc PASS + Next build PASS exit 0
  </acceptance_criteria>
  <done>
    Page wire xong, build clean. State cũ priceRange + client-side filter triệt tiêu hoàn toàn. FilterSidebar render đúng vị trí dưới Categories.
  </done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 3: UAT — visual + interaction verify trên Docker stack</name>
  <what-built>
    - Backend: GET /api/products/brands + extended GET /api/products?brands=&priceMin=&priceMax= (Plan 01)
    - Frontend: FilterSidebar component (Plan 02) wired vào /products page (Task 1+2 plan này)
    - Empty state copy mới + reset behavior D-10 (categories không bị reset bởi Xóa bộ lọc)
  </what-built>
  <how-to-verify>
    Start Docker stack nếu chưa:
    ```
    docker compose up -d
    ```
    Mở browser `http://localhost:3000/products`.

    1. **Brand list render** — Sidebar dưới block "Danh mục" hiển thị section "Thương hiệu" với checkboxes alphabetical từ DB (Dell, HP, Apple, etc. tuỳ seed data). Nếu DB không brand nào → "Chưa có thương hiệu nào".
    2. **Brand instant apply** — Tick 1 brand → grid update gần như tức thời (loading skeleton thoáng qua, không debounce). Network tab: `GET /api/products?brands=Dell&page=0&size=24` (verify URL có repeatable param đúng).
    3. **Brand multi-select** — Tick thêm brand thứ 2 → URL `?brands=Dell&brands=HP` → grid hiển thị OR (sản phẩm Dell HOẶC HP).
    4. **Price input debounce** — Type "5000000" vào "Từ" + "10000000" vào "Đến" → đợi ~400ms → grid filter. Không gọi API mỗi keystroke.
    5. **VND format onBlur** — Click ra ngoài input → giá trị đổi thành "5.000.000" / "10.000.000" (dấu chấm). Click vào lại → strip về "5000000" để edit.
    6. **Validation min > max** — Set "Từ" = 20000000, "Đến" = 5000000, click ra ngoài → border đỏ + dòng đỏ "Giá tối thiểu phải nhỏ hơn giá tối đa" hiện dưới price section. Network tab: KHÔNG có request gửi đi (D-05 SC-3).
    7. **Preset chip** — Click "5-10tr" → 2 input fill "5.000.000" / "10.000.000" + chip highlight nền xanh + grid update. Click chip "5-10tr" lần nữa → toggle off, input clear, grid update không filter price.
    8. **Reset button "Xóa bộ lọc"** (trong FilterSidebar header) — Tick brands + set price → click "Xóa bộ lọc" → brand uncheck hết, price input clear, preset chip clear active. Categories selection (nếu có) GIỮ NGUYÊN không reset (D-10 — khác header "Xóa tất cả").
    9. **Header "Xóa tất cả"** — Sidebar header button → reset TOÀN BỘ (categories + search + sort + brand + price).
    10. **Empty state** — Tick brand không tồn tại sản phẩm + price range < 1000 → grid empty → hiển thị "Không tìm thấy sản phẩm phù hợp với bộ lọc" + body "Thử bỏ bớt thương hiệu hoặc nới rộng khoảng giá" + button "Xóa bộ lọc" (cùng handler reset).
    11. **Pagination reset** — Nếu pagination tồn tại (>24 sản phẩm) → ở page 2 → tick brand → page reset về 0 (URL `page=0`).
    12. **Categories KHÔNG bị reset bởi FilterSidebar reset** — Click 1 category chip → tick brands → click "Xóa bộ lọc" trong sidebar → category vẫn active, chỉ brand+price reset. (D-10 verify)
    13. **A11y keyboard** — Tab qua các checkbox brand, preset chip, reset button → focus visible. Tab vào checkbox + Space → toggle.
    14. **Mobile responsive** (resize <768px) — Toggle "Bộ lọc" → sidebar drawer mở → FilterSidebar render bên trong drawer cùng Categories. Đóng drawer → button "Xem N sản phẩm" hiển thị count đúng.

    Nếu BẤT KỲ điểm nào fail → mô tả chi tiết (screenshot tốt hơn) + reproduce steps; KHÔNG approve.
  </how-to-verify>
  <resume-signal>
    Type "approved" nếu tất cả 14 items PASS. Hoặc liệt kê items fail kèm reproduction steps để executor fix.
  </resume-signal>
</task>

</tasks>

<verification>
- `cd sources/frontend && npx tsc --noEmit` → exit 0
- `cd sources/frontend && npm run build` → exit 0, no warnings related to phase 14 files
- Grep audit:
  - `grep -n "priceRange\|filteredProducts\|products.filter(" sources/frontend/src/app/products/page.tsx` → 0 matches (state cũ + client filter loại sạch)
  - `grep -n "FilterSidebar\|listBrands\|filterBrands" sources/frontend/src/app/products/page.tsx` → ≥ 6 matches
- Manual UAT checklist 14/14 items PASS — confirmed by user
</verification>

<success_criteria>
- ROADMAP §"Phase 14 SC-1": `/products` hiển thị FilterSidebar với brand checkboxes (DISTINCT từ DB) + price min/max — VERIFIED bằng UAT items 1+4
- ROADMAP §"Phase 14 SC-2": tick 2 brands + set price → backend JPQL filter đúng (same-facet OR, cross-facet AND) — VERIFIED UAT items 2+3+4
- ROADMAP §"Phase 14 SC-3": validate min > max client-side → KHÔNG fire request — VERIFIED UAT item 6
- ROADMAP §"Phase 14 SC-4": reset button → xóa toàn bộ facet selection về default — VERIFIED UAT item 8
- D-09 local React state, no URL encoding — VERIFIED bằng URL không có brand/price params sau refresh page
- D-11 page reset on filter change — VERIFIED UAT item 11 + grep `page: 0`
- D-12 empty state copy match — VERIFIED UAT item 10 + grep
- D-10 reset KHÔNG đụng categories — VERIFIED UAT item 12
- KHÔNG dependency mới — VERIFIED git diff sources/frontend/package.json empty
</success_criteria>

<output>
Sau khi UAT approve, tạo `.planning/phases/14-basic-search-filters/14-03-SUMMARY.md` ghi:
- Files modified (paths + diff line counts)
- tsc + build kết quả
- UAT 14/14 PASS confirmation (hoặc list items deferred + rationale)
- Mark Phase 14 ready for verification (`/gsd-verify-phase 14`)
- Note SEARCH-01 + SEARCH-02 → DONE; SEARCH-03 + SEARCH-04 vẫn deferred v1.3 theo ROADMAP
</output>
