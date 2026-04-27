---
status: partial
phase: 09-residual-closure-verification
source: [09-VERIFICATION.md]
started: "2026-04-27T00:00:00Z"
updated: "2026-04-27T00:00:00Z"
---

## Current Test

[awaiting human testing — requires `docker compose up -d --build` stack]

## Tests

### 1. AUTH-06: Middleware redirect 4 routes (incognito browser)
expected: Truy cập /profile/orders (chưa login) → 307 redirect /login?returnTo=%2Fprofile%2Forders; /checkout → redirect /login; /admin → redirect /login; USER vào /admin → redirect /403
result: [pending]

### 2. AUTH-07: Stats endpoints curl 5 cases
expected: GET /api/products/admin/stats với admin Bearer → 200 {totalProducts:N}; admin orders → 200 {totalOrders:N, pendingOrders:M}; admin users → 200 {totalUsers:K}; USER token → 403; no header → 401
result: [pending]

### 3. AUTH-07: Password change curl 4 cases
expected: POST /api/users/me/password với đúng oldPassword → 200; sai oldPassword → 422 code=AUTH_INVALID_PASSWORD; no auth → 401; newPassword < 8 → 400
result: [pending]

### 4. UI-02: Admin dashboard + password form UI
expected: /admin shows 4 KPI cards với số thật (NOT 0); 1 service down → card shows '--' + retry; /profile/settings form 3 fields; sai oldPassword → field error "Mật khẩu hiện tại không đúng"; success → "Đã đổi mật khẩu" + form reset + KHÔNG logout
result: [pending]

### 5. TEST-01: Playwright full suite run
expected: `docker compose down -v && docker compose up -d --build && (cd sources/frontend && npx playwright test --reporter=list)` → 14 passed (0 failed)
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
