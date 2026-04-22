# Phase 01 — Validation (Nyquist)

This file exists because `.planning/config.json` enables `"workflow.nyquist_validation": true`.

## Validation gates (must pass before Phase 1 is “done”)

### Gate A — Swagger/OpenAPI reachable everywhere (API-01)
- For each service (direct):
  - `GET /v3/api-docs` returns JSON
  - `GET /swagger-ui.html` returns 200/302
- For gateway (entrypoint):
  - `GET http://localhost:8080/swagger-ui.html` returns 200/302
  - For each service prefix:
    - `GET http://localhost:8080/api/<prefix>/v3/api-docs` returns JSON

### Gate B — Error schema consistency (API-03)
- For each service (direct) using smoke endpoints:
  - `POST /__contract/validate` with `{}` returns 400 with:
    - `code=VALIDATION_ERROR`
    - non-empty `fieldErrors[]`
    - `traceId` populated when `X-Request-Id` is provided
- Gateway-local error:
  - `GET http://localhost:8080/api/does-not-exist/foo` returns standard error schema with `code=NOT_FOUND`

### Gate C — Success envelope consistency baseline (API-03)
- For each service:
  - `GET /__contract/ping` returns the success envelope and does **not** break:
    - `/actuator/**`
    - `/v3/api-docs/**`
    - `/swagger-ui/**` and `/swagger-ui.html`

### Gate D — Status code conventions documented (API-04)
- Confirm the conventions exist in Phase 1 docs:
  - `.planning/phases/01/PLAN.md` (overview) or `01-01-PLAN.md`

## Evidence to capture (suggested)
- Save curl outputs (headers + body) for:
  - one service direct `__contract/validate` (with and without `X-Request-Id`)
  - one service direct `/v3/api-docs`
  - gateway `/swagger-ui.html`
  - gateway `/api/users/v3/api-docs`
  - gateway route-miss error response
