# Phase 13: Reviews & Ratings - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 13-reviews-ratings
**Areas discussed:** Cross-service eligibility, Review form UX, displayName snapshot, Eligibility check FE flow

---

## Cross-service Eligibility

| Option | Description | Selected |
|--------|-------------|----------|
| RestTemplate internal HTTP | product-svc gọi order-svc:8080/internal qua Docker network; endpoint mới | ✓ |
| Cross-schema SQL query | product-svc DataSource query trực tiếp order_svc schema | |

**User's choice:** RestTemplate internal HTTP
**Notes:** Endpoint mới `/internal/orders/eligibility?userId=&productId=`. Không qua gateway (Docker-internal only). Gateway không route /internal/* ra ngoài.

---

## Eligibility Endpoint Placement (order-svc)

| Option | Description | Selected |
|--------|-------------|----------|
| Internal-only endpoint | GET /internal/orders/eligibility — gateway không route | ✓ |
| Reuse GET /orders | Fetch list rồi filter client-side trong product-svc | |

**User's choice:** Internal-only endpoint
**Notes:** URL: `http://order-service:8080/internal/orders/eligibility`

---

## Review Form UX — Star Widget

| Option | Description | Selected |
|--------|-------------|----------|
| CSS interactive stars | 5 clickable stars, hover highlight, no lib | ✓ |
| Radio buttons styled as stars | Accessible radio inputs labeled as stars | |

**User's choice:** CSS interactive stars

---

## Review Form UX — Form Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Trong reviews tab — top | Form ở đầu tab (trên list) | ✓ |
| Section riêng phía dưới PDP | Reviews section luôn visible, không cần tab | |

**User's choice:** Trong reviews tab, form đầu tab, list bên dưới

---

## Post-submit Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Reset form + refresh list | Toast + reset + reload trang 1 list | ✓ |
| Hide form sau submit | Form biến mất, show message tĩnh | |

**User's choice:** Reset form + toast + refresh list

---

## Content Limit

| Option | Description | Selected |
|--------|-------------|----------|
| 10–2000 ký tự | Min required, max 2000 | |
| 0–500 ký tự | Optional content, max 500 | ✓ |

**User's choice:** Optional content, max 500 ký tự
**Notes:** Chỉ rating (1-5) là bắt buộc. Textarea là optional.

---

## displayName Source

| Option | Description | Selected |
|--------|-------------|----------|
| JWT claim (fullName) | Không cần call user-svc, lấy từ 'name' claim | ✓ |
| Fetch từ user-svc | Inter-service call thêm | |

**User's choice:** JWT claim — snapshot fullName từ 'name' claim

---

## JWT 'name' Claim Existence

| Option | Description | Selected |
|--------|-------------|----------|
| Chưa rõ, để planner check | Planner inspect auth-svc JwtProvider | ✓ |
| Chưa có, cần thêm | Add fullName vào token generation | |

**Notes:** Planner/researcher sẽ inspect `auth-service/JwtProvider` và thêm claim nếu thiếu.

---

## Eligibility FE Flow

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-check endpoint riêng | GET /eligibility khi load tab → show/hide form | ✓ |
| Submit-only check | Luôn show form, handle 422 khi submit | |

**User's choice:** Pre-check endpoint — 2 round trips, UX rõ ràng (form chỉ hiện khi eligible)

---

## Not Logged In UX

| Option | Description | Selected |
|--------|-------------|----------|
| Show hint + login link | Link /login?redirect=/products/{slug} | ✓ |
| Không hiển thị gì | Ít disruptive, mất cross-sell opportunity | |

**User's choice:** Show hint + login link

---

## Claude's Discretion

- Jsoup version cho OWASP sanitize
- Pagination UI cho review list (numbered vs "Xem thêm")
- Review list item layout chi tiết

## Deferred Ideas

- REV-04 author edit/delete — v1.3 (locked)
- Review images upload — v1.3
- Hide form nếu user đã review (duplicate prevention FE) — Claude's Discretion
