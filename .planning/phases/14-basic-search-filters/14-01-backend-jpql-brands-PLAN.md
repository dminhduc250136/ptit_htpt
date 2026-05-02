---
phase: 14-basic-search-filters
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
  - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java
autonomous: true
requirements:
  - SEARCH-01
  - SEARCH-02
must_haves:
  truths:
    - "GET /api/products?brands=Dell&brands=HP trả về chỉ products có brand IN (Dell, HP)"
    - "GET /api/products?priceMin=5000000&priceMax=10000000 trả về chỉ products có price BETWEEN 5tr và 10tr"
    - "GET /api/products?keyword=laptop&brands=Dell&priceMin=5000000 áp dụng cross-facet AND (keyword AND brand AND price)"
    - "GET /api/products/brands trả về danh sách brand DISTINCT alphabetical, không null, không deleted"
    - "Response giữ nguyên ApiResponse envelope + Page-shape (content/totalElements/totalPages/currentPage/pageSize/isFirst/isLast)"
  artifacts:
    - path: "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java"
      provides: "findWithFilters JPQL + findDistinctBrands"
      contains: "findWithFilters"
    - path: "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java"
      provides: "listProducts(brands, priceMin, priceMax) overload + listBrands()"
      contains: "listBrands"
    - path: "sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java"
      provides: "Extended /products params + new GET /products/brands"
      contains: "/brands"
  key_links:
    - from: "ProductController.listProducts"
      to: "ProductCrudService.listProducts(8-arg)"
      via: "method delegation"
      pattern: "productCrudService\\.listProducts\\("
    - from: "ProductCrudService.listProducts(8-arg)"
      to: "ProductRepository.findWithFilters"
      via: "JPQL Page<ProductEntity> query"
      pattern: "productRepo\\.findWithFilters"
    - from: "ProductController.listBrands"
      to: "ProductRepository.findDistinctBrands"
      via: "service.listBrands() delegate"
      pattern: "findDistinctBrands"
---

<objective>
Refactor product-svc list endpoint sang JPQL optional params (D-06, D-07, D-08) — hỗ trợ keyword + brand multi-select + price range. Thêm endpoint mới `GET /products/brands` cho FE FilterSidebar fetch danh sách thương hiệu (D-03).

Purpose: Loại bỏ in-memory `.filter()` (ProductCrudService.java:42-47) — không scale với Pageable thật; ship backend foundation cho SEARCH-01 + SEARCH-02 trước khi FE wire.
Output: 3 file Java modified — repository thêm 2 query methods, service thêm overload + listBrands(), controller extend params + new /brands endpoint. Build PASS, manual curl PASS.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/14-basic-search-filters/14-CONTEXT.md
@.planning/phases/14-basic-search-filters/14-PATTERNS.md
@sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java
@sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java
@sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java
@sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java

<interfaces>
Existing — KHÔNG được phá:

```java
// ProductRepository.java (current)
public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  Optional<ProductEntity> findBySlug(String slug);
}

// ProductCrudService.java line 33 — overload 5-arg dùng nội bộ (giữ lại, delegate sang 8-arg mới)
public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted)
public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted, String keyword)

// ApiResponse envelope (KHÔNG thay đổi)
ApiResponse.of(int status, String message, T data)

// ProductEntity có @SQLRestriction("deleted = false") → JPQL KHÔNG cần AND p.deleted = false
// ProductEntity.brand: nullable String; ProductEntity.price: BigDecimal
```

Interface mới sẽ tạo (downstream Plan 03 tiêu thụ):

```java
// ProductRepository — NEW methods
@Query(...) Page<ProductEntity> findWithFilters(String keyword, List<String> brands,
    BigDecimal priceMin, BigDecimal priceMax, Pageable pageable);
@Query(...) List<String> findDistinctBrands();

// ProductCrudService — NEW overload + method
public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted,
    String keyword, List<String> brands, BigDecimal priceMin, BigDecimal priceMax);
public List<String> listBrands();

// ProductController — endpoint mới
GET /api/products/brands → ApiResponse<List<String>>
GET /api/products?brands=&priceMin=&priceMax=&... → ApiResponse<Map> (mở rộng params, response shape unchanged)
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: ProductRepository — JPQL findWithFilters + findDistinctBrands</name>
  <files>sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java</files>
  <read_first>
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java (current — chỉ 9 dòng)
    - sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java (analog lines 16-33 — JPQL optional params với cast(:param as type))
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java (xác nhận fields name, brand, price, @SQLRestriction)
  </read_first>
  <action>
    Thêm 2 methods vào ProductRepository, GIỮ NGUYÊN `findBySlug`. Thêm imports: `java.math.BigDecimal`, `java.util.List`, `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`.

    Method 1 — `findWithFilters` (D-06, D-07):
    ```java
    @Query("SELECT p FROM ProductEntity p WHERE " +
           "(cast(:keyword as string) IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', cast(:keyword as string), '%'))) " +
           "AND (:brands IS NULL OR p.brand IN :brands) " +
           "AND (cast(:priceMin as big_decimal) IS NULL OR p.price >= :priceMin) " +
           "AND (cast(:priceMax as big_decimal) IS NULL OR p.price <= :priceMax)")
    Page<ProductEntity> findWithFilters(
        @Param("keyword") String keyword,
        @Param("brands") List<String> brands,
        @Param("priceMin") BigDecimal priceMin,
        @Param("priceMax") BigDecimal priceMax,
        Pageable pageable);
    ```
    Sort/order do `Pageable` quyết định — KHÔNG hardcode `ORDER BY` trong JPQL. `@SQLRestriction("deleted = false")` trên ProductEntity tự loại deleted — KHÔNG cần thêm `AND p.deleted = false`.

    Method 2 — `findDistinctBrands` (D-03):
    ```java
    @Query("SELECT DISTINCT p.brand FROM ProductEntity p " +
           "WHERE p.brand IS NOT NULL AND p.brand <> '' ORDER BY p.brand ASC")
    List<String> findDistinctBrands();
    ```

    Javadoc kiểu Phase 11 (xem OrderRepository.java line 16-20, 35-41) — comment phase + decision IDs.
  </action>
  <verify>
    <automated>cd sources/backend/product-service && ./mvnw -q compile</automated>
  </verify>
  <acceptance_criteria>
    - File `ProductRepository.java` chứa exactly substring `findWithFilters` (grep PASS)
    - File chứa exactly substring `findDistinctBrands` (grep PASS)
    - File chứa substring `cast(:keyword as string) IS NULL` (grep PASS — JPQL pattern đúng)
    - File chứa substring `:brands IS NULL OR p.brand IN :brands` (grep PASS)
    - File chứa substring `Page<ProductEntity>` (grep PASS — return type đúng)
    - `findBySlug` method vẫn còn (grep `findBySlug` returns 1 match)
    - Maven compile PASS exit 0
  </acceptance_criteria>
  <done>
    Repository compile clean, 2 methods mới + findBySlug cũ cùng tồn tại, JPQL syntax verified bằng compile.
  </done>
</task>

<task type="auto">
  <name>Task 2: ProductCrudService — extend listProducts + add listBrands</name>
  <files>sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java</files>
  <read_first>
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java (current — đặc biệt lines 33-53 in-memory filter cần REPLACE, lines 211-229 paginate helper)
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java (sau Task 1 — để biết signature method mới)
  </read_first>
  <action>
    Bước 1 — Thêm imports: `org.springframework.data.domain.Page`, `org.springframework.data.domain.PageRequest`, `org.springframework.data.domain.Pageable`, `org.springframework.data.domain.Sort`. Giữ nguyên các imports hiện có.

    Bước 2 — Refactor 2 overload `listProducts` cũ (lines 33-53) thành 3 overload (giữ backward compat với callers ngoài Phase 14):

    ```java
    public Map<String, Object> listProducts(int page, int size, String sort, boolean includeDeleted) {
      return listProducts(page, size, sort, includeDeleted, null, null, null, null);
    }

    public Map<String, Object> listProducts(int page, int size, String sort,
                                            boolean includeDeleted, String keyword) {
      return listProducts(page, size, sort, includeDeleted, keyword, null, null, null);
    }

    public Map<String, Object> listProducts(int page, int size, String sort,
                                            boolean includeDeleted, String keyword,
                                            List<String> brands, BigDecimal priceMin,
                                            BigDecimal priceMax) {
      // Normalize: empty/blank → null để JPQL `IS NULL` clause skip
      String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword;
      List<String> normalizedBrands = (brands == null || brands.isEmpty()) ? null : brands;

      Pageable pageable = PageRequest.of(Math.max(page, 0),
          size <= 0 ? 20 : Math.min(size, 100), parseSort(sort));

      Page<ProductEntity> resultPage = productRepo.findWithFilters(
          normalizedKeyword, normalizedBrands, priceMin, priceMax, pageable);

      Map<String, Object> response = new LinkedHashMap<>();
      response.put("content", resultPage.getContent().stream().map(this::toResponse).toList());
      response.put("totalElements", resultPage.getTotalElements());
      response.put("totalPages", resultPage.getTotalPages());
      response.put("currentPage", resultPage.getNumber());
      response.put("pageSize", resultPage.getSize());
      response.put("isFirst", resultPage.isFirst());
      response.put("isLast", resultPage.isLast());
      return response;
    }
    ```

    Bước 3 — Thêm helper `parseSort` (private static):
    ```java
    private static Sort parseSort(String sort) {
      if (sort == null || sort.isBlank()) {
        return Sort.by(Sort.Direction.DESC, "updatedAt");
      }
      String[] parts = sort.split(",");
      String field = parts[0].trim();
      Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
          ? Sort.Direction.DESC : Sort.Direction.ASC;
      return Sort.by(dir, field);
    }
    ```
    LƯU Ý: `includeDeleted` param hiện không còn ý nghĩa vì `@SQLRestriction` filter ở SQL layer — giữ chữ ký để KHÔNG break callers, ignore value (đồng bộ comment hiện có lines 39-41).

    Bước 4 — Thêm method `listBrands()` (D-03), đặt SAU `listCategories` để gom theo nhóm read-only:
    ```java
    public List<String> listBrands() {
      return productRepo.findDistinctBrands();
    }
    ```

    Bước 5 — XOÁ `productComparator` cũ (lines 189-198) — không còn caller (sort giờ qua `parseSort` + `Pageable`). Method `categoryComparator` GIỮ LẠI (vẫn dùng cho `listCategories`).

    Bước 6 — `paginate` helper (lines 211-229) GIỮ LẠI vì `listCategories` (line 117) vẫn dùng. KHÔNG xoá.
  </action>
  <verify>
    <automated>cd sources/backend/product-service && ./mvnw -q compile</automated>
  </verify>
  <acceptance_criteria>
    - File chứa substring `public List<String> listBrands()` (grep PASS)
    - File chứa substring `productRepo.findWithFilters` (grep PASS — service đã wire repository method mới)
    - File chứa substring `private static Sort parseSort` (grep PASS)
    - File chứa 3 overload `listProducts(` (grep `public Map<String, Object> listProducts` returns 3 matches)
    - File KHÔNG còn substring `productComparator` (grep returns 0 matches)
    - File KHÔNG còn substring `productRepo.findAll().stream()` (grep returns 0 matches — in-memory filter đã loại)
    - Maven compile PASS exit 0
  </acceptance_criteria>
  <done>
    Service compile clean, listProducts 8-arg overload delegate sang `findWithFilters`, listBrands() delegate sang `findDistinctBrands`, in-memory filter cũ đã xoá, callers cũ vẫn build (overload 4-arg + 5-arg vẫn còn).
  </done>
</task>

<task type="auto">
  <name>Task 3: ProductController — extend params + new /brands endpoint + smoke test</name>
  <files>sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java</files>
  <read_first>
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java (current — đặc biệt lines 31-40 listProducts, lines 71-78 listCategories template cho /brands)
    - sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java (sau Task 2 — biết signature 8-arg + listBrands)
  </read_first>
  <action>
    Bước 1 — Thêm imports: `java.math.BigDecimal`, `java.util.List` (nếu chưa có).

    Bước 2 — Mở rộng method `listProducts` (lines 31-40 hiện tại). Signature mới:
    ```java
    @GetMapping
    public ApiResponse<Map<String, Object>> listProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "updatedAt,desc") String sort,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) List<String> brands,
        @RequestParam(required = false) BigDecimal priceMin,
        @RequestParam(required = false) BigDecimal priceMax
    ) {
      return ApiResponse.of(200, "Products listed",
          productCrudService.listProducts(page, size, sort, false, keyword, brands, priceMin, priceMax));
    }
    ```
    Spring tự bind `?brands=Dell&brands=HP` → `List<String>{"Dell","HP"}` (D-08 repeatable param).

    Bước 3 — Thêm endpoint `GET /products/brands` (D-03), đặt SAU `listCategories` (line 71-78) để gom theo nhóm reference data:
    ```java
    @GetMapping("/brands")
    public ApiResponse<List<String>> listBrands() {
      return ApiResponse.of(200, "Brands listed", productCrudService.listBrands());
    }
    ```

    Bước 4 — KHÔNG sửa các endpoint khác (POST/PUT/DELETE products, categories CRUD). Giữ nguyên chú thích Javadoc nếu có.

    Bước 5 — Build + smoke test:
    ```
    cd sources/backend/product-service
    ./mvnw -q -DskipTests package
    ```
    Nếu Docker stack đang chạy (`docker compose ps` show product-service Up), chạy:
    ```
    curl -sf "http://localhost:8082/api/products/brands" | head -c 200
    curl -sf "http://localhost:8082/api/products?priceMin=1000000&priceMax=99999999&page=0&size=5" | head -c 500
    ```
    Nếu Docker không chạy — skip curl, đánh dấu UAT debt cho Wave 2 verification (đồng bộ với pattern Phase 11 UAT pending).
  </action>
  <verify>
    <automated>cd sources/backend/product-service && ./mvnw -q -DskipTests package</automated>
  </verify>
  <acceptance_criteria>
    - File chứa substring `@RequestParam(required = false) List<String> brands` (grep PASS)
    - File chứa substring `@RequestParam(required = false) BigDecimal priceMin` (grep PASS)
    - File chứa substring `@RequestParam(required = false) BigDecimal priceMax` (grep PASS)
    - File chứa substring `@GetMapping("/brands")` (grep PASS)
    - File chứa substring `productCrudService.listBrands()` (grep PASS)
    - Maven package PASS exit 0
    - (Optional, nếu Docker up) `curl -sf .../products/brands` trả 200 + JSON `{status:200, data:[...]}` envelope
    - (Optional, nếu Docker up) `curl -sf .../products?priceMin=1000000&priceMax=99999999` trả 200 + content array nonempty
  </acceptance_criteria>
  <done>
    Controller compile + package PASS, endpoint mới + extended params đăng ký vào Spring routing. Manual curl smoke test PASS nếu stack chạy, hoặc ghi note "UAT pending docker stack" trong SUMMARY.
  </done>
</task>

</tasks>

<verification>
- `cd sources/backend/product-service && ./mvnw -q -DskipTests package` → BUILD SUCCESS
- Grep audit: `grep -rn "findWithFilters\|findDistinctBrands\|listBrands" sources/backend/product-service/src/main/java/` → 6+ matches across repository/service/controller
- Optional curl smoke (Docker up):
  - `curl -sf http://localhost:8082/api/products/brands` → 200, body có `"data":[...]`
  - `curl -sf "http://localhost:8082/api/products?brands=Dell"` → 200, content chỉ chứa products có `"brand":"Dell"`
  - `curl -sf "http://localhost:8082/api/products?priceMin=99999999"` → 200, content có thể empty array (acceptable)
</verification>

<success_criteria>
- ROADMAP §"Phase 14 SC-2": JPQL `WHERE (:brands IS NULL OR brand IN :brands) AND (:priceMin IS NULL OR price >= :priceMin) AND (:priceMax IS NULL OR price <= :priceMax)` — VERIFIED bằng grep ProductRepository.java
- D-06 in-memory filter loại bỏ — VERIFIED bằng grep `productRepo.findAll().stream()` returns 0
- D-07 same-facet OR (`brand IN :brands`), cross-facet AND (clauses ghép `AND`) — VERIFIED grep
- D-08 controller params `brands` (List<String> repeatable), `priceMin`, `priceMax` (BigDecimal optional) — VERIFIED grep
- D-03 endpoint `/products/brands` trả DISTINCT alphabetical — VERIFIED grep + manual curl nếu Docker up
- KHÔNG có dependency mới trong `pom.xml` (vanilla Spring Data JPA) — VERIFIED git diff pom.xml empty
</success_criteria>

<output>
Sau khi complete, tạo `.planning/phases/14-basic-search-filters/14-01-SUMMARY.md` ghi:
- Files modified + line counts
- Maven build kết quả
- Curl smoke test kết quả (hoặc "UAT pending docker stack")
- Note bất kỳ deviation nào khỏi PATTERNS.md (e.g. nếu phải xử lý edge case JPQL Hibernate version)
- Ready signal cho Plan 03 wire FE
</output>
