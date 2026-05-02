# Project Research Summary — v1.2 UI/UX Completion

**Project:** tmdt-use-gsd (B2C e-commerce — laptop catalog, microservices stack)
**Domain:** B2C e-commerce — visible-first UX completion on top of v1.0 + v1.1 foundation
**Researched:** 2026-04-26
**Milestone:** v1.2 (subsequent — phase numbering tiếp tục từ Phase 9)
**Confidence:** HIGH cho stack/features/architecture/pitfalls (cross-verified với codebase + Context7 + ngành công nghiệp references)

---

## Executive Summary

v1.2 là milestone "UX completion" trên nền v1.0 (CRUD + Swagger) và v1.1 (DB foundation + real auth + admin CRUD + cart→order persistence). 11 target features chia 5 nhóm: **residual closure** (AUTH-06, UI-02, Playwright re-baseline), **account** (wishlist, order filtering, profile editing), **discovery** (reviews, advanced filters), **checkout** (address book), **public polish** (homepage, PDP enhancements). Đây là milestone "ship features visible" thuần túy — không thêm service mới, không thay framework, chỉ extend existing Spring Boot 3.3.2 / Next.js 16.2.3 stack với 1 BE lib (Thumbnailator nếu chọn upload thật, hoặc 0 lib nếu chọn URL input cho avatar) + 4 FE libs (rhf, zod, @hookform/resolvers, yet-another-react-lightbox).

**Recommended approach:** Closure-first (Phase 9 dọn 3 carry-over residual + verify AUTH-06 thực tế đã đóng chưa), schema-heavy phases sớm (Phase 10 user-svc V3-V5 cluster cho profile/address/wishlist; Phase 13 product-svc V4-V5 cho reviews + denormalized cached cols), code-only phases cuối (homepage + PDP polish). Kiến trúc giữ nguyên: wishlist + addresses ở user-svc, reviews ở product-svc, **không cross-service backend aggregation** (admin KPI dùng FE Promise.all gọi 3 `/stats` endpoint riêng từng service). Snapshot pattern v1.1 (OrderItem.shippingAddress JSONB) được giữ → address book hard-delete an toàn.

**Key risks:** (1) **Flyway V-number collision** — phải reserve V-number ranges trong MILESTONES.md trước khi spawn plan-phase agents (precedent v1.1 đã hit collision DB-05). (2) **Reviews XSS + rating drift** — sanitize content + recompute average from scratch, không dùng formula incremental. (3) **AUTH-06 verification** — middleware.ts hiện đã có matcher mở rộng `/account|/profile|/checkout|/admin` (codebase inspection ARCHITECTURE.md §1) nhưng PROJECT.md/MILESTONES.md vẫn ghi PARTIAL → Phase 9.1 phải verify state thực thay vì plan thêm work. (4) **Avatar upload security** — magic byte check + re-encode hoặc disable upload (URL input fallback). Mitigations đã document trong PITFALLS.md (26 pitfalls, 7 critical).

---

## Key Findings

### Recommended Stack (additions only) — Detail: STACK.md

**Backend additions:**
- **Thumbnailator 0.4.20** (user-service): pure-Java image resize cho avatar upload — chỉ thêm nếu chọn multipart path; nếu chọn URL-input fallback (ARCHITECTURE.md §7 recommendation) → 0 BE deps.
- **Spring Data JPA Specifications**: dùng cho advanced search filters (brand IN / price BETWEEN / rating ≥ / inStock). API có sẵn — không thêm dep.
- **Postgres BYTEA inline** cho avatar (nếu upload thật) — không cần MinIO/S3.

**Frontend additions:**
- **react-hook-form 7.55.x + zod 3.24.x + @hookform/resolvers 5.x** (~23KB gz)
- **yet-another-react-lightbox 3.21.x** (~11KB gz) cho PDP gallery
- **Custom 30-LOC StarRating**, **Native `<input type="date">`**, **CSS scroll-snap** carousel — tránh kéo thêm libs

**Net footprint:** ~34KB gz FE + 1 BE lib (optional). Anti-bloat reject Redux, TanStack Query, Tailwind, shadcn/ui, MinIO, Redis, Elasticsearch, dayjs, react-dropzone.

### Expected Features — Detail: FEATURES.md

**Table stakes (all 11 ship):** AUTH-06 (LOW), UI-02 (MED), Playwright re-baseline (MED), Wishlist (MED), Order filtering (MED), Profile editing (MED), Reviews (HIGH), Search filters (HIGH), Address book (MED), Homepage (MED), PDP enhancements (MED).

**Skip sub-features:** multi-list wishlists, verified-purchase reviews, helpful votes, review images, facet counts, address validation API, image zoom, dashboard charts, email change, avatar crop UI.

**Anti-features (out):** WebSocket sync, review moderation queue, Elasticsearch, product comparison, wishlist sharing, multi-step checkout (defer v1.3+).

### Architecture Approach — Detail: ARCHITECTURE.md

**Service ownership:** user-svc (wishlists, addresses, profile), product-svc (reviews, search filters, specs/gallery/avg_rating), order-svc (filtering query params), FE-only (homepage, PDP), FE aggregate (UI-02 admin KPI = Promise.all 3 `/stats`).

**Critical gateway change:** Thêm `user-service-me` route group **TRƯỚC** `user-service-base` (precedent admin/auth ordering). Sai order → `/api/users/me/wishlist` match `/api/users/{id}` với id="me" → 404.

**Schema cluster:**
- user-svc V3 (avatar_url) + V4 (addresses) + V5 (wishlists)
- product-svc V4 (reviews) + V5 (specs+gallery+avg_rating+review_count) + V6 (search indexes optional)
- order-svc V3 composite index optional

**Image upload decision:** v1.2 dùng URL input (precedent `thumbnail_url`) → 0 infra change. Multipart defer v1.3.

### Critical Pitfalls — Detail: PITFALLS.md (26 total, top 7)

1. **Flyway V-number collision** (§1) — Reserve V-numbers trong MILESTONES.md TRƯỚC plan-phase
2. **AUTH-06 matcher pattern** (§2) — Cover root + nested; skip `/api/users/auth/*`
3. **Reviews XSS** (§3) — Plain text render, BE OWASP sanitize
4. **Rating average drift** (§4) — Recompute from scratch trong `@PostPersist/Update/Remove`
5. **Address default-flag concurrency** (§7) — Partial unique index `WHERE is_default = true`
6. **Password change without oldPassword** (§8) — Endpoint dedicated, KHÔNG trong PATCH chung
7. **Avatar upload bypass** (§10) — Magic byte + re-encode + cap 2MB, hoặc URL input

Notable: hero LCP (§18), wishlist stale stock (§14), order filter timezone (§11), Playwright stale selectors (§24), traceId missing (§25), ApiErrorResponse new branches (§26).

---

## Implications for Roadmap

### Synthesizer Phase Split Recommendation: 7 phases (9-15)

FEATURES suggested 4 phases (gom theo nhóm domain). ARCHITECTURE suggested 9 phases (tách theo schema). **Synthesizer middle-ground: 7 phases** — gom wishlist/address/profile cùng user-svc cluster nhưng tách reviews riêng (HIGH complexity), gom homepage+PDP (cùng FE-heavy), order filtering đính kèm address phase.

| # | Phase | Goal | Features |
|---|-------|------|----------|
| 9 | Residual Closure & Verification | Verify AUTH-06 + UI-02 dashboard rewire + Playwright re-baseline | Residual 3 |
| 10 | User-Svc Schema Cluster + Profile Editing | V3-V5 user-svc, gateway `me` routes, FE deps install, profile UI form pattern foundation | Profile editing (+ schema cho 11, 12) |
| 11 | Address Book + Order History Filtering | Address CRUD + checkout integration + order filter bar | Address book, Order filtering |
| 12 | Wishlist | Heart icon + wishlist page + move-to-cart | Wishlist |
| 13 | Reviews Schema + UI | V4-V5 product-svc, recompute logic, XSS-safe rendering, integrate PDP | Reviews & ratings |
| 14 | Advanced Search Filters | JPA Specification + FilterSidebar + URL state | Search filters |
| 15 | Public Polish + Milestone Audit | Homepage redesign + PDP enhancements + Playwright v1.2 + audit + tag | Homepage, PDP enhancements |

### Phase Ordering Rationale

- Closure first → validate audit assumptions (AUTH-06)
- Schema-heavy early (P10, P13) → group Flyway migrations vào 2 phases tập trung
- Profile editing trong P10 (form pattern foundation cho address/review forms sau)
- Address + Order filtering gom (cùng `/profile/*`, size phù hợp)
- Reviews P13 sau wishlist (HIGH complexity → wishlist MEDIUM ship trước build confidence)
- Search filters P14 sau reviews (rating filter cần avg_rating cached col)
- Public polish cuối (depends product schema từ P13)

### Research Flags

**Phases likely needing `/gsd-research-phase`:**
- **Phase 13 (Reviews):** XSS sanitization library config, rating recompute pattern, N+1 prevention
- **Phase 14 (Search filters):** JPA Specification advanced patterns, URL state encoding
- **Phase 10 (Avatar sub-decision):** chỉ nếu chọn multipart (recommend URL input → no research)

**Phases với standard patterns (skip research):** Phase 9, 11, 12, 15

### Pre-Phase Setup (BEFORE Phase 9)

**Critical setup tasks roadmap MUST handle:**
1. **Reserve Flyway V-numbers** trong MILESTONES.md v1.2 section (PITFALLS §1):
   - user-svc: V3 avatar, V4 addresses, V5 wishlists
   - product-svc: V4 reviews, V5 specs+gallery+rating-cached, V6 search-indexes (optional)
   - order-svc: V3 composite index (optional)
2. **Verify AUTH-06 codebase state** — Read `sources/frontend/src/middleware.ts` line 41. Nếu đã có matcher mở rộng → mark closed, Phase 9 chỉ verify-only
3. **Avatar upload decision lock** — URL input (recommend) vs multipart
4. **Route convention decision** — recommend `/profile/*` consolidation
5. **Featured = "top 8 by createdAt DESC"** — KHÔNG thêm `featured BOOLEAN` column
6. **Categories source decision** — verify ProductEntity có category field

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Context7-verified versions; Spring Data JPA Specifications stable; foundation locked |
| Features | HIGH | Industry-standard B2C patterns cross-verified (Baymard, NN Group, Shopify, Tiki/Lazada VN) |
| Architecture | HIGH | Verified directly against codebase (`middleware.ts:41`, `application.yml`, services structure) |
| Pitfalls | HIGH | Grounded trong v1.1 incidents thực (DB-05 collision, login redirect loop, cart stock bypass) |

**Overall confidence:** HIGH

### Gaps to Address (open questions cho requirement-definition)

1. **AUTH-06 verification pending** — verify codebase state trước khi plan thêm work
2. **Avatar upload scope** — URL input (recommend) vs multipart
3. **Route convention** — `/profile/*` consolidation
4. **Reviews moderation** — author edit/delete + admin soft-delete via existing `deleted` column
5. **Address limit per user** — cap 10 với `ADDRESS_LIMIT_EXCEEDED` error code
6. **Product specs schema** — recommend constrained TS interface với Zod validate
7. **Reviews eligibility** — any logged-in user v1.2; verified-buyer defer v1.3+
8. **Order filter pagination** — offset acceptable v1.2 (low volume); cursor defer (tech debt)

---

## Sources

### Primary (HIGH confidence)
- Codebase inspection — `middleware.ts`, `application.yml`, Flyway migrations, `services/*.ts`, `package.json`
- `.planning/PROJECT.md`, `.planning/MILESTONES.md`, `.planning/milestones/v1.1-MILESTONE-AUDIT.md`
- Spring Data JPA reference (Context7), react-hook-form 7.55.x docs, yet-another-react-lightbox official docs

### Secondary (MEDIUM-HIGH)
- Baymard Institute, NN Group, Shopify, Tiki/Lazada VN comparison
- Postgres docs (partial unique index, BYTEA TOAST), Next.js middleware matcher syntax, OWASP Java HTML Sanitizer

---
*Research completed: 2026-04-26*
*Ready for roadmap: yes — recommend 7-phase structure (9-15) with pre-phase Flyway V-number reservation + AUTH-06 verification + avatar upload decision lock*
