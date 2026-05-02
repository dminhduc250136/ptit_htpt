---
phase: 13-reviews-ratings
plan: "04"
subsystem: frontend
tags: [nextjs, react, typescript, rhf, zod, reviews, ratings, xss-safe, eligibility]

requires:
  - phase: 13-03
    provides: BE reviews API — 3 endpoints (list, eligibility, submit), Review BE DTO với reviewerName+content

provides:
  - Review interface align với BE DTO (reviewerName + content, remove userName/comment)
  - services/reviews.ts — listReviews, checkEligibility, submitReview
  - ReviewSection/StarWidget.tsx — 5-button radiogroup, hover/click, aria-label, focus-visible
  - ReviewSection/ReviewForm.tsx — rhf+zod, rating required, content optional ≤500, char counter warn≥450
  - ReviewSection/ReviewList.tsx — paginated list, XSS-safe text node, ReadOnlyStars, skeleton, retry
  - ReviewSection/ReviewSection.tsx — orchestrator eligibility pre-check + 3 hint variants + submit toast
  - ReviewSection/ReviewSection.module.css — design tokens CSS module
  - PDP page.tsx: tab Reviews wired → <ReviewSection />
  - ProductCard + PDP header: dùng product.avgRating thay product.rating

affects: [pdp-reviews-tab, product-card-rating, phase-13-complete]

tech-stack:
  added:
    - "react-hook-form + zod (ReviewForm validation)"
    - "@hookform/resolvers/zod (zodResolver)"
  patterns:
    - "ReviewSection orchestrator: eligibility pre-check chỉ khi user logged-in (D-09)"
    - "XSS-safe: {review.content} React text node — KHÔNG dangerouslySetInnerHTML"
    - "Fail-safe eligibility: catch → setEligible(false) (ẩn form, không leak error)"
    - "Submit error mapping: REVIEW_NOT_ELIGIBLE → toast + setEligible(false); REVIEW_ALREADY_EXISTS → toast; default → generic"
    - "Char counter: warn color khi ≥450 chars (--error token)"

key-files:
  created:
    - sources/frontend/src/services/reviews.ts
    - sources/frontend/src/app/products/[slug]/ReviewSection/StarWidget.tsx
    - sources/frontend/src/app/products/[slug]/ReviewSection/ReviewForm.tsx
    - sources/frontend/src/app/products/[slug]/ReviewSection/ReviewList.tsx
    - sources/frontend/src/app/products/[slug]/ReviewSection/ReviewSection.tsx
    - sources/frontend/src/app/products/[slug]/ReviewSection/ReviewSection.module.css
  modified:
    - sources/frontend/src/types/index.ts
    - sources/frontend/src/components/ui/ProductCard/ProductCard.tsx
    - sources/frontend/src/app/products/[slug]/page.tsx

key-decisions:
  - "useAuth từ @/providers/AuthProvider (không phải @/components/AuthProvider) — verified từ source"
  - "Product.rating giữ lại dưới dạng optional legacy field, thêm avgRating?: number mới — tránh breaking change"
  - "Build fail /profile/orders là pre-existing useSearchParams Suspense issue — không liên quan plan này; TypeScript PASS"
  - "Lint errors (2 errors) đều ở AddressPicker.tsx (pre-existing) — không có lỗi trong files mới"

metrics:
  duration: ~20min
  completed: 2026-04-27T09:26:00Z
  tasks: 2/3 (Task 3 = checkpoint:human-verify)
  files_created: 6
  files_modified: 3
---

# Phase 13 Plan 04: Frontend ReviewSection Summary

**ReviewSection FE hoàn chỉnh trên PDP tab Reviews — StarWidget interactive, ReviewForm rhf+zod, ReviewList paginated XSS-safe, eligibility pre-check 3 variants, avgRating display align BE, TypeScript PASS**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-27T09:06:00Z
- **Completed:** 2026-04-27T09:26:00Z
- **Tasks:** 2/3 (Task 3 = UAT checkpoint — awaiting human verify)
- **Files created:** 6 (services/reviews.ts + 5 ReviewSection/* files)
- **Files modified:** 3 (types/index.ts, ProductCard.tsx, page.tsx)

## Accomplishments

### Task 1 — Type align + services/reviews.ts + avgRating display
- `types/index.ts`: Review interface rewritten → reviewerName+content align với BE DTO; Product.avgRating thêm mới (rating deprecated optional)
- `services/reviews.ts`: tạo mới với 3 functions — listReviews, checkEligibility, submitReview; httpGet/httpPost auto-unwrap ApiResponse envelope
- `ProductCard.tsx`: dùng `product.avgRating ?? 0` thay `product.rating`
- `page.tsx` PDP header: stars + ratingText dùng `product.avgRating` với toFixed(1)

### Task 2 — ReviewSection components + wire PDP
- `StarWidget.tsx`: `role="radiogroup"`, `aria-label="Chọn số sao"`, 5 buttons với `aria-pressed`, hover fill `var(--secondary-container)`, focus-visible outline 2px primary
- `ReviewForm.tsx`: rhf+zod schema `rating: z.number().min(1)` + `content: z.string().max(500).optional()`, char counter realtime đổi màu ≥450, submit loading state, reset sau success
- `ReviewList.tsx`: heading "Đánh giá từ khách hàng ({count})", ReadOnlyStars 14×14, `{review.content}` text node (XSS-safe, không dangerouslySetInnerHTML), "Xem thêm đánh giá" button append pagination, skeleton loading 3 cards, RetrySection on error
- `ReviewSection.tsx`: eligibility check chỉ khi user logged-in; 3 variants — guest hint + login link, eligible → form, not-eligible → hint; submit → toast "Đã gửi đánh giá" + reload list; error codes mapping đầy đủ
- `page.tsx`: reviewPlaceholder block → `<ReviewSection productId={product.id} slug={slug ?? ''} />`

## Task Commits

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Align Review type + services/reviews.ts + avgRating | f751790 | types/index.ts, services/reviews.ts, ProductCard.tsx, page.tsx |
| 2 | ReviewSection components + wire PDP | ea33c5a | StarWidget, ReviewForm, ReviewList, ReviewSection, CSS module, page.tsx |

## Build & Lint Results

- **TypeScript:** PASS (Finished TypeScript in 2.7s — 0 TS errors)
- **Build:** Fail ở `/profile/orders` (pre-existing `useSearchParams` Suspense issue — tồn tại TRƯỚC plan này, confirmed bằng git stash test)
- **Lint:** 2 errors ở `AddressPicker.tsx` (pre-existing `set-state-in-effect`) — KHÔNG có error nào từ files mới của plan 13-04
- **XSS grep:** 0 match `dangerouslySetInnerHTML` trong ReviewSection/

## Deviations from Plan

**1. [Rule 1 - Pre-existing] Build fail /profile/orders không liên quan**
- Confirmed pre-existing qua git stash test: lỗi tồn tại trước khi apply bất kỳ change nào của plan 04
- TypeScript compilation PASS — chỉ static rendering của /profile/orders fail
- Không fix (ngoài scope plan 04); ghi nhận ở đây

**2. [Rule 2 - Auth path verify] useAuth import từ @/providers/AuthProvider**
- Plan comment "executor confirm path đúng" — đã verify: file tại `sources/frontend/src/providers/AuthProvider.tsx`, export `useAuth`
- Import trong ReviewSection.tsx: `import { useAuth } from '@/providers/AuthProvider'` — correct

## UAT Status

Task 3 (checkpoint:human-verify) — **DEFERRED (2026-05-02)**

User đã accept đóng Phase 13 mà không chạy đủ 6 kịch bản UAT — pattern "executed, verify pending" để mở Phase 14. UAT 6 kịch bản dưới đây vẫn cần chạy trước khi tag milestone v1.2.

Original status: PENDING human UAT

6 kịch bản cần verify:
1. Guest: hint + link đăng nhập, KHÔNG gọi eligibility endpoint
2. Logged-in not-eligible: hint "Chỉ người đã mua...", form ẩn
3. Logged-in eligible: form hiện, star hover/click, submit toast, form reset
4. XSS test: `<script>alert('XSS')</script>` → KHÔNG execute, text literal
5. Error states: duplicate review → toast "Bạn đã đánh giá..."
6. avgRating display: ProductCard + PDP header hiển thị decimal từ BE

## Known Stubs

Không có stubs — tất cả data wired thực từ BE API endpoints.

## Threat Flags

Không có surface mới ngoài threat model đã định nghĩa trong PLAN.md (T-13-04-01 đến T-13-04-05).

**T-13-04-01 (XSS) verify:** `{review.content}` React text node — grep confirm 0 match `dangerouslySetInnerHTML` trong ReviewSection/. Defense in depth với BE Jsoup strip (Plan 03).

## Self-Check: PASSED

- FOUND: services/reviews.ts ✓
- FOUND: StarWidget.tsx, ReviewForm.tsx, ReviewList.tsx, ReviewSection.tsx, ReviewSection.module.css ✓
- FOUND: types/index.ts có reviewerName + avgRating ✓
- FOUND: ProductCard.tsx có product.avgRating ✓
- FOUND: page.tsx có `<ReviewSection` + import `from './ReviewSection/ReviewSection'` ✓
- FOUND commit f751790 (Task 1) ✓
- FOUND commit ea33c5a (Task 2) ✓
- TypeScript PASS ✓
- No dangerouslySetInnerHTML in ReviewSection/ ✓

---
*Phase: 13-reviews-ratings*
*Plan: 04 (Tasks 1-2 complete, Task 3 awaiting UAT)*
*Completed: 2026-04-27*
