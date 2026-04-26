---
phase: 7
slug: search-admin-real-data
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-26
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Maven (Spring Boot Test) + Next.js build check |
| **Config file** | `sources/backend/*/pom.xml` / `sources/frontend/package.json` |
| **Quick run command** | `cd sources/frontend && npm run build` |
| **Full suite command** | `cd sources/backend/product-service && mvn test -q && cd ../user-service && mvn test -q` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd sources/frontend && npm run build`
- **After every plan wave:** Run full suite command (backend unit tests + FE build)
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 7-01-01 | 01 | 1 | UI-01 | — | N/A | manual | `curl "http://localhost:8080/api/products?keyword=test"` | ✅ exists | ⬜ pending |
| 7-02-01 | 02 | 1 | UI-01..04 | — | N/A | build | `cd sources/backend/api-gateway && mvn spring-boot:run -q` | ✅ exists | ⬜ pending |
| 7-03-01 | 03 | 2 | UI-03 | — | N/A | manual | `curl http://localhost:8080/api/orders/admin/orders` | ❌ W0 | ⬜ pending |
| 7-04-01 | 04 | 2 | UI-04 | — | N/A | unit | `cd sources/backend/user-service && mvn test -q` | ✅ exists | ⬜ pending |
| 7-05-01 | 05 | 3 | UI-02..04 | — | N/A | build | `cd sources/frontend && npm run build` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- Existing infrastructure covers all phase requirements (Spring Boot Test + Next.js build).
- No new test files required before execution.

*Wave 0 có thể bổ sung nếu planner thấy cần unit test cho ProductCrudService keyword search.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Search `/search?q=keyword` render kết quả thật | UI-01 | Cần browser + running stack | Vào `/search`, nhập keyword, verify results không phải mock |
| Admin tạo/edit/xóa product | UI-02 | UI interaction + modal | Login admin, vào `/admin/products`, test CRUD flow |
| Admin orders detail page + status update | UI-03 | Navigation + PATCH flow | Login admin, click order row → detail page → đổi status |
| Admin users list + edit modal | UI-04 | UI interaction + PATCH | Login admin, vào `/admin/users`, test edit fullName/phone/roles |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
