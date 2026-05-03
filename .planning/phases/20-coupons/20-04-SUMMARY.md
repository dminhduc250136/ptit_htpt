---
phase: 20
plan: 04
subsystem: gateway-routing + roadmap-meta
tags: [gateway, routes, roadmap, infra, coupons]
requirements: [COUP-02, COUP-03]
dependency_graph:
  requires: []
  provides:
    - "Gateway routes /api/orders/coupons/** + /api/orders/admin/coupons/** sẵn sàng cho FE Plan 20-05/20-06"
    - "ROADMAP V-number reservations đúng (V5 cho coupons, V4 cho carts đã shipped)"
  affects:
    - "Phase 20 Plans 20-05 (FE checkout) + 20-06 (FE admin) call qua gateway"
tech_stack:
  added: []
  patterns:
    - "Spring Cloud Gateway Path predicate ordering: specific TRƯỚC catch-all"
    - "RewritePath named group capture (?<seg>.*) — pattern existing"
key_files:
  created: []
  modified:
    - "sources/backend/api-gateway/src/main/resources/application.yml (+28 lines, 4 route entries)"
    - ".planning/ROADMAP.md (V3→V5 + Phase 20 Plans 6 + Phase 21/22 placeholder cleanup)"
decisions:
  - "D-15: 4 gateway routes — 2 cặp base+catchAll cho admin coupons và user coupons"
  - "D-01: V3 → V5 (V4 đã shipped Phase 18 cart)"
  - "Out-of-scope cleanup: Phase 21/22 placeholder Plans copy nhầm 17-XX → thay TBD note (Rule 3 satisfy acceptance criterion 17-04 only in Phase 17)"
metrics:
  duration_min: 5
  completed_date: "2026-05-03"
  tasks: 2
  files_modified: 2
---

# Phase 20 Plan 04: Gateway Routes + ROADMAP Patch — Summary

Wire 4 gateway routes (admin coupons + user coupons preview, mỗi cặp base+catchAll) trong `api-gateway/application.yml` đặt TRƯỚC catch-all order-service-admin/order-service để rewrite chính xác về `/admin/coupons/**` và `/orders/coupons/**`; đồng thời patch `.planning/ROADMAP.md` V-number table (V3→V5 do V4 cart đã shipped Phase 18) và thay placeholder Phase 20 Plans copy nhầm 17-XX bằng đúng 6 plans 20-01..20-06.

## Tasks Completed

| Task | Mô tả | Commit |
|------|-------|--------|
| 1 | Thêm 4 gateway routes (order-service-admin-coupons-base/catch + order-service-coupons-user-base/catch) | `d82a251` |
| 2 | Patch ROADMAP V3→V5 + Phase 20 Plans 6 plans + Phase 21/22 placeholder cleanup | `12837ca` |

## Acceptance Criteria

### Task 1 — Gateway routes
- ✅ Grep `id: order-service-admin-coupons-base` → 1 match (line 113)
- ✅ Grep `id: order-service-admin-coupons$` → 1 match (line 119)
- ✅ Grep `id: order-service-coupons-user-base` → 1 match (line 127)
- ✅ Grep `id: order-service-coupons-user$` → 1 match (line 133)
- ✅ Grep RewritePath admin coupons → 1 match
- ✅ Grep RewritePath user coupons → 1 match
- ✅ Order trong YAML: 4 route mới (line 113/119/127/133) đứng TRƯỚC `order-service-admin-base` (line 141), `order-service-admin` (147), `order-service-base` (155), `order-service` (161)
- ⚠️ mvn package KHÔNG chạy được — Maven CLI không có trên Windows env (đã defer pattern theo Phase 19 Plans 01/02/03 STATE.md). Substituted: pyyaml safe_load → YAML parse OK, 26 routes total, 8 order-service* routes đúng thứ tự specific → catch-all

### Task 2 — ROADMAP patch
- ✅ Grep `| order-svc | V5 | Coupons` → 1 match
- ✅ Grep `| order-svc | V3 |` → 0 match
- ✅ Grep `**Plans:** 6 plans` trong Phase 20 → 1 match (line 151)
- ✅ Grep `20-0[1-6]-PLAN.md` → 6 occurrences
- ✅ Grep `17-04-PLAN.md` → 1 occurrence (line 93, Phase 17 ONLY) — Phase 21/22 placeholders thay bằng TBD note

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Scope/Blocking] Phase 21/22 placeholder cleanup**
- **Found during:** Task 2 verification
- **Issue:** Plan acceptance criterion yêu cầu `17-04-PLAN.md xuất hiện trong Phase 17 ONLY (NOT trong Phase 20 / 21 / 22)`, nhưng `<action>` chỉ mô tả patch Phase 20. Phase 21 và Phase 22 vẫn có placeholder Plans copy nhầm từ Phase 17 (gây vi phạm acceptance criterion).
- **Fix:** Thay placeholder Plans Phase 21 và Phase 22 bằng TBD note (`Plans sẽ được chốt khi /gsd-plan-phase 21 chạy`, tương tự Phase 22). KHÔNG fabricate plan content cho phases chưa được planned — chỉ remove placeholder sai.
- **Files modified:** .planning/ROADMAP.md (lines 173–179, 195–201)
- **Commit:** `12837ca`
- **Justification:** Out-of-scope cleanup nhưng cần để pass acceptance criterion. Không tạo content giả (placeholders thực sự là TBD vì plans chưa tồn tại).

**2. [Rule 3 — Tooling unavailable] mvn package skipped**
- **Found during:** Task 1 verification
- **Issue:** Plan verification yêu cầu `cd sources/backend/api-gateway && mvn -q -DskipTests package` nhưng `mvn` không có trong PATH của Windows env này (precedent: STATE.md Phase 19 Plan 01/02/03 đều note "Maven CLI không có trên Windows env này — defer cho /gsd-verify-work").
- **Fix:** Substituted YAML parse via Python `pyyaml` — `yaml.safe_load()` succeed, 26 routes parsed, 8 order-service* routes đúng thứ tự (specific TRƯỚC catch-all).
- **Files modified:** None (verification-only).
- **Commit:** N/A
- **Defer:** Maven build verify defer cho `/gsd-verify-work` hoặc khi spin docker-compose up cho Plan 20-05/20-06 smoke.

## Threat Surface Verification

Threat register T-20-04-01..04 đã được honor:
- T-20-04-01 (Spoofing pass-through): accept — auth gate ở downstream (order-svc JwtRoleGuard cho `/admin/coupons/**`)
- T-20-04-02 (Path tampering): mitigate — RewritePath named group `(?<seg>.*)` strict regex, KHÔNG inject
- T-20-04-04 (Routing misorder): mitigate — verify line numbers 113/119/127/133 < 141 (order-service-admin-base), pyyaml parse OK

## Self-Check: PASSED

- ✅ FOUND: sources/backend/api-gateway/src/main/resources/application.yml (4 route entries new)
- ✅ FOUND: .planning/ROADMAP.md (V5 + 6 plans)
- ✅ FOUND: commit d82a251 (Task 1)
- ✅ FOUND: commit 12837ca (Task 2)
- ✅ FOUND: SUMMARY.md (this file, post-creation)
