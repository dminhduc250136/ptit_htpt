---
phase: "01"
status: human_needed
updated: "2026-04-22"
---

# Phase 01 — Verification

## Status

Implementation work for Phase 01 is complete, but **human verification is still required** (runtime curl checks + Swagger UI smoke checks).

## What was implemented (evidence in commits + summaries)

- `01-01`: Standard success envelope + error schema + trace-id propagation + smoke endpoints in all services
- `01-02`: Springdoc OpenAPI enabled in all services (`/v3/api-docs`, `/swagger-ui.html`)
- `01-03`: Gateway Swagger UI lists each service spec + gateway request-id propagation + gateway-local error schema

See:
- `.planning/phases/01/01-01-SUMMARY.md`
- `.planning/phases/01/01-02-SUMMARY.md`
- `.planning/phases/01/01-03-SUMMARY.md`

## Human verification checklist (run locally)

### Gate A — Swagger/OpenAPI reachable everywhere (API-01)

- For each service (direct):
  - `GET /v3/api-docs` returns JSON
  - `GET /swagger-ui.html` returns 200/302
- For gateway:
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

- Confirm conventions exist in:
  - `.planning/phases/01/PLAN.md` and/or `.planning/phases/01/01-01-PLAN.md`

