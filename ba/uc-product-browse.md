# UC-PRODUCT-BROWSE: Duyệt/Tìm kiếm/Lọc/Chi tiết sản phẩm

## Tóm tắt
Customer và Guest duyệt catalog qua homepage, category listing, search, detail page. Filter theo brand/giá/rating/inStock. Sort theo giá/new/rating. Product detail hiển thị images, spec, stock, price, reviews summary.

## Context Links
- Strategy: [../strategy/services/product-business.md](../strategy/services/product-business.md)
- Technical Spec: [../technical-spec/ts-product-browse.md](../technical-spec/ts-product-browse.md)
- Architecture: [../architecture/services/product-service.md](../architecture/services/product-service.md)

## Actors
- **Primary**: Customer (logged-in), Guest (không login)
- **Secondary**: Search indexer (Postgres FTS)

## Preconditions
- Không có (public endpoints)

---

## Flow A — Homepage

### Main Flow
1. User vào `/`
2. FE (SSR) gọi song song:
   - GET /api/v1/products?featured=true&limit=8 — sản phẩm nổi bật
   - GET /api/v1/products?sort=createdAt,desc&limit=8 — mới nhất
   - GET /api/v1/products?sort=soldCount,desc&limit=8 — bán chạy (MVP dùng rating)
   - GET /api/v1/categories/tree
3. FE render: hero banner + category nav + 3 product rails + footer
4. SEO: metadata tĩnh + JSON-LD Organization

### Acceptance Criteria
- [ ] AC-A1: Homepage load p95 < 2s (SSR)
- [ ] AC-A2: Category menu hover reveal subcategories
- [ ] AC-A3: Click product card → detail page

---

## Flow B — Category Listing

### Main Flow
1. User click category "Điện thoại" → `/category/dien-thoai`
2. FE (SSR + revalidate 1min) gọi:
   - GET /api/v1/categories/{slug} → validate + get name
   - GET /api/v1/products?category={slug}&page=0&size=20&sort=createdAt,desc
3. FE render:
   - Breadcrumb: Home > Điện thoại
   - Sidebar filter (brand, price range, rating, inStock)
   - Main: grid sản phẩm + pagination
   - Sort dropdown top-right

### Filter Interaction
1. User check "Apple" brand
2. FE update URL query `?brand=Apple`
3. FE (CSR fetch) gọi GET với query mới
4. Grid re-render instantly (React Query + cache)

### Pagination
- Page size 20, max 50 (via query)
- URL sync: `?page=2`
- Mobile: infinite scroll (load more); Desktop: page number

### Acceptance Criteria
- [ ] AC-B1: Filter multi-select brand, applied ngay khi chọn
- [ ] AC-B2: Sort theo: Mới nhất (default), Giá tăng, Giá giảm, Đánh giá cao, Bán chạy
- [ ] AC-B3: URL shareable (filter state trong query param)
- [ ] AC-B4: Empty state: "Không có sản phẩm phù hợp" với gợi ý clear filter

### Data Inputs (query params)
- `category` (slug)
- `brand` (comma-separated)
- `priceMin`, `priceMax` (integer)
- `rating` (>= value, 1-5)
- `inStock` (boolean)
- `sort` (field,direction)
- `page`, `size`

### Exception Flows
- **EF-B1: Category không tồn tại** → 404 page
- **EF-B2: Invalid filter value** (priceMin > priceMax) → FE swap hoặc 400

---

## Flow C — Search

### Main Flow
1. User nhập keyword ở SearchBar (header)
2. FE debounce 300ms
3. FE gọi GET /api/v1/products/search?q={keyword}&limit=5 → autocomplete dropdown (max 5 results + "Xem tất cả")
4. User click 1 result → product detail
5. Hoặc click "Xem tất cả" → `/search?q={keyword}` full results

### Full Search Page
1. FE (SSR) gọi GET /api/v1/products/search?q=&page=0&size=20
2. Render giống category listing nhưng title "Kết quả cho: {keyword}"
3. Filters + sort giống category

### Search Behavior
- Min 2 ký tự mới search
- Case-insensitive, bỏ dấu
- Match trên: name, brand, description
- Nếu không kết quả → suggest: "Có phải bạn muốn tìm: {suggestion}" (MVP: không suggest, chỉ empty state)

### Acceptance Criteria
- [ ] AC-C1: Autocomplete dropdown hiển thị trong 500ms
- [ ] AC-C2: Search bỏ dấu ("dien thoai" match "điện thoại")
- [ ] AC-C3: Empty state rõ: "Không tìm thấy sản phẩm nào cho '{keyword}'"
- [ ] AC-C4: Search bar trên mọi page (trong header)

---

## Flow D — Product Detail

### Main Flow
1. User click product card → `/product/{slug}`
2. FE (SSR + revalidate 30s) gọi:
   - GET /api/v1/products/{slug} — full detail
   - GET /api/v1/products/{id}/reviews?page=0&size=5 — top reviews
3. FE render:
   - Breadcrumb
   - Left: image gallery (thumbnail + main image, swipe mobile)
   - Right: name, brand, rating summary, price (strikethrough if salePrice), stock status, quantity selector, Add to Cart button, Buy Now button
   - Tabs: Description, Specs table (từ `specs` JSON), Reviews
4. Related products rail bottom

### Stock Status Display
- `stock = 0` → "Hết hàng", disable buttons, change button text
- `stock 1-10` → badge "Chỉ còn X sản phẩm" (warning color)
- `stock > 10` → không hiển thị số, chỉ "Còn hàng"

### Add to Cart Interaction
1. User chọn quantity (1-10)
2. Click "Thêm vào giỏ"
3. If not logged in:
   - Option A: redirect login (MVP)
   - Option B: lưu local cart + prompt login khi checkout (phase 2)
4. If logged in:
   - FE gọi POST /api/v1/cart/items
   - BE check stock > 0
   - FE hiển thị mini-cart drawer với item vừa thêm + toast

### Specs Rendering
- Render `specs` JSON theo category template:
  - Phone: RAM, Storage, Screen, Battery, Camera, Chip, OS
  - Laptop: CPU, RAM, SSD, Screen, GPU, OS, Weight
- Unknown keys: render fallback key-value

### Exception Flows
- **EF-D1: Product không tồn tại** → 404
- **EF-D2: Product INACTIVE** → 404 (không public)
- **EF-D3: Stock = 0** → disable add-to-cart, hiển thị "Hết hàng"
- **EF-D4: Add quantity > stock** → FE cap max = stock, warning

### Acceptance Criteria
- [ ] AC-D1: Page load p95 < 1.5s (SSR)
- [ ] AC-D2: Image gallery: max 10, thumbnail + zoom mobile
- [ ] AC-D3: Price: giá gốc strikethrough + giá sale bold nếu có
- [ ] AC-D4: Quantity: 1-10, validate client-side
- [ ] AC-D5: Meta tags: og:title, og:image (primary image), og:description
- [ ] AC-D6: JSON-LD Product schema cho SEO

### Data Outputs (GET /products/{slug})
```json
{
  "id": "uuid",
  "sku": "APPLE-IP15PRO-256",
  "slug": "iphone-15-pro-256gb",
  "name": "iPhone 15 Pro 256GB",
  "brand": "Apple",
  "category": { "id": "uuid", "name": "iPhone", "slug": "iphone" },
  "description": "...",
  "price": 28990000,
  "salePrice": 26990000,
  "effectivePrice": 26990000,
  "stock": 15,
  "inStock": true,
  "images": ["https://cdn.../1.jpg", "..."],
  "specs": {
    "ram": "8GB",
    "storage": "256GB",
    "screen_size": "6.1 inch",
    "chip": "A17 Pro"
  },
  "rating": 4.7,
  "reviewCount": 128
}
```

---

## Business Rules (references)
- BR-PRICING-01, -02: Price display rules
- BR-PRODUCT-01: Status ACTIVE mới hiển thị
- BR-PRODUCT-04: Images rules
- BR-INVENTORY-01, -07: Stock display

## Non-functional Requirements
- **Performance**:
  - Homepage p95 < 2s
  - Category listing p95 < 1.5s
  - Product detail p95 < 1s (SSR)
  - Autocomplete < 500ms
- **SEO**:
  - Product detail: metadata + JSON-LD
  - Sitemap auto-gen
  - Breadcrumb schema
- **Availability**: 99.5%
- **Concurrent users**: support 500 concurrent browsing

## UI Screens
- `/` (homepage)
- `/category/{slug}` (category listing)
- `/product/{slug}` (product detail)
- `/search?q=` (search results)
