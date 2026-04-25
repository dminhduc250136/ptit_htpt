# Milestone v1.0 Requirements — MVP Stabilization

## v1.0 Requirements

### API Contract & Documentation

- [x] **API-01**: Every service exposes Swagger/OpenAPI docs in a consistent way (including auth where applicable).
- [x] **API-02**: API Gateway publishes an aggregated or discoverable OpenAPI surface for frontend integration.
- [x] **API-03**: Standardize response envelope and error format across services (error code, message, field errors, trace id).
- [x] **API-04**: Standardize HTTP status code usage across services (success, validation, auth, not-found, conflict).

### CRUD Completeness

- [x] **CRUD-01**: User Service CRUD endpoints are complete and follow the standard contract patterns.
- [x] **CRUD-02**: Product Service CRUD endpoints are complete (including admin operations) and follow the standard contract patterns.
- [x] **CRUD-03**: Order Service CRUD endpoints are complete (cart + order lifecycle) and follow the standard contract patterns.
- [x] **CRUD-04**: Payment Service endpoints are complete for the assignment scope (mock payment is acceptable) and follow the standard contract patterns.
- [x] **CRUD-05**: Inventory Service endpoints are complete and follow the standard contract patterns.
- [x] **CRUD-06**: Notification Service endpoints are complete (dispatch + templates/config as applicable) and follow the standard contract patterns.

### Validation & Error Handling

- [ ] **VAL-01**: Input validation is enforced consistently (request DTO validation, field-level messages).
- [ ] **VAL-02**: Global exception handling is consistent and maps to the standardized error format.
- [ ] **VAL-03**: Auth/authz errors are consistent across gateway and services.

### Frontend ↔ Backend Alignment

- [ ] **FE-01** (status: needs-rework): Frontend API client aligns to the documented contracts (URLs, DTOs, status codes, error format).
  - Compile-time: **achieved** Wave 1 (04-01) — openapi-typescript codegen + httpGet/Post wrappers + 6 generated `.generated.ts` files compile clean.
  - Runtime: **NOT aligned** — UAT (04-03) surfaced 3 concrete gaps: (1) backend `product-service` returns thin Product DTO while FE `ProductCard` consumes rich DTO with `category.name`/`thumbnailUrl`/`rating`/etc.; (2) backend `order-service.createOrder` requires `userId`+`status` (raw entity Upsert) while FE sends domain-command shape; (3) backend `/api/products/products/slug/{slug}` returns HTTP 500. See `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-03-SUMMARY.md` §FE-01 gaps surfaced. Closure planned via Phase 4.1.
- [x] **FE-02** (status: met): Checkout and cart flows handle error cases gracefully (validation, stock, payment failure, auth).
  - All 5 dispatcher branches verified per UAT (04-03): B1 real-backend `VALIDATION_ERROR` → Banner ✓; B2 stubbed CONFLICT/STOCK_SHORTAGE → Stock modal + Cập nhật/Xóa khỏi giỏ ✓; B3 stubbed CONFLICT/PAYMENT → Payment modal + Thử lại/Đổi phương thức ✓; B4a stubbed 401 → silent redirect + clearTokens (T-04-04) ✓; B5 stubbed 502 → Toast + no auto-retry on POST (D-10) ✓. Open-redirect guard verified end-to-end (B4b real, T-04-03). T-04-02/03/04 verified. See `04-03-SUMMARY.md` §FE-02 verified.

## Future (Deferred)

- [ ] **TEST-01**: Broad integration test suite across all services
- [ ] **OBS-01**: Centralized tracing/log correlation across services

## Out of Scope (for v1.0)

- Real payment gateway integration — mock is sufficient for assignment
- Production-grade infra and HA — Docker Compose local is sufficient

## Traceability (filled by roadmap)

| Requirement | Phase |
|------------|-------|
| API-01 | Phase 1 |
| API-02 | Phase 1 |
| API-03 | Phase 1 |
| API-04 | Phase 1 |
| CRUD-01 | Phase 2 |
| CRUD-02 | Phase 2 |
| CRUD-03 | Phase 2 |
| CRUD-04 | Phase 2 |
| CRUD-05 | Phase 2 |
| CRUD-06 | Phase 2 |
| VAL-01 | Phase 3 |
| VAL-02 | Phase 3 |
| VAL-03 | Phase 3 |
| FE-01 | Phase 4 |
| FE-02 | Phase 4 |

