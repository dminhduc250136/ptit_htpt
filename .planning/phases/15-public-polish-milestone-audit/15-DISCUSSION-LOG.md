# Phase 15: Public Polish + Milestone Audit — Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-02
**Phase:** 15-public-polish-milestone-audit
**Mode:** Auto (global Auto Mode active — Claude picked recommended defaults from analysis of ROADMAP success criteria + existing codebase state, no interactive questions asked)
**Areas analyzed:** Hero, Featured, Categories, New Arrivals, PDP Gallery, PDP Specs, PDP Breadcrumb, PDP Stock Badge, Smoke E2E, Milestone Audit

---

## Hero (PUB-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Local WebP + next/image priority | Download Unsplash → public/hero/, dùng next/image priority | ✓ |
| Remote loader Unsplash | Cấu hình next.config images.domains, vẫn fetch từ CDN | |
| Giữ `<img>` + thêm `loading=eager` | Quick fix, không cải thiện đáng kể LCP | |

**Auto-selected:** Local WebP + priority — tránh dependency CDN khi demo offline, LCP tốt nhất.

**`/collections` route**: chưa tồn tại → đổi CTA thứ 2 thành link `/products` để tránh 404.

---

## Featured Products (PUB-01)

| Option | Description | Selected |
|--------|-------------|----------|
| Sort `createdAt,desc` + CSS scroll-snap carousel | Match ROADMAP exact | ✓ |
| Sort `reviewCount,desc` (giữ hiện tại) + grid | KHÔNG match SC-1 | |
| Slick/Embla JS carousel | Lib mới = scope creep | |

**Dedupe vs New Arrivals:** Featured = page 0 size 8; New Arrivals = page 1 size 8 cùng sort.

---

## Categories Grid (PUB-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Link `/products?category={slug}` (giữ hiện tại) | Categories ≠ Brands trong data model | ✓ |
| Link `/products?brand={slug}` per ROADMAP wording | Sai semantic — categories không phải brands | |
| Build "Shop by Brand" section riêng | Scope creep cho v1.2 | |

**Lý do chọn:** ROADMAP wording "brand-filtered" diễn dịch là gợi ý chứ không phải hard contract. Categories và brands là 2 dimension khác nhau — đổi sang `?brand=` sẽ phá tính semantic.

---

## PDP Thumbnail Gallery (PUB-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Polish existing CSS+React state implementation | Code đã có, thêm a11y + active highlight | ✓ |
| Refactor sang lightbox lib | Defer v1.3 (locked decision) | |
| Embedded zoom-on-hover | Out of scope | |

---

## PDP Specs Table (PUB-03)

| Option | Description | Selected |
|--------|-------------|----------|
| Giữ implementation hiện tại (tab Thông số) | Đã render từ `product.specifications` đúng | ✓ |
| Render inline (no tab) dưới price | UX rebuild, không trong scope | |

---

## PDP Breadcrumb (PUB-03)

| Option | Description | Selected |
|--------|-------------|----------|
| `Home > {Brand} > {Name}` strict per ROADMAP | Match SC-2 + tận dụng filter Phase 14 | ✓ |
| Giữ `Home > Sản phẩm > {Category} > {Name}` | KHÔNG match SC-2 | |
| Hybrid (Home > Brand > Category > Name) | Quá dài, breadcrumb sạch hơn 3 segment | |

**Fallback:** Nếu `brand == null` → `Home > Sản phẩm > {Name}`.

---

## PDP Stock Badge (PUB-04)

| Option | Description | Selected |
|--------|-------------|----------|
| 3-tier color (green ≥10 / yellow 1-9 / red 0) + hide button khi 0 | Match SC-3 strict | ✓ |
| 2-tier (giữ hiện tại) | KHÔNG match SC-3 | |
| 4-tier (e.g. very-low <3) | Over-engineer | |

---

## Smoke E2E (TEST-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 4 tests trong 1 file `smoke.spec.ts` | Gọn, 4 critical paths theo ROADMAP | ✓ |
| 4 tests split per-feature file | Phân tán, khó maintain | |
| 8+ tests full suite | Defer v1.3 | |

**Test list selected:** Homepage navigation, Address-at-checkout, Review submission, Profile editing.

---

## Milestone Audit + v1.2 Tag

| Option | Description | Selected |
|--------|-------------|----------|
| Run `/gsd-audit-milestone v1.2` → tag annotated v1.2 | Standard GSD milestone closure | ✓ |
| Skip audit, tag direct | Bỏ qua verification gate | |

**User push tag:** thủ công sau review, KHÔNG auto-push.

---

## Claude's Discretion (Auto Mode picks)

- CSS class names + module structure cho `featuredScroll` carousel.
- Hero WebP image dimensions (~600×800 + 400×500, ~150-200KB).
- Quantity selector hide vs disable khi stock=0 (chọn hide consistent với button).
- Keyboard arrow thumbnail nav (D-11) — implement nếu < 30 LOC.
- DELIVERED order test data setup approach.
- Inline copy chi tiết stock badge (Vietnamese natural).

---

## Deferred Ideas (captured during analysis)

- PDP fullscreen lightbox + axe-core gate
- Playwright full 8+ suite
- `featured BOOLEAN` column
- Shop by Brand grid
- Visual regression testing
- Hero A/B testing
- Per-category icons
- Lighthouse CI gate
- `/collections` route
- Stock badge animations
