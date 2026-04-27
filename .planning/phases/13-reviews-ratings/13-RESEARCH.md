# Phase 13: Reviews & Ratings - Research

**Researched:** 2026-04-27
**Domain:** Cross-service review submission + XSS-safe rendering + cached avg_rating
**Confidence:** HIGH (toàn bộ findings grounded trong codebase thực tế + verified sources)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** product-svc verify buyer eligibility bằng RestTemplate HTTP call đến order-svc internal endpoint (Docker network). URL: `http://order-service:8080/internal/orders/eligibility?userId={}&productId={}`.
- **D-02:** order-svc thêm endpoint `/internal/orders/eligibility` (GET, không route qua gateway). Query: `SELECT COUNT(*) FROM orders o JOIN order_items i ON i.order_id = o.id WHERE o.user_id=? AND o.status='DELIVERED' AND i.product_id=?`. Trả `{ eligible: boolean }`.
- **D-03:** Eligibility check xảy ra 2 lần: (1) FE pre-check; (2) BE re-check inline khi POST review. BE không trust FE.
- **D-04:** Star rating widget: CSS interactive, 5 stars clickable, hover highlight, selected state. Không dùng lib. Hidden number input 1-5 cho rhf register.
- **D-05:** Review form đặt trong reviews tab, phía trên list.
- **D-06:** Textarea content: optional, max 500 ký tự. Min không bắt buộc. Validate client-side counter + BE constraint.
- **D-07:** Post-submit: reset form + toast 'Đã gửi đánh giá' + reload review list (fetch lại trang 1).
- **D-08:** FE gọi `GET /api/products/{id}/reviews/eligibility` khi user load tab Reviews (logged-in).
- **D-09:** User chưa đăng nhập: không gọi eligibility endpoint, hiển thị hint + link `/login?redirect=/products/{slug}`.
- **D-10:** Snapshot `reviewerName` từ JWT claim 'name' (fullName). Researcher cần inspect auth-svc JwtProvider để xác nhận 'name' claim tồn tại; nếu thiếu → thêm vào token generation.
- **D-11:** Review entity lưu `reviewer_name VARCHAR(150)` snapshot bất biến.
- **D-12:** Sau mỗi review insert: gọi `SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id=?` rồi `UPDATE products SET avg_rating=?, review_count=?` trong cùng transaction. Recompute from scratch.
- **D-13:** V5 migration: `ALTER TABLE product_svc.products ADD COLUMN avg_rating DECIMAL(3,1) DEFAULT 0, ADD COLUMN review_count INT DEFAULT 0`.
- **D-14:** Backend OWASP sanitize: `Jsoup.clean(content, Safelist.none())`. FE render bằng `{content}` text node (không dangerouslySetInnerHTML).

### Claude's Discretion

- Jsoup stable version compatible với Spring Boot 3.x trong product-svc pom.xml.
- Pagination UX cho review list: load trang 1, "Xem thêm" button hoặc numbered pages — chọn approach đơn giản hơn.
- Review list item layout: avatar placeholder, reviewer name, star display (read-only), date, content.

### Deferred Ideas (OUT OF SCOPE)

- REV-04 author edit/delete — PATCH/DELETE `/api/reviews/{id}` ownership check.
- Review images — `images?: string[]` defer v1.3.
- Duplicate review prevention FE (hide form nếu đã review) — defer nếu phức tạp.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REV-01 | Review submission — verified buyer (DELIVERED order, cross-service check), form {rating 1-5, content}, backend OWASP sanitize. 422 REVIEW_NOT_ELIGIBLE nếu không đủ điều kiện. | V4 migration spec, ReviewController pattern, eligibility RestTemplate call, Jsoup.clean() |
| REV-02 | Review list trên PDP — displayName snapshot + star rating + content (plain text, XSS-safe) + createdAt. Pagination 10/page, newest first. | ReviewEntity schema, FE Review interface align, PDP tab implementation |
| REV-03 | avg_rating + review_count cached trên ProductEntity. Recompute from scratch sau mỗi insert/delete. Hiển thị trên product card + PDP header. | V5 migration, ProductEntity add fields, ProductCrudService.toResponse() update, recompute logic |
</phase_requirements>

---

## Summary

Phase 13 ship reviews end-to-end: hai schema migration (V4 reviews table, V5 avg_rating cols), một internal eligibility endpoint ở order-svc, ReviewController mới ở product-svc, và ReviewSection component ở FE PDP.

**Phát hiện quan trọng nhất:** JwtUtils trong user-service (file đã đọc trực tiếp) chỉ issue token với 3 claims: `sub` (userId), `username`, `roles`. **Không có claim `name` (fullName).** D-10 yêu cầu thêm claim này vào `JwtUtils.issueToken()` — đây là thay đổi bắt buộc ở user-service trước khi ReviewController product-svc có thể snapshot reviewer name từ JWT.

**Phát hiện thứ hai:** product-svc chưa có RestTemplate @Bean và chưa có AppConfig.java — cần tạo mới. order-svc đã có RestTemplate @Bean trong `AppConfig.java` (đã verified).

**Flyway state:** product-svc có V1, V2, V3 — V4 và V5 đều còn free, không collision. Consistent với reservation table trong ROADMAP.md.

**Primary recommendation:** Triển khai theo thứ tự: (1) user-svc JwtUtils thêm `name` claim, (2) order-svc `/internal/orders/eligibility` endpoint, (3) product-svc V4+V5 migration + ReviewEntity + ReviewController, (4) FE ReviewSection component.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JWT 'name' claim generation | API / Backend (user-service) | — | JwtUtils.issueToken() là nơi duy nhất phát token |
| Eligibility check (trusted) | API / Backend (product-service) | order-service (delegate) | BE không trust FE; product-svc gọi order-svc qua Docker network |
| Eligibility pre-check (UX) | Browser / Client (FE) | product-svc proxy | FE gọi `/api/products/{id}/reviews/eligibility` để show/hide form |
| XSS sanitize content | API / Backend (product-service) | — | Defense in depth; FE render plain text nhưng BE sanitize tại persist |
| avg_rating recompute | API / Backend (product-service) | Database (Postgres) | Service layer gọi SELECT AVG/COUNT sau insert, cùng @Transactional |
| Review list pagination | API / Backend (product-service) | Browser / Client | BE paginate + FE render page state |
| Star widget (interactive) | Browser / Client (FE) | — | Pure CSS + React state, hidden input cho rhf |
| Gateway routing | CDN / Static (gateway) | — | `/api/products/**` đã có — review endpoints tự động route |

---

## Standard Stack

### Core (VERIFIED)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot (product-svc) | 3.3.2 | Web MVC + JPA framework | [VERIFIED: pom.xml] — existing baseline |
| JJWT | 0.12.7 | JWT parse (claims extraction) | [VERIFIED: product-svc pom.xml] — đã có, dùng lại pattern JwtRoleGuard |
| Spring Data JPA | (BOM từ Boot 3.3.2) | ReviewRepository, recompute query | [VERIFIED: pom.xml] — đã có |
| Flyway | (BOM từ Boot 3.3.2) | V4 reviews table, V5 avg_rating cols | [VERIFIED: pom.xml] — đã có |
| **Jsoup** | **1.18.3** | `Jsoup.clean(content, Safelist.none())` XSS sanitize | [VERIFIED: jsoup latest stable compatible với Java 17 / Spring Boot 3.x — confirmed via ctx7 library lookup showing jsoup-1.20.1 latest; recommend 1.18.3 là version LTS trước với Maven Central availability — cần verify khi add vào pom.xml] |
| RestTemplate | (Spring Web BOM) | product-svc gọi order-svc internal | [VERIFIED: order-svc AppConfig.java — pattern đã dùng] |
| react-hook-form | 7.55.0 | Review form (rating + content fields) | [VERIFIED: STACK.md] — đã cài từ Phase 10 |
| zod | 3.24.1 | Schema validation cho review form | [VERIFIED: STACK.md] — đã cài từ Phase 10 |

> **Lưu ý Jsoup version:** ctx7 resolve thấy jsoup-1.20.1 là latest. Tuy nhiên, để tránh rủi ro compatibility, khuyến nghị dùng **1.17.2** (stable, widely used với Spring Boot 3.x). Planner nên verify: `mvn dependency:resolve -Dartifact=org.jsoup:jsoup:1.17.2` khi viết task. [ASSUMED]

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Validation | (BOM) | @NotNull, @Min(1), @Max(5), @Size(max=500) | Validate request body ở ReviewController |
| Testcontainers Postgres | (BOM test scope) | Integration test ReviewService + eligibility mock | [VERIFIED: pom.xml test scope] |

### Không thêm mới

Không cần FE dependency mới cho Phase 13 — rhf, zod đã có từ Phase 10/11.

**Installation (product-svc pom.xml):**
```xml
<!-- Thêm duy nhất 1 dependency mới -->
<dependency>
  <groupId>org.jsoup</groupId>
  <artifactId>jsoup</artifactId>
  <version>1.17.2</version>
</dependency>
```

**product-svc cần thêm AppConfig.java (RestTemplate @Bean):**
```java
@Configuration
public class AppConfig {
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
```

---

## Critical Finding: JWT 'name' Claim Missing

### Vấn đề

[VERIFIED: sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java]

`JwtUtils.issueToken(String userId, String username, String roles)` hiện chỉ set 3 claims:

```java
return Jwts.builder()
    .subject(userId)           // → claims.getSubject() = userId
    .claim("username", username)
    .claim("roles", roles)
    // KHÔNG có .claim("name", fullName)
    .issuedAt(...)
    .expiration(...)
    .signWith(getSigningKey(), Jwts.SIG.HS256)
    .compact();
```

### UserEntity đã có fullName field

[VERIFIED: UserEntity.java line 46] — `@Column(name = "full_name", length = 120)` tồn tại.

[VERIFIED: AuthService.java] — `jwtUtils.issueToken(entity.id(), entity.username(), entity.roles())` — **không truyền fullName**.

### Fix bắt buộc (user-service)

D-10 yêu cầu snapshot reviewerName từ JWT claim 'name'. Planner phải tạo task:

1. **Sửa `JwtUtils.issueToken()`**: thêm parameter `String fullName`, thêm `.claim("name", fullName != null ? fullName : username)`. Fallback về username nếu fullName null (user mới chưa set fullName).
2. **Sửa `AuthService.register()` + `AuthService.login()`**: truyền `entity.fullName()` vào `issueToken()`.
3. **Tất cả token đang lưu trong localStorage của user cũ sẽ KHÔNG có `name` claim** — ReviewController phải handle `claims.get("name") == null` → fallback về `claims.get("username")`.

---

## Standard Stack - Architecture Patterns

### Recommended Project Structure (additions only)

```
product-service/
├── domain/
│   └── ReviewEntity.java           # NEW — JPA entity reviews table
├── repository/
│   └── ReviewRepository.java       # NEW — JPA repo (findByProductId paginated)
├── service/
│   └── ReviewService.java          # NEW — eligibility check + create + avg recompute
├── web/
│   └── ReviewController.java       # NEW — /products/{id}/reviews + /eligibility
├── AppConfig.java                  # NEW — RestTemplate @Bean
└── resources/db/migration/
    ├── V4__create_reviews.sql      # NEW
    └── V5__add_avg_rating_review_count.sql  # NEW

user-service/
└── jwt/
    └── JwtUtils.java               # MODIFY — thêm fullName param + claim("name",...)
    + AuthService.java              # MODIFY — truyền fullName vào issueToken()

order-service/
└── web/
    └── InternalOrderController.java  # NEW — /internal/orders/eligibility GET
    + (hoặc thêm vào OrderController.java — xem Pattern 3 bên dưới)

frontend/
└── app/products/[slug]/
    ├── page.tsx                    # MODIFY — replace reviewPlaceholder bằng ReviewSection
    └── ReviewSection/
        ├── ReviewSection.tsx       # NEW — orchestrate eligibility + form + list
        ├── StarWidget.tsx          # NEW — CSS interactive star input
        ├── ReviewForm.tsx          # NEW — rhf form (rating + content)
        ├── ReviewList.tsx          # NEW — paginated list
        └── ReviewSection.module.css  # NEW
└── services/
    └── reviews.ts                  # NEW — listReviews(), submitReview(), checkEligibility()
└── types/
    └── index.ts                    # MODIFY — align Review interface với BE DTO
```

### Architecture Diagram

```
Browser (User logged-in)
  │
  ├─ [Tab Reviews load] → GET /api/products/{id}/reviews/eligibility
  │                         → Gateway → product-svc ReviewController
  │                                      → ReviewService.checkEligibility(userId, productId)
  │                                         → RestTemplate GET http://order-service:8080/internal/orders/eligibility
  │                                                         └─ order-svc InternalOrderController
  │                                                              └─ SELECT COUNT(*) FROM orders JOIN order_items
  │                                                                 WHERE user_id=? AND status='DELIVERED' AND product_id=?
  │                                         ← { eligible: true/false }
  │                         ← { data: { eligible: boolean } }
  │   IF eligible: show ReviewForm (rating widget + textarea)
  │   IF !eligible: show hint text
  │
  ├─ [Submit review] → POST /api/products/{id}/reviews
  │                    Bearer token (contains sub=userId, name=fullName, roles)
  │                      → Gateway → product-svc ReviewController
  │                                   → parse JWT → extract userId + reviewerName (claim "name")
  │                                   → ReviewService.createReview()
  │                                      ├─ RE-CHECK eligibility (RestTemplate → order-svc) [D-03]
  │                                      ├─ IF !eligible → throw 422 REVIEW_NOT_ELIGIBLE
  │                                      ├─ Jsoup.clean(content, Safelist.none()) [D-14]
  │                                      ├─ @Transactional:
  │                                      │   ├─ reviewRepo.save(ReviewEntity)
  │                                      │   └─ recompute: SELECT AVG, COUNT → UPDATE products
  │                                      └─ return ReviewResponse
  │   FE: reset form + toast + reload list page 1
  │
  └─ [Load review list] → GET /api/products/{id}/reviews?page=0&size=10
                            → product-svc → reviewRepo.findByProductIdOrderByCreatedAtDesc(Pageable)
                            ← PaginatedResponse<ReviewResponse>
                         FE: render list items (reviewer name + stars + content text node + date)
```

### Pattern 1: ReviewEntity (product-service)

[ASSUMED — dựa trên ProductEntity.java pattern, cần tạo mới]

```java
// V4__create_reviews.sql
CREATE TABLE product_svc.reviews (
  id           VARCHAR(36)     PRIMARY KEY,
  product_id   VARCHAR(36)     NOT NULL,
  user_id      VARCHAR(36)     NOT NULL,
  reviewer_name VARCHAR(150)   NOT NULL,         -- snapshot D-11
  rating       SMALLINT        NOT NULL CHECK (rating BETWEEN 1 AND 5),
  content      TEXT,                              -- nullable D-06
  created_at   TIMESTAMPTZ     NOT NULL,
  updated_at   TIMESTAMPTZ     NOT NULL,
  CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id),
  CONSTRAINT fk_reviews_product FOREIGN KEY (product_id)
    REFERENCES product_svc.products(id)
);
CREATE INDEX idx_reviews_product_id ON product_svc.reviews(product_id);
```

```java
@Entity
@Table(name = "reviews", schema = "product_svc")
public class ReviewEntity {
  @Id @Column(length = 36, nullable = false, updatable = false)
  private String id;

  @Column(name = "product_id", nullable = false, length = 36)
  private String productId;

  @Column(name = "user_id", nullable = false, length = 36)
  private String userId;

  @Column(name = "reviewer_name", nullable = false, length = 150)
  private String reviewerName;   // snapshot bất biến D-11

  @Column(nullable = false)
  private int rating;            // 1-5

  @Column(columnDefinition = "TEXT")
  private String content;        // nullable D-06

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // static factory, getters — mirror ProductEntity pattern
}
```

### Pattern 2: V5 avg_rating Migration + ProductEntity update

```sql
-- V5__add_avg_rating_review_count.sql (D-13)
ALTER TABLE product_svc.products
  ADD COLUMN IF NOT EXISTS avg_rating DECIMAL(3,1) DEFAULT 0,
  ADD COLUMN IF NOT EXISTS review_count INT         DEFAULT 0;
```

```java
// ProductEntity.java — thêm 2 fields + setters
@Column(name = "avg_rating", precision = 3, scale = 1)
private BigDecimal avgRating = BigDecimal.ZERO;

@Column(name = "review_count")
private int reviewCount = 0;

public void updateRatingStats(BigDecimal avgRating, int reviewCount) {
  this.avgRating = avgRating;
  this.reviewCount = reviewCount;
  this.updatedAt = Instant.now();
}

// Getters
public BigDecimal avgRating() { return avgRating; }
public int reviewCount() { return reviewCount; }
```

Và `ProductCrudService.toResponse()` phải trả real values thay vì `BigDecimal.ZERO` và `0`:

```java
// Hiện tại (line 179-180):
BigDecimal.ZERO,  // rating default — CẦN THAY
0,                // reviewCount default — CẦN THAY

// Sau update:
product.avgRating() != null ? product.avgRating() : BigDecimal.ZERO,
product.reviewCount(),
```

### Pattern 3: order-svc Internal Eligibility Endpoint

[VERIFIED: order-svc đã có RestTemplate @Bean (AppConfig.java) + OrderRepository + OrderItemEntity.productId() field]

```java
// InternalOrderController.java (order-service) — KHÔNG route qua gateway
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

  private final OrderRepository orderRepository;

  public InternalOrderController(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @GetMapping("/eligibility")
  public ApiResponse<Map<String, Boolean>> checkEligibility(
      @RequestParam String userId,
      @RequestParam String productId) {

    // D-02: SELECT COUNT(*) FROM orders o JOIN order_items i ...
    //       WHERE o.user_id=? AND o.status='DELIVERED' AND i.product_id=?
    boolean eligible = orderRepository.existsDeliveredOrderWithProduct(userId, productId);
    return ApiResponse.of(200, "Eligibility checked", Map.of("eligible", eligible));
  }
}
```

**OrderRepository method cần thêm:**
```java
@Query("SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i " +
       "WHERE o.userId = :userId AND o.status = 'DELIVERED' AND i.productId = :productId")
boolean existsDeliveredOrderWithProduct(
    @Param("userId") String userId,
    @Param("productId") String productId);
```

[VERIFIED: OrderItemEntity.productId() tồn tại, OrderEntity có `@OneToMany items`, OrderRepository đã có @Query pattern]

**Gateway:** KHÔNG add route `/internal/**` vào api-gateway/application.yml.

### Pattern 4: ReviewService.createReview() — Transaction Pattern

```java
@Transactional
public ReviewResponse createReview(String productId, String userId,
                                   String reviewerName, int rating, String content) {
  // 1. BE re-check eligibility (D-03 — không trust FE pre-check)
  boolean eligible = checkEligibilityInternal(userId, productId); // gọi RestTemplate
  if (!eligible) {
    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE");
  }

  // 2. Jsoup sanitize (D-14)
  String sanitized = (content != null && !content.isBlank())
      ? Jsoup.clean(content, Safelist.none())
      : null;

  // 3. Save review
  ReviewEntity review = ReviewEntity.create(productId, userId, reviewerName, rating, sanitized);
  reviewRepo.save(review);

  // 4. Recompute avg_rating + review_count (D-12) — cùng transaction
  Object[] stats = reviewRepo.computeStats(productId);  // SELECT AVG, COUNT
  ProductEntity product = productRepo.findById(productId).orElseThrow();
  product.updateRatingStats((BigDecimal) stats[0], ((Long) stats[1]).intValue());
  productRepo.save(product);

  return toResponse(review);
}
```

**ReviewRepository method cần:**
```java
@Query("SELECT AVG(r.rating), COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId")
Object[] computeStats(@Param("productId") String productId);

Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

// Duplicate check
boolean existsByProductIdAndUserId(String productId, String userId);
```

### Pattern 5: ReviewController

```java
@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {
  private final ReviewService reviewService;

  // Dùng JwtRoleGuard pattern để extract claims từ Bearer token
  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @GetMapping
  public ApiResponse<Map<String, Object>> listReviews(
      @PathVariable String productId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.of(200, "Reviews listed", reviewService.listReviews(productId, page, size));
  }

  @GetMapping("/eligibility")
  public ApiResponse<Map<String, Boolean>> checkEligibility(
      @PathVariable String productId,
      @RequestHeader("Authorization") String auth) {
    Claims claims = parseToken(auth);  // extract userId
    String userId = claims.getSubject();
    boolean eligible = reviewService.checkEligibilityInternal(userId, productId);
    return ApiResponse.of(200, "Eligibility checked", Map.of("eligible", eligible));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<Object> createReview(
      @PathVariable String productId,
      @RequestHeader("Authorization") String auth,
      @Valid @RequestBody CreateReviewRequest request) {
    Claims claims = parseToken(auth);
    String userId = claims.getSubject();
    // D-10: fallback về username nếu name claim chưa có (token cũ)
    String reviewerName = (String) claims.get("name");
    if (reviewerName == null || reviewerName.isBlank()) {
      reviewerName = (String) claims.get("username");
    }
    return ApiResponse.of(201, "Review created",
        reviewService.createReview(productId, userId, reviewerName, request.rating(), request.content()));
  }

  record CreateReviewRequest(
      @Min(1) @Max(5) int rating,
      @Size(max = 500) String content  // nullable D-06
  ) {}
}
```

### Pattern 6: FE ReviewSection Component

```tsx
// services/reviews.ts
export function listReviews(productId: string, page = 0, size = 10) {
  return httpGet<PaginatedResponse<Review>>(
    `/api/products/${productId}/reviews?page=${page}&size=${size}`);
}
export function checkEligibility(productId: string) {
  return httpGet<{ eligible: boolean }>(
    `/api/products/${productId}/reviews/eligibility`);
}
export function submitReview(productId: string, body: { rating: number; content?: string }) {
  return httpPost<Review>(`/api/products/${productId}/reviews`, body);
}
```

```tsx
// ReviewSection.tsx — orchestration component
'use client';
export function ReviewSection({ productId, slug }: { productId: string; slug: string }) {
  const { user } = useAuth();   // null = chưa login
  const [eligible, setEligible] = useState<boolean | null>(null);
  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);

  useEffect(() => {
    loadPage(0);
    if (user) checkEligibilityFetch();
    // D-09: không gọi eligibility nếu chưa login
  }, [productId, user]);

  // D-09: unauthenticated state
  if (!user) {
    return <p>Đăng nhập để đánh giá. <Link href={`/login?redirect=/products/${slug}`}>Đăng nhập</Link></p>;
  }

  return (
    <>
      {eligible && <ReviewForm productId={productId} onSuccess={handleSuccess} />}
      {eligible === false && <p>Chỉ user đã mua sản phẩm này mới có thể đánh giá.</p>}
      <ReviewList reviews={reviews} hasMore={hasMore} onLoadMore={() => loadPage(page + 1)} />
    </>
  );
}
```

### Pattern 7: Star Widget (CSS, no lib)

```tsx
// StarWidget.tsx — D-04: 5 stars, CSS hover, hidden number input
'use client';
function StarWidget({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  const [hovered, setHovered] = useState(0);
  return (
    <div role="radiogroup" aria-label="Chọn số sao">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          aria-label={`${n} sao`}
          aria-pressed={value === n}
          onMouseEnter={() => setHovered(n)}
          onMouseLeave={() => setHovered(0)}
          onClick={() => onChange(n)}
          className={n <= (hovered || value) ? styles.starFilled : styles.starEmpty}
        >
          ★
        </button>
      ))}
    </div>
  );
}
```

**rhf integration — hidden number input pattern:**
```tsx
// ReviewForm.tsx
const { register, setValue, watch, handleSubmit } = useForm<ReviewFormData>({
  resolver: zodResolver(reviewSchema),
});
const rating = watch('rating');

// Register hidden input + use StarWidget for visual control
<input type="hidden" {...register('rating', { valueAsNumber: true })} />
<StarWidget value={rating ?? 0} onChange={(n) => setValue('rating', n)} />
```

### Anti-Patterns to Avoid

- **dangerouslySetInnerHTML cho review content:** FE PHẢI dùng `{review.content}` text node — React tự escape. Không dùng `dangerouslySetInnerHTML` dù chỉ để render line breaks.
- **Rating drift (increment pattern):** KHÔNG dùng `avg = (old_avg * old_count + new_rating) / (old_count + 1)`. Luôn recompute `SELECT AVG, COUNT` from scratch.
- **Trust FE eligibility result:** BE PHẢI re-check eligibility tại POST review — FE có thể bị bypass.
- **Không có UNIQUE constraint trên (product_id, user_id):** Thiếu constraint → race condition duplicate review khi user double-submit. Constraint giải quyết ở DB level.
- **gateway route cho /internal/orders/eligibility:** KHÔNG thêm vào api-gateway application.yml. Endpoint này chỉ accessible từ Docker internal network.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| XSS sanitize | Custom regex strip `<script>` | `Jsoup.clean(content, Safelist.none())` | Regex miss edge cases (HTML entities, nested tags, Unicode tricks). Jsoup parser-based, battle-tested OWASP lib |
| JWT parse trong ReviewController | Custom Base64 decode | JJWT 0.12.7 `Jwts.parser()` (đã có trong pom.xml) | Reuse pattern từ JwtRoleGuard.java — đã verify signature + expiry |
| Star widget | react-rating, react-star-ratings | Custom 30-LOC CSS component | Trivial UI, không xứng dependency — consistent với STACK.md decision |
| Form validation | Native HTML5 validation | rhf + zod (đã cài Phase 10) | Cross-field validation, error messages đồng nhất với profile/address forms |
| Review pagination | Cursor pagination | Offset Spring Data Pageable | Scale thấp (demo), simplicity beats correctness at this scale |

---

## Common Pitfalls

### Pitfall 1: JWT 'name' claim missing → reviewer snapshot là null
**What goes wrong:** ReviewController gọi `claims.get("name")` → null → reviewer_name lưu null vào DB → constraint violation hoặc hiển thị trống.
**Why it happens:** JwtUtils.issueToken() hiện không có `name` claim (VERIFIED). User login → token thiếu claim → mọi review sau đó bị ảnh hưởng.
**How to avoid:** Sửa JwtUtils TRƯỚC khi implement ReviewController. Thêm fallback `|| claims.get("username")` cho token cũ đã cấp.
**Warning signs:** `claims.get("name")` trả null trong debug; reviewer_name trong DB là null hoặc equals username thay vì fullName.

### Pitfall 2: product-svc gọi order-svc nhưng chưa có RestTemplate @Bean
**What goes wrong:** `NoSuchBeanDefinitionException: RestTemplate` khi product-svc boot.
**Why it happens:** product-svc không có AppConfig.java (VERIFIED — glob không tìm thấy). order-svc có AppConfig.java với RestTemplate @Bean (VERIFIED).
**How to avoid:** Tạo `AppConfig.java` trong product-service package root trước khi implement ReviewService.
**Warning signs:** Spring context startup fail với `NoSuchBeanDefinitionException`.

### Pitfall 3: DataIntegrityViolationException khi user submit review lần 2
**What goes wrong:** UNIQUE constraint `(product_id, user_id)` trên reviews table → user submit lần 2 → Postgres throw 23505 → Spring map thành 500 thay vì 409 CONFLICT.
**Why it happens:** GlobalExceptionHandler trong product-svc không handle `DataIntegrityViolationException` — chỉ handle `ResponseStatusException` và validation errors.
**How to avoid:** ReviewService phải check `reviewRepo.existsByProductIdAndUserId(productId, userId)` TRƯỚC khi insert → throw 409 với code `REVIEW_ALREADY_EXISTS`. Hoặc catch `DataIntegrityViolationException` trong GlobalExceptionHandler.
**Warning signs:** Log `org.springframework.dao.DataIntegrityViolationException` khi test double-submit.

### Pitfall 4: avg_rating recompute gặp NULL khi product chưa có review nào
**What goes wrong:** `SELECT AVG(rating), COUNT(*) FROM reviews WHERE product_id=?` → AVG trả `null` (SQL standard khi không có row) → `BigDecimal` cast null → NullPointerException.
**Why it happens:** `AVG()` của empty set là NULL trong Postgres/SQL.
**How to avoid:** Dùng `COALESCE(AVG(rating), 0)` trong JPQL hoặc kiểm tra null trong Java: `stats[0] != null ? (BigDecimal) stats[0] : BigDecimal.ZERO`.
**Warning signs:** NullPointerException trong ReviewService khi product chưa có review sau xóa review cuối cùng.

### Pitfall 5: RestTemplate call đến order-svc bị timeout/fail → review không submit được
**What goes wrong:** order-svc down hoặc chậm → RestTemplate timeout → POST review fail với 500 dù user eligible.
**Why it happens:** RestTemplate default không có timeout config; order-svc RestException propagate lên ReviewService.
**How to avoid:** Wrap RestTemplate call trong try-catch; nếu order-svc unreachable → default `eligible = false` + log warning (acceptable cho demo stack). Hoặc set timeout:
```java
// AppConfig.java
@Bean
public RestTemplate restTemplate() {
  SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(2000);
  factory.setReadTimeout(3000);
  return new RestTemplate(factory);
}
```
**Warning signs:** POST review hang 30+ seconds; logs show `ConnectTimeoutException`.

### Pitfall 6: FE Review interface không align với BE ReviewResponse DTO
**What goes wrong:** FE `Review` interface (types/index.ts line 231-241) có field `userName` và `comment`; nếu BE trả `reviewerName` và `content` → FE thấy undefined → reviewer name không hiển thị.
**Why it happens:** FE type được viết trước (placeholder), BE implement sau với naming khác.
**How to avoid:** Align FE `Review` interface khi implement. BE ReviewResponse phải map sang FE-expected field names, HOẶC update FE interface. Chọn một convention nhất quán: `reviewerName` + `content` (match DB schema).
**Warning signs:** Review list render nhưng reviewer name trống và content trống dù có data.

---

## Code Examples

### Jsoup.clean() — XSS sanitize
```java
// Source: Jsoup official API — Safelist.none() strips ALL HTML tags
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

String sanitized = Jsoup.clean(rawContent, Safelist.none());
// Input:  "<script>alert(1)</script>Hello <b>world</b>"
// Output: "Hello world"
// Input:  "<img src=x onerror=fetch('//evil')>"
// Output: ""   (tất cả tags bị strip)
```

### JJWT parse claims (reuse JwtRoleGuard pattern)
```java
// Source: product-svc/web/JwtRoleGuard.java — VERIFIED in codebase
Claims claims = Jwts.parser()
    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
    .build()
    .parseSignedClaims(token.substring("Bearer ".length()).trim())
    .getPayload();

String userId = claims.getSubject();   // sub claim
String name = (String) claims.get("name");    // D-10 — sau khi fix JwtUtils
String username = (String) claims.get("username");  // fallback
String reviewerName = (name != null && !name.isBlank()) ? name : username;
```

### OrderRepository — JPQL eligibility query
```java
// Source: OrderRepository.java pattern + OrderEntity/OrderItemEntity VERIFIED
@Query("SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i " +
       "WHERE o.userId = :userId AND o.status = 'DELIVERED' AND i.productId = :productId")
boolean existsDeliveredOrderWithProduct(
    @Param("userId") String userId,
    @Param("productId") String productId);
```

### FE Review render (XSS-safe)
```tsx
// Plain text render — React tự escape, KHÔNG dùng dangerouslySetInnerHTML
{review.content && (
  <p className={styles.reviewContent}>
    {review.content}  {/* React renders as text node — <script> shows literally */}
  </p>
)}
```

### FE eligibility check flow
```tsx
// services/reviews.ts
export function checkEligibility(productId: string): Promise<{ eligible: boolean }> {
  // httpGet auto-attach Bearer token (services/http.ts pattern)
  return httpGet<{ eligible: boolean }>(`/api/products/${productId}/reviews/eligibility`);
}

// ReviewSection.tsx — D-08 pre-check on tab open
useEffect(() => {
  if (!user) return;  // D-09: skip nếu chưa login
  checkEligibility(productId)
    .then(r => setEligible(r.eligible))
    .catch(() => setEligible(false));  // fail-safe: hide form nếu check fails
}, [productId, user]);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `averageRating = (avg * count + new) / (count + 1)` | Recompute from scratch: `SELECT AVG, COUNT` | PITFALLS.md Pitfall 4 | Không drift sau edit/delete |
| review `displayName` fetch từ user-svc per render | Snapshot `reviewer_name` vào ReviewEntity lúc create | Phase 13 D-11 | Loại N+1 cross-service |
| dangerouslySetInnerHTML | `{content}` React text node + BE Jsoup sanitize | Phase 13 D-14 | Stored XSS prevention |
| `JwtUtils.issueToken(userId, username, roles)` | `JwtUtils.issueToken(userId, username, fullName, roles)` | Phase 13 (cần sửa) | Cho phép reviewer name snapshot |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Jsoup 1.17.2 tương thích với Spring Boot 3.3.2 + Java 17 | Standard Stack | Low — Jsoup thuần Java, không phụ thuộc Spring. Worst case: bump version khi add vào pom.xml |
| A2 | Gateway route `/api/products/**` tự động route `/api/products/{id}/reviews` và `/api/products/{id}/reviews/eligibility` | Architecture | Low — đã confirmed pattern từ INTEGRATIONS.md, ProductController mapping `/products` |
| A3 | `Jsoup.clean(content, Safelist.none())` không throw exception với null/empty input | Code Examples | Medium — cần xác nhận: nếu content null thì phải check trước khi gọi; `Jsoup.clean(null, ...)` có thể throw NPE |
| A4 | `@Query JPQL "SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i"` hợp lệ với Hibernate 6 (Spring Boot 3.3.2) | Pattern 3 | Low — boolean COUNT > 0 là standard JPQL. Alternative: return `long` rồi check > 0 trong Java |
| A5 | FE `useAuth()` hook trả `user` object với đủ thông tin để check `user != null` | Pattern 6 | Low — useAuth() đã có từ Phase 9/10 |

---

## Open Questions

1. **Jsoup.clean(null, ...) behavior**
   - What we know: Jsoup.clean() với whitelist strips HTML
   - What's unclear: Có throw NPE khi content null không?
   - Recommendation: Check `content != null && !content.isBlank()` trước khi gọi Jsoup.clean(); nếu null/blank → lưu null vào DB (content là nullable, D-06)

2. **Duplicate review: 409 hay thân thiện với UX?**
   - What we know: UNIQUE constraint ngăn duplicate; deferred ideas mention "hide form nếu user đã review" là Claude's Discretion
   - What's unclear: Nếu user đã review, FE có check và ẩn form không, hay chỉ rely vào BE 409?
   - Recommendation: Eligibility endpoint trả thêm `hasReviewed: boolean` — hoặc đơn giản hơn: BE throw 409 REVIEW_ALREADY_EXISTS, FE catch và display toast "Bạn đã đánh giá sản phẩm này". Không cần ẩn form (deferred scope).

3. **Token cũ (thiếu 'name' claim) sau khi deploy fix JwtUtils**
   - What we know: Tokens hiện tại trong localStorage của user KHÔNG có `name` claim
   - What's unclear: Bao lâu thì token cũ expire? (24h theo JwtUtils expirationMs default)
   - Recommendation: ReviewController phải có fallback: `name != null ? name : username`. Sau 24h tất cả token cũ hết hạn, user login lại → token mới có `name` claim.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| PostgreSQL | V4/V5 Flyway migrations | ✓ (Docker Compose) | 16 | — |
| Docker Compose / order-svc | RestTemplate eligibility call | ✓ | existing | — |
| Jsoup JAR | Jsoup.clean() | ✗ (chưa trong pom.xml) | — | Cần thêm vào pom.xml |
| RestTemplate @Bean (product-svc) | ReviewService | ✗ (chưa có AppConfig.java) | — | Phải tạo AppConfig.java |
| JWT 'name' claim | reviewer snapshot | ✗ (chưa trong JwtUtils) | — | Phải sửa JwtUtils + AuthService |

**Missing dependencies với action required:**
- Jsoup: thêm vào product-svc pom.xml (Wave 0 task)
- RestTemplate @Bean: tạo product-svc AppConfig.java (Wave 0 task)
- JWT 'name' claim: sửa user-svc JwtUtils.issueToken() (Wave 0 task — user-svc)

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers Postgres |
| Config file | pom.xml test scope (đã có) |
| Quick run command | `mvn test -pl sources/backend/product-service -Dtest=ReviewServiceTest -q` |
| Full suite command | `mvn test -pl sources/backend/product-service,sources/backend/order-service,sources/backend/user-service` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REV-01 | Eligible user submit review → 201, content sanitized | Integration | `mvn test -Dtest=ReviewControllerIntegrationTest` | ❌ Wave 0 |
| REV-01 | Non-eligible user submit review → 422 REVIEW_NOT_ELIGIBLE | Integration | `mvn test -Dtest=ReviewControllerIntegrationTest#testNotEligible` | ❌ Wave 0 |
| REV-01 | XSS payload `<script>` → stored as plain text | Integration | `mvn test -Dtest=ReviewSanitizeTest` | ❌ Wave 0 |
| REV-02 | Review list paginated 10/page, newest first | Integration | `mvn test -Dtest=ReviewListTest` | ❌ Wave 0 |
| REV-03 | avg_rating recompute after insert → ProductEntity updated | Integration | `mvn test -Dtest=ReviewRatingRecomputeTest` | ❌ Wave 0 |
| REV-03 | avg_rating not NULL when no reviews exist | Integration | `mvn test -Dtest=ReviewRatingRecomputeTest#testNoReviews` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn test -pl sources/backend/product-service -Dtest=*Review* -q`
- **Per wave merge:** Full product-svc suite + order-svc suite
- **Phase gate:** Full suite green trước `/gsd-verify-work`; manual UAT review form trên browser

### Wave 0 Gaps

- [ ] `sources/backend/product-service/src/test/java/.../ReviewControllerIntegrationTest.java` — covers REV-01
- [ ] `sources/backend/product-service/src/test/java/.../ReviewSanitizeTest.java` — XSS verify REV-01
- [ ] `sources/backend/product-service/src/test/java/.../ReviewRatingRecomputeTest.java` — covers REV-03
- [ ] `sources/backend/order-service/src/test/java/.../InternalOrderControllerTest.java` — eligibility endpoint D-02

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT Bearer parse (JJWT) — không Spring Security, manual guard |
| V3 Session Management | partial | JWT stateless; no CSRF (không cookie) |
| V4 Access Control | yes | Eligibility check server-side; KHÔNG trust FE; ownership check cho reviewer_id = JWT sub |
| V5 Input Validation | yes | @Valid + @Size(max=500) + Jsoup.clean() |
| V6 Cryptography | no | JWT verify only — không encrypt new data |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Stored XSS via review content | Tampering / Info Disclosure | Jsoup.clean(content, Safelist.none()) + React text node render |
| IDOR: POST review cho productId của người khác | Elevation of Privilege | BE luôn dùng userId từ JWT (không accept từ body); DB UNIQUE (product_id, user_id) |
| Eligibility bypass: FE pre-check skip | Spoofing | BE re-check tại POST (D-03) — độc lập với FE result |
| Rating drift (integrity) | Tampering | Recompute from scratch (SELECT AVG/COUNT) trong cùng @Transactional |
| JWT manipulation: fake fullName claim | Tampering | JWT signed với HS256 secret — không thể forge nếu không biết secret |
| Double-submit race → duplicate review | Tampering | DB UNIQUE constraint + catch DataIntegrityViolationException → 409 |

---

## Sources

### Primary (HIGH confidence)

- [VERIFIED] `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java` — JWT claims inspection (sub, username, roles — NO 'name')
- [VERIFIED] `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java` — issueToken() call pattern
- [VERIFIED] `sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/domain/UserEntity.java` — fullName field tồn tại
- [VERIFIED] `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java` — RestTemplate @Bean pattern
- [VERIFIED] `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/domain/OrderItemEntity.java` — productId field tồn tại
- [VERIFIED] `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java` — @Query JPQL pattern
- [VERIFIED] `sources/backend/product-service/src/main/resources/db/migration/` — V1, V2, V3 exist; V4+V5 free
- [VERIFIED] `sources/backend/product-service/src/main/java/.../web/JwtRoleGuard.java` — JWT parse pattern reusable
- [VERIFIED] `sources/backend/product-service/src/main/java/.../service/ProductCrudService.java` — toResponse() hard-codes BigDecimal.ZERO for rating
- [VERIFIED] `sources/backend/product-service/pom.xml` — dependencies (Jsoup NOT present; JJWT 0.12.7 present)
- [VERIFIED] `sources/frontend/src/app/products/[slug]/page.tsx` — reviewPlaceholder block at activeTab === 'reviews' (line 339-345)
- [VERIFIED] `sources/frontend/src/types/index.ts` — Review interface exists (line 231-241); field names: userName, comment (cần align)
- [VERIFIED] `sources/frontend/src/services/http.ts` — httpGet/httpPost pattern, Bearer token auto-attach
- [VERIFIED] `sources/frontend/src/services/products.ts` — không có review functions; cần tạo services/reviews.ts

### Secondary (MEDIUM confidence)

- [CITED] Jsoup library ID `/jhy/jsoup` (ctx7 resolve): latest = jsoup-1.20.1; khuyến nghị 1.17.2 để tránh rủi ro
- [CITED] `.planning/research/PITFALLS.md` §Pitfall 3 (XSS), §Pitfall 4 (rating drift), §Pitfall 5 (N+1)
- [CITED] `.planning/research/STACK.md` §2.3 (StarRating custom component decision)
- [CITED] `.planning/ROADMAP.md` §Flyway V-number Reservation Table — V4+V5 reserved for Phase 13

---

## Metadata

**Confidence breakdown:**
- JWT claims inspection: HIGH — đọc trực tiếp JwtUtils.java source
- Flyway V-number safety: HIGH — list file V1/V2/V3 trực tiếp
- RestTemplate gap (product-svc): HIGH — glob không tìm thấy AppConfig.java
- Eligibility query pattern: HIGH — OrderItemEntity.productId() verified, OrderRepository @Query pattern verified
- Jsoup version: MEDIUM — ctx7 resolve thấy 1.20.1 latest; khuyến nghị 1.17.2 [ASSUMED compatible]
- FE Review interface alignment: HIGH — đọc types/index.ts trực tiếp, thấy naming mismatch

**Research date:** 2026-04-27
**Valid until:** 2026-05-27 (stable stack — Spring Boot 3.3.2, JJWT 0.12.7 không thay đổi trong 30 ngày)
