# Phase 13: Reviews & Ratings - Pattern Map

**Mapped:** 2026-04-27
**Files analyzed:** 16 file mới / sửa đổi
**Analogs found:** 14 / 16

---

## File Classification

| File Mới / Sửa Đổi | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `sources/backend/product-service/src/main/resources/db/migration/V4__create_reviews.sql` | migration | batch | `V3__add_product_stock.sql` | exact |
| `sources/backend/product-service/src/main/resources/db/migration/V5__add_avg_rating_review_count.sql` | migration | batch | `V3__add_product_stock.sql` | exact |
| `sources/backend/product-service/.../domain/ReviewEntity.java` | model | CRUD | `ProductEntity.java` | exact |
| `sources/backend/product-service/.../repository/ReviewRepository.java` | repository | CRUD | `ProductRepository.java` + `OrderRepository.java` | exact |
| `sources/backend/product-service/.../service/ReviewService.java` | service | CRUD | `ProductCrudService.java` | exact |
| `sources/backend/product-service/.../web/ReviewController.java` | controller | request-response | `ProductController.java` + `JwtRoleGuard.java` | exact |
| `sources/backend/product-service/.../domain/ProductEntity.java` | model | CRUD | self (modify) | — |
| `sources/backend/product-service/.../service/ProductCrudService.java` | service | CRUD | self (modify) | — |
| `sources/backend/product-service/AppConfig.java` | config | request-response | `order-service/AppConfig.java` | exact |
| `sources/backend/order-service/.../web/InternalOrderController.java` | controller | request-response | `OrderController.java` | role-match |
| `sources/backend/order-service/.../repository/OrderRepository.java` | repository | CRUD | self (modify) + existing @Query patterns | exact |
| `sources/backend/user-service/.../jwt/JwtUtils.java` | utility | request-response | self (modify) | — |
| `sources/backend/user-service/.../service/AuthService.java` | service | request-response | self (modify) | — |
| `sources/frontend/src/services/reviews.ts` | service | request-response | `services/products.ts` + `services/orders.ts` | exact |
| `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewSection.tsx` | component | request-response | `app/products/[slug]/page.tsx` (pattern `useEffect` + `useAuth`) | role-match |
| `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewForm.tsx` | component | request-response | `components/ui/AddressForm/AddressForm.tsx` | exact |
| `sources/frontend/src/app/products/[slug]/ReviewSection/StarWidget.tsx` | component | event-driven | không có analog (custom) | no-analog |
| `sources/frontend/src/app/products/[slug]/ReviewSection/ReviewList.tsx` | component | request-response | `app/products/[slug]/page.tsx` (list render pattern) | partial |
| `sources/frontend/src/types/index.ts` | utility | — | self (modify) | — |

---

## Pattern Assignments

### `V4__create_reviews.sql` (migration, batch)

**Analog:** `sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql`

**Migration pattern** (lines 1-4):
```sql
-- Phase X / Plan Y: mô tả ngắn gọn mục đích migration.
-- IF NOT EXISTS đảm bảo idempotent khi chạy lại trong test environments.
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0;
UPDATE product_svc.products SET stock = 50 WHERE deleted = false;
```

**V4 reviews table** (dựa trên pattern V1 schema + V3 column add):
```sql
-- Tham khảo V1__init_schema.sql: TIMESTAMP WITH TIME ZONE, VARCHAR(36) PK, CONSTRAINT naming convention
-- Tham khảo V3: ADD COLUMN IF NOT EXISTS pattern cho idempotency
CREATE TABLE product_svc.reviews (
  id            VARCHAR(36)   PRIMARY KEY,
  product_id    VARCHAR(36)   NOT NULL,
  user_id       VARCHAR(36)   NOT NULL,
  reviewer_name VARCHAR(150)  NOT NULL,
  rating        SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
  content       TEXT,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT uq_review_product_user UNIQUE (product_id, user_id),
  CONSTRAINT fk_reviews_product FOREIGN KEY (product_id)
    REFERENCES product_svc.products(id)
);
CREATE INDEX idx_reviews_product_id ON product_svc.reviews(product_id);
```

**Naming conventions từ V1** (lines 4-29 của V1__init_schema.sql):
- Schema prefix: `product_svc.`
- PK: `VARCHAR(36) PRIMARY KEY`
- UNIQUE constraint: `CONSTRAINT uq_{table}_{field}` 
- FK constraint: `CONSTRAINT fk_{table}_{ref}`
- Index: `CREATE INDEX idx_{table}_{col} ON product_svc.{table}({col})`

---

### `V5__add_avg_rating_review_count.sql` (migration, batch)

**Analog:** `sources/backend/product-service/src/main/resources/db/migration/V3__add_product_stock.sql`

**Pattern** (lines 1-3 của V3):
```sql
-- Phase 8 / Plan 01 (D-01): Thêm column stock vào product_svc.products.
-- IF NOT EXISTS đảm bảo idempotent khi chạy lại trong test environments.
ALTER TABLE product_svc.products ADD COLUMN IF NOT EXISTS stock INT NOT NULL DEFAULT 0;
```

**V5 pattern:**
```sql
-- Phase 13 (D-13): Thêm avg_rating + review_count vào product_svc.products.
ALTER TABLE product_svc.products
  ADD COLUMN IF NOT EXISTS avg_rating DECIMAL(3,1) DEFAULT 0,
  ADD COLUMN IF NOT EXISTS review_count INT         DEFAULT 0;
```

---

### `ReviewEntity.java` (model, CRUD)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java`

**Imports pattern** (lines 1-13):
```java
package com.ptit.htpt.productservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
```

**Class declaration + annotations pattern** (lines 24-28):
```java
@Entity
@Table(name = "products", schema = "product_svc")
@SQLRestriction("deleted = false")   // ReviewEntity KHÔNG cần soft-delete (REV-04 deferred)
@SQLDelete(...)
public class ProductEntity {
```

**ID + Column pattern** (lines 30-72):
```java
@Id
@Column(length = 36, nullable = false, updatable = false)
private String id;

@Column(name = "product_id", nullable = false, length = 36)
private String productId;     // FK reference by ID string (không dùng @ManyToOne object join)

@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;

@Column(name = "updated_at", nullable = false)
private Instant updatedAt;
```

**Static factory pattern** (lines 90-102):
```java
public static ProductEntity create(String name, ...) {
  Instant now = Instant.now();
  ProductEntity entity = new ProductEntity(UUID.randomUUID().toString(), ...);
  entity.brand = brand;       // optional fields set after constructor
  return entity;
}
```

**Accessor naming: record-style getters** (lines 135-148):
```java
public String id() { return id; }
public String productId() { return productId; }
// NOT getXxx() — dùng field name trực tiếp
```

**equals/hashCode pattern** (lines 152-162 — chỉ dùng id):
```java
@Override
public boolean equals(Object o) {
  if (this == o) return true;
  if (!(o instanceof ProductEntity that)) return false;
  return Objects.equals(id, that.id);
}
@Override
public int hashCode() { return Objects.hash(id); }
```

**ReviewEntity sẽ copy toàn bộ pattern trên, bỏ `@SQLRestriction`/`@SQLDelete` (deferred), thêm:**
```java
@Column(name = "reviewer_name", nullable = false, length = 150)
private String reviewerName;   // D-11: snapshot bất biến, updatable = false ngầm định

@Column(nullable = false)
private int rating;            // 1-5

@Column(columnDefinition = "TEXT")
private String content;        // nullable — D-06: optional
```

---

### `ReviewRepository.java` (repository, CRUD)

**Analog 1:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/repository/ProductRepository.java`

**Base pattern** (toàn bộ file — 9 lines):
```java
package com.ptit.htpt.productservice.repository;

import com.ptit.htpt.productservice.domain.ProductEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {
  Optional<ProductEntity> findBySlug(String slug);
}
```

**Analog 2 — @Query pattern:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java`

**@Query + @Param pattern** (lines 20-33):
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId " +
       "AND (:status IS NULL OR o.status = :status) ...")
List<OrderEntity> findByUserIdWithFilters(
    @Param("userId") String userId,
    @Param("status") String status,
    ...
);
```

**ReviewRepository cần:**
```java
// Derived query — Spring Data tự generate SQL
Page<ReviewEntity> findByProductIdOrderByCreatedAtDesc(String productId, Pageable pageable);

boolean existsByProductIdAndUserId(String productId, String userId);

// Custom JPQL — AVG + COUNT trong 1 query
@Query("SELECT AVG(r.rating), COUNT(r) FROM ReviewEntity r WHERE r.productId = :productId")
Object[] computeStats(@Param("productId") String productId);

// Eligibility query — boolean COUNT > 0 pattern từ RESEARCH Pattern 3
@Query("SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i " +
       "WHERE o.userId = :userId AND o.status = 'DELIVERED' AND i.productId = :productId")
boolean existsDeliveredOrderWithProduct(...);  // (dùng cho OrderRepository, không ReviewRepository)
```

---

### `ReviewService.java` (service, CRUD)

**Analog:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/service/ProductCrudService.java`

**Imports + @Service pattern** (lines 1-28):
```java
package com.ptit.htpt.productservice.service;

import com.ptit.htpt.productservice.domain.ProductEntity;
import com.ptit.htpt.productservice.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductCrudService {
  private final ProductRepository productRepo;
  private final CategoryRepository categoryRepo;

  public ProductCrudService(ProductRepository productRepo, CategoryRepository categoryRepo) {
    this.productRepo = productRepo;
    this.categoryRepo = categoryRepo;
  }
```

**ResponseStatusException error pattern** (lines 55-59):
```java
ProductEntity product = productRepo.findById(id)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
// Pattern: throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE")
// Pattern: throw new ResponseStatusException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS")
```

**Pagination map pattern** (lines 211-228):
```java
// ProductCrudService.paginate() — ReviewService dùng Spring Data Pageable thay vì manual paginate
// nhưng response map format giống nhau:
Map<String, Object> result = new LinkedHashMap<>();
result.put("content", content);
result.put("totalElements", totalElements);
result.put("totalPages", totalPages);
result.put("currentPage", safePage);
result.put("pageSize", safeSize);
result.put("isFirst", safePage <= 0);
result.put("isLast", safePage >= Math.max(totalPages - 1, 0));
```

**toResponse() pattern** (lines 161-187 — ReviewService sẽ có tương tự):
```java
public ProductResponse toResponse(ProductEntity product) {
  // Map entity fields → response DTO fields
  // Null-safe: product.thumbnailUrl() != null ? product.thumbnailUrl() : ""
  return new ProductResponse(product.id(), product.name(), ...);
}
```

**ReviewService thêm RestTemplate injection:**
```java
// Copy từ order-svc AppConfig.java pattern — inject @Bean:
private final RestTemplate restTemplate;

// Constructor injection (không dùng @Autowired field injection)
public ReviewService(ReviewRepository reviewRepo, ProductRepository productRepo,
                     RestTemplate restTemplate, ...) { ... }
```

---

### `ReviewController.java` (controller, request-response)

**Analog 1:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/ProductController.java`

**Imports + @RestController pattern** (lines 1-28):
```java
package com.ptit.htpt.productservice.web;

import com.ptit.htpt.productservice.api.ApiResponse;
import com.ptit.htpt.productservice.service.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/{productId}/reviews")
public class ReviewController {
  private final ReviewService reviewService;

  public ReviewController(ReviewService reviewService) {
    this.reviewService = reviewService;
  }
```

**ApiResponse.of() response pattern** (lines 32-40):
```java
@GetMapping
public ApiResponse<Map<String, Object>> listProducts(...) {
  return ApiResponse.of(200, "Products listed",
      productCrudService.listProducts(page, size, sort, false, keyword));
}
```

**@ResponseStatus(HttpStatus.CREATED) pattern** (lines 54-58):
```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<Object> createProduct(@Valid @RequestBody ProductUpsertRequest request) {
  return ApiResponse.of(201, "Product created", productCrudService.createProduct(request));
}
```

**Analog 2 — JWT parse:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java`

**JWT parse + claims extract pattern** (lines 26-63):
```java
@Value("${app.jwt.secret}")
private String jwtSecret;

private SecretKey getSigningKey() {
  return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
}

// Trong method — @RequestHeader("Authorization") String auth:
if (auth == null || !auth.startsWith("Bearer ")) {
  throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
      "Missing or invalid Authorization header");
}
String token = auth.substring("Bearer ".length()).trim();
Claims claims;
try {
  claims = Jwts.parser()
      .verifyWith(getSigningKey())
      .build()
      .parseSignedClaims(token)
      .getPayload();
} catch (Exception e) {
  throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
}
String userId = claims.getSubject();                    // sub = userId
String name = (String) claims.get("name");              // D-10
String username = (String) claims.get("username");      // fallback
String reviewerName = (name != null && !name.isBlank()) ? name : username;
```

**record DTO pattern** (pattern từ RESEARCH — dùng Java record inner class):
```java
// Đặt bên trong ReviewController (pattern tương tự ProductCrudService inner records)
record CreateReviewRequest(
    @Min(1) @Max(5) int rating,
    @Size(max = 500) String content   // nullable — D-06
) {}
```

---

### `ProductEntity.java` — MODIFY (model, CRUD)

**Analog:** self (`sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/domain/ProductEntity.java`)

**Existing field pattern** (lines 41-71 — copy style):
```java
@Column(nullable = false, precision = 12, scale = 2)
private BigDecimal price;

@Column(nullable = false)
private int stock = 0;
```

**Thêm 2 fields (D-13) theo style hiện có:**
```java
@Column(name = "avg_rating", precision = 3, scale = 1)
private BigDecimal avgRating = BigDecimal.ZERO;

@Column(name = "review_count")
private int reviewCount = 0;
```

**Thêm setter + getters theo style record-accessor** (lines 119-148):
```java
public void setStock(int stock) {
  this.stock = Math.max(0, stock);
  this.updatedAt = Instant.now();
}
// → Tương tự:
public void updateRatingStats(BigDecimal avgRating, int reviewCount) {
  this.avgRating = avgRating != null ? avgRating : BigDecimal.ZERO;
  this.reviewCount = reviewCount;
  this.updatedAt = Instant.now();
}
public BigDecimal avgRating() { return avgRating; }
public int reviewCount() { return reviewCount; }
```

---

### `ProductCrudService.java` — MODIFY (service, CRUD)

**Analog:** self

**Đoạn cần thay thế** (lines 179-180):
```java
// TRƯỚC (hardcode defaults):
BigDecimal.ZERO,  // rating default
0,                // reviewCount default

// SAU (đọc từ entity):
product.avgRating() != null ? product.avgRating() : BigDecimal.ZERO,
product.reviewCount(),
```

---

### `AppConfig.java` (product-service) (config, request-response)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/AppConfig.java`

**Toàn bộ file** (lines 1-13 — copy exact, chỉ đổi package):
```java
package com.ptit.htpt.productservice;  // đổi package

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
```

**Note:** RESEARCH khuyến nghị thêm timeout config (Pitfall 5):
```java
@Bean
public RestTemplate restTemplate() {
  SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(2000);
  factory.setReadTimeout(3000);
  return new RestTemplate(factory);
}
```

---

### `InternalOrderController.java` (order-service) (controller, request-response)

**Analog:** `sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/web/OrderController.java`

**@RestController + @RequestMapping pattern** (lines 23-29):
```java
@RestController
@RequestMapping("/orders")
public class OrderController {
  private final OrderCrudService orderCrudService;

  public OrderController(OrderCrudService orderCrudService) {
    this.orderCrudService = orderCrudService;
  }
```

**ApiResponse.of() + @RequestParam pattern** (lines 31-51):
```java
@GetMapping
public ApiResponse<Map<String, Object>> listOrders(
    @RequestParam(defaultValue = "0") int page,
    ...
) {
  return ApiResponse.of(200, "Orders listed", orderCrudService.listOrders(...));
}
```

**InternalOrderController pattern:**
```java
package com.ptit.htpt.orderservice.web;

import com.ptit.htpt.orderservice.api.ApiResponse;
import com.ptit.htpt.orderservice.repository.OrderRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/orders")   // KHÔNG qua gateway — Docker internal only
public class InternalOrderController {
  private final OrderRepository orderRepository;

  public InternalOrderController(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
  }

  @GetMapping("/eligibility")
  public ApiResponse<Map<String, Boolean>> checkEligibility(
      @RequestParam String userId,
      @RequestParam String productId) {
    boolean eligible = orderRepository.existsDeliveredOrderWithProduct(userId, productId);
    return ApiResponse.of(200, "Eligibility checked", Map.of("eligible", eligible));
  }
}
```

---

### `OrderRepository.java` — MODIFY (order-service) (repository, CRUD)

**Analog:** self (`sources/backend/order-service/src/main/java/com/ptit/htpt/orderservice/repository/OrderRepository.java`)

**@Query + @Param pattern để thêm** (lines 20-33):
```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId " +
       "AND (:status IS NULL OR o.status = :status) ...")
List<OrderEntity> findByUserIdWithFilters(
    @Param("userId") String userId,
    ...
);
```

**Method mới theo cùng pattern:**
```java
@Query("SELECT COUNT(o) > 0 FROM OrderEntity o JOIN o.items i " +
       "WHERE o.userId = :userId AND o.status = 'DELIVERED' AND i.productId = :productId")
boolean existsDeliveredOrderWithProduct(
    @Param("userId") String userId,
    @Param("productId") String productId);
```

---

### `JwtUtils.java` — MODIFY (user-service) (utility, request-response)

**Analog:** self (`sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/jwt/JwtUtils.java`)

**Đoạn cần sửa** (lines 44-53):
```java
// TRƯỚC:
public String issueToken(String userId, String username, String roles) {
  return Jwts.builder()
      .subject(userId)
      .claim("username", username)
      .claim("roles", roles)
      .issuedAt(...)
      .expiration(...)
      .signWith(getSigningKey(), Jwts.SIG.HS256)
      .compact();
}

// SAU — thêm parameter fullName + claim "name":
public String issueToken(String userId, String username, String fullName, String roles) {
  return Jwts.builder()
      .subject(userId)
      .claim("username", username)
      .claim("name", fullName != null ? fullName : username)  // D-10: fallback về username
      .claim("roles", roles)
      .issuedAt(...)
      .expiration(...)
      .signWith(getSigningKey(), Jwts.SIG.HS256)
      .compact();
}
```

---

### `AuthService.java` — MODIFY (user-service) (service, request-response)

**Analog:** self (`sources/backend/user-service/src/main/java/com/ptit/htpt/userservice/service/AuthService.java`)

**Đoạn cần sửa** (lines 52 và 69):
```java
// TRƯỚC (cả register + login):
String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.roles());

// SAU — truyền thêm fullName:
String token = jwtUtils.issueToken(entity.id(), entity.username(), entity.fullName(), entity.roles());
```

**Lưu ý:** `UserEntity.fullName()` đã tồn tại (RESEARCH VERIFIED — line 46 của UserEntity.java).

---

### `services/reviews.ts` (FE service, request-response)

**Analog:** `sources/frontend/src/services/products.ts`

**Imports + file structure pattern** (lines 1-24 của products.ts):
```typescript
/**
 * JSDoc header — mô tả API, gateway paths, pitfalls note
 */
import type { Product, PaginatedResponse } from '@/types';
import { httpGet, httpPost } from './http';
```

**Function pattern** (lines 36-44):
```typescript
export function listProducts(params?: ListProductsParams): Promise<PaginatedResponse<Product>> {
  const qs = new URLSearchParams();
  if (params?.page !== undefined) qs.set('page', String(params.page));
  const suffix = qs.toString() ? `?${qs}` : '';
  return httpGet<PaginatedResponse<Product>>(`/api/products${suffix}`);
}
```

**reviews.ts sẽ follow pattern:**
```typescript
import type { Review, PaginatedResponse } from '@/types';
import { httpGet, httpPost } from './http';

export function listReviews(productId: string, page = 0, size = 10): Promise<PaginatedResponse<Review>> {
  return httpGet<PaginatedResponse<Review>>(
    `/api/products/${productId}/reviews?page=${page}&size=${size}`
  );
}

export function checkEligibility(productId: string): Promise<{ eligible: boolean }> {
  return httpGet<{ eligible: boolean }>(
    `/api/products/${productId}/reviews/eligibility`
  );
}

export function submitReview(
  productId: string,
  body: { rating: number; content?: string }
): Promise<Review> {
  return httpPost<Review>(`/api/products/${productId}/reviews`, body);
}
```

---

### `ReviewSection.tsx` (FE component, request-response)

**Analog:** `sources/frontend/src/app/products/[slug]/page.tsx`

**'use client' + imports pattern** (lines 1-17):
```typescript
'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useToast } from '@/components/ui/Toast/Toast';
import { isApiError } from '@/services/errors';
```

**useAuth() pattern** (lines 19-32 của page.tsx + AuthProvider.tsx):
```typescript
// AuthProvider.tsx — useAuth() trả:
interface AuthState {
  isAuthenticated: boolean;
  user: { id: string; email: string; name: string } | null;
}
// Dùng: const { user } = useAuth(); — null = chưa login (D-09)
```

**useEffect + load pattern** (lines 35-55 của page.tsx):
```typescript
const load = useCallback(async () => {
  if (!slug) return;
  setLoading(true);
  setFailed(false);
  try {
    const p = await getProductBySlug(slug);
    setProduct(p);
  } catch {
    setFailed(true);
  } finally {
    setLoading(false);
  }
}, [slug]);

useEffect(() => { load(); }, [load]);
```

**RetrySection fallback pattern** (import từ `components/ui/RetrySection`):
```typescript
import RetrySection from '@/components/ui/RetrySection/RetrySection';
// Dùng khi fetch reviews fail: <RetrySection onRetry={load} />
```

---

### `ReviewForm.tsx` (FE component, request-response)

**Analog:** `sources/frontend/src/components/ui/AddressForm/AddressForm.tsx`

**rhf + zod pattern** (lines 1-21):
```typescript
'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Button from '@/components/ui/Button/Button';
import styles from './AddressForm.module.css';

const addressSchema = z.object({
  fullName: z.string().min(2, 'Tối thiểu 2 ký tự').max(100, 'Tối đa 100 ký tự'),
});
type AddressFormData = z.infer<typeof addressSchema>;
```

**useForm + zodResolver pattern** (lines 46-60):
```typescript
const {
  register,
  handleSubmit,
  formState: { errors },
} = useForm<AddressFormData>({
  resolver: zodResolver(addressSchema),
  defaultValues: {
    fullName: initialValues?.fullName ?? '',
  },
});
```

**onSubmit + Button loading pattern** (lines 62-134):
```typescript
const onFormSubmit = handleSubmit(async (data: AddressFormData) => {
  await onSubmit(data);
});

return (
  <form onSubmit={onFormSubmit} className={styles.form} noValidate>
    {/* fields */}
    <Button type="submit" variant="primary" loading={loading}>
      Lưu địa chỉ
    </Button>
  </form>
);
```

**ReviewForm thêm watch() cho StarWidget:**
```typescript
const { register, setValue, watch, handleSubmit, reset, formState: { errors } } = useForm({...});
const rating = watch('rating');

// Hidden number input cho rhf + StarWidget visual (D-04):
<input type="hidden" {...register('rating', { valueAsNumber: true })} />
<StarWidget value={rating ?? 0} onChange={(n) => setValue('rating', n)} />
```

**Review schema:**
```typescript
const reviewSchema = z.object({
  rating: z.number().min(1, 'Vui lòng chọn số sao').max(5),
  content: z.string().max(500, 'Tối đa 500 ký tự').optional(),
});
```

---

### `ReviewList.tsx` (FE component, request-response)

**Analog:** `sources/frontend/src/app/products/[slug]/page.tsx` (list render section)

**List render pattern** (lines 321-344):
```typescript
{product.specifications.map((spec, i) => (
  <tr key={i} className={i % 2 === 0 ? styles.specRowEven : styles.specRowOdd}>
    <td className={styles.specLabel}>{spec.label}</td>
    <td className={styles.specValue}>{spec.value}</td>
  </tr>
))}
```

**ReviewList pattern (XSS-safe — D-14):**
```typescript
{reviews.map((review) => (
  <div key={review.id} className={styles.reviewItem}>
    <span className={styles.reviewerName}>{review.reviewerName}</span>
    {/* Plain text node — React tự escape, KHÔNG dùng dangerouslySetInnerHTML */}
    {review.content && <p className={styles.reviewContent}>{review.content}</p>}
  </div>
))}
```

---

### `types/index.ts` — MODIFY (utility)

**Analog:** self (lines 230-241)

**Existing Review interface** (lines 230-241):
```typescript
// ===== REVIEW (Part of Product Service) =====
export interface Review {
  id: string;
  userId: string;
  userName: string;        // <-- CẦN đổi thành reviewerName (align BE DTO)
  userAvatar?: string;
  productId: string;
  rating: number;
  comment: string;         // <-- CẦN đổi thành content (align BE DTO)
  images?: string[];
  createdAt: string;
}
```

**Sau align (D-11 + RESEARCH Pitfall 6):**
```typescript
export interface Review {
  id: string;
  userId: string;
  reviewerName: string;    // snapshot từ JWT claim 'name' — D-11
  productId: string;
  rating: number;
  content?: string;        // nullable — D-06
  createdAt: string;
}
```

---

## Shared Patterns

### ApiResponse Envelope (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/ApiResponse.java`
**Apply to:** `ReviewController.java`, `InternalOrderController.java`
```java
// Tất cả controller responses đều wrap:
return ApiResponse.of(200, "Reviews listed", data);
return ApiResponse.of(201, "Review created", data);
```

### ResponseStatusException Error Handling (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/api/GlobalExceptionHandler.java`
**Apply to:** `ReviewService.java`, `ReviewController.java`

GlobalExceptionHandler tự catch `ResponseStatusException` (lines 58-82) và map thành `ApiErrorResponse`. ReviewService chỉ cần throw:
```java
throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "REVIEW_NOT_ELIGIBLE");
throw new ResponseStatusException(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS");
throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
```

**Lưu ý:** `DataIntegrityViolationException` (RESEARCH Pitfall 3) KHÔNG được handle trong GlobalExceptionHandler hiện tại — ReviewService phải pre-check `existsByProductIdAndUserId` trước insert.

### JWT Bearer Parse (BE)
**Source:** `sources/backend/product-service/src/main/java/com/ptit/htpt/productservice/web/JwtRoleGuard.java` (lines 34-63)
**Apply to:** `ReviewController.java`

Copy toàn bộ signing key + parse pattern từ JwtRoleGuard vào ReviewController (hoặc inject JwtRoleGuard bean và thêm method `extractClaims(String auth): Claims`).

### httpGet/httpPost Pattern (FE)
**Source:** `sources/frontend/src/services/http.ts` (lines 144-148)
**Apply to:** `services/reviews.ts`
```typescript
export const httpGet    = <T>(path: string) => request<T>('GET', path);
export const httpPost   = <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) => ...
// Bearer token tự động được attach từ localStorage (lines 55-60)
```

### useToast Hook (FE)
**Source:** `sources/frontend/src/app/products/[slug]/page.tsx` (line 23)
**Apply to:** `ReviewSection.tsx`, `ReviewForm.tsx`
```typescript
import { useToast } from '@/components/ui/Toast/Toast';
const { showToast } = useToast();
// D-07: showToast('Đã gửi đánh giá', 'success')
// Error: showToast('Có lỗi xảy ra', 'error')
```

### isApiError + error handling (FE)
**Source:** `sources/frontend/src/app/products/[slug]/page.tsx` (line 15)
**Apply to:** `ReviewSection.tsx`
```typescript
import { isApiError } from '@/services/errors';
// catch(err) { if (isApiError(err) && err.code === 'REVIEW_NOT_ELIGIBLE') ... }
```

---

## No Analog Found

| File | Role | Data Flow | Lý do không có analog |
|------|------|-----------|----------------------|
| `ReviewSection/StarWidget.tsx` | component | event-driven | Không có interactive widget nào trong codebase — custom CSS star input là lần đầu tiên. Dùng pattern từ RESEARCH Pattern 7 (5 `<button>` + hover state + `aria-label`). |

---

## Metadata

**Analog search scope:** `sources/backend/product-service/`, `sources/backend/order-service/`, `sources/backend/user-service/`, `sources/frontend/src/`
**Files scanned:** 22 BE files + 55 FE files = 77 files
**Pattern extraction date:** 2026-04-27
