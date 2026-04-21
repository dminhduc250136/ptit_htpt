# UC-PRODUCT-REVIEW: Review & Rating Sản phẩm

## Tóm tắt
Customer đã có order DELIVERED chứa sản phẩm có quyền post review (1-5 sao + comment <= 1000 chars). 1 user x 1 sản phẩm x 1 order = 1 review. Edit trong 24h. Average rating compute realtime/async.

## Context Links
- Strategy: [../strategy/services/product-business.md#review-rules-br-review](../strategy/services/product-business.md#review-rules-br-review)
- Technical Spec: [../technical-spec/ts-product-review.md](../technical-spec/ts-product-review.md)
- Architecture: [../architecture/services/product-service.md](../architecture/services/product-service.md)

## Actors
- **Primary**: Customer (đã có order DELIVERED)

## Preconditions
- User đã login role=CUSTOMER
- User có ít nhất 1 order state=DELIVERED hoặc COMPLETED chứa product cần review
- User chưa review product trong order đó (1-to-1)

---

## Flow A — Xem Reviews (Public)

### Main Flow
1. User vào `/product/{slug}`, scroll xuống tab "Đánh giá"
2. FE gọi GET /api/v1/products/{id}/reviews?page=0&size=10&sort=createdAt,desc
3. BE trả reviews (filter isHidden=false) + rating distribution
4. FE render:
   - Rating summary: 4.7 ★ (128 đánh giá)
   - Distribution bars: 5★ (70%), 4★ (20%), 3★ (5%), 2★ (3%), 1★ (2%)
   - List review với: user avatar + masked name + rating + date + comment
5. Pagination hoặc "Load more" (mobile)

### Acceptance Criteria
- [ ] AC-A1: Hiển thị tối đa 10 reviews/page
- [ ] AC-A2: Rating distribution từ aggregate (không query từng review)
- [ ] AC-A3: User name mask: "Nguyễn V." (show first + last initial) — privacy
- [ ] AC-A4: Reviews isHidden=true không show public

### Data Outputs (GET reviews)
```json
{
  "summary": {
    "averageRating": 4.7,
    "totalReviews": 128,
    "distribution": { "5": 89, "4": 26, "3": 7, "2": 4, "1": 2 }
  },
  "data": [
    {
      "id": "uuid",
      "rating": 5,
      "comment": "Sản phẩm tốt, giao nhanh",
      "userMaskedName": "Nguyễn V.",
      "userAvatar": "https://...",
      "createdAt": "2026-03-15T..."
    }
  ],
  "meta": { "page": 0, "size": 10, "total": 128 }
}
```

---

## Flow B — Post Review

### Preconditions
- User logged in
- User có entry trong `review_eligibility` chưa `used=true`

### Entry points
1. **From order detail**: Page `/account/orders/{id}`, với order DELIVERED, mỗi item có button "Viết đánh giá"
2. **From profile**: Page `/account/reviews` liệt kê pending reviews (sản phẩm đã mua mà chưa review)

### Main Flow
1. User click "Viết đánh giá" → modal mở với product info
2. User chọn rating 1-5 sao (required) + nhập comment (optional, max 1000)
3. User click "Gửi"
4. FE gửi POST /api/v1/products/{productId}/reviews { orderId, rating, comment }
5. BE:
   - Verify user có eligibility (query `review_eligibility` where userId, productId, orderId, used=false)
   - Nếu không có → 403 `NOT_ELIGIBLE_TO_REVIEW`
   - Insert review
   - Mark eligibility `used=true`
   - Update product aggregate (async job hoặc inline):
     - `reviewCount++`
     - Recompute `rating = avg(rating all reviews visible)`
   - Publish event `ReviewPosted`
6. BE trả 201 { review }
7. FE close modal, refresh order/reviews page, toast "Cảm ơn đánh giá!"

### Exception Flows
- **EF-B1: User chưa mua** → 403 `NOT_ELIGIBLE`
- **EF-B2: Đã review product này trong order** → 400 `ALREADY_REVIEWED`
- **EF-B3: Order chưa DELIVERED** → 403 `ORDER_NOT_DELIVERED`
- **EF-B4: Rating < 1 hoặc > 5** → 400 `INVALID_RATING`
- **EF-B5: Comment > 1000 chars** → 400 `COMMENT_TOO_LONG`

### Acceptance Criteria
- [ ] AC-B1: Chỉ user với order DELIVERED + có product mới post được
- [ ] AC-B2: 1 user x 1 product x 1 order = 1 review
- [ ] AC-B3: Average rating update trong 5 phút sau post
- [ ] AC-B4: Review public ngay (không cần duyệt)

### Data Inputs
- orderId (UUID, required)
- rating (int 1-5, required)
- comment (string, max 1000, optional)

### Data Outputs
- `{ id, productId, rating, comment, createdAt, userMaskedName, userAvatar }`

---

## Flow C — Edit Review (trong 24h)

### Preconditions
- Review tạo < 24h
- User là owner

### Main Flow
1. User vào `/account/reviews` → click review để edit
2. Modal mở với prefilled data
3. User edit rating/comment
4. FE gửi PATCH /api/v1/reviews/{id} { rating, comment }
5. BE:
   - Verify owner
   - Verify createdAt > now - 24h
   - Update
   - Recompute product aggregate async
6. BE trả 200

### Exception Flows
- **EF-C1: Review > 24h** → 403 `REVIEW_EDIT_EXPIRED`
- **EF-C2: Not owner** → 403 `FORBIDDEN`

### Acceptance Criteria
- [ ] AC-C1: Edit window 24h, enforce backend
- [ ] AC-C2: Edit button hidden trong UI nếu > 24h

---

## Flow D — Admin Hide Review (cross-ref UC-ADMIN)

### Main Flow
1. Admin vào `/admin/reviews` hoặc product detail admin view
2. Admin click "Hide" trên review vi phạm
3. FE gửi PATCH /api/v1/admin/reviews/{id}/hide { reason }
4. BE: set `isHidden=true`, recompute aggregate (exclude hidden)
5. Review không còn hiển thị public
6. BE trả 200

### Acceptance Criteria
- [ ] AC-D1: Admin có thể hide review
- [ ] AC-D2: Aggregate exclude hidden reviews
- [ ] AC-D3: User vẫn thấy review của mình trong profile (nhưng với flag hidden)

---

## Business Rules (references)
- BR-REVIEW-01: Chỉ order DELIVERED mới review
- BR-REVIEW-02: 1 user x 1 product x 1 order
- BR-REVIEW-03: Rating 1-5, comment max 1000
- BR-REVIEW-04: No approval needed
- BR-REVIEW-05: Rating aggregate
- BR-REVIEW-06: Edit 24h window

## Non-functional Requirements
- **Performance**: Post review < 500ms. Aggregate update async (Kafka event `ReviewPosted` → job).
- **Data integrity**: Eligibility check atomic (tránh race condition double review)

## UI Screens
- `/product/{slug}` — tab Reviews (read)
- `/account/orders/{id}` — "Viết đánh giá" buttons per item
- `/account/reviews` — pending + posted reviews của user
- Modal "Viết đánh giá" — rating stars + textarea
- Admin: `/admin/products/{id}` — review moderation panel
