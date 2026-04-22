# Agent Operating Guide

## Purpose
- This file defines how AI agents should load context in this repository.
- Single source of truth for project context is `PROJECT_CONTEXT.md` at repository root.

## Required Read Order
For non-trivial tasks, agents are expected to read:
1. `PROJECT_CONTEXT.md`
2. `README.md`
3. `_index.json`

Then load domain docs by task type:
- Business intent: `strategy/`
- Use-case behavior and acceptance criteria: `ba/`
- System and service design: `architecture/`
- Implementation details and contracts: `technical-spec/`

## Domain Routing
- Auth/profile/user-admin tasks: prioritize user docs in all 4 layers.
- Catalog/review/admin-product tasks: prioritize product docs in all 4 layers.
- Cart/checkout/payment/order-admin tasks: prioritize order docs in all 4 layers.
- Frontend UI tasks: read `sources/frontend/AGENTS.md` before editing.
- If task intent/domain is ambiguous, ask clarification before selecting a layer/domain.

## Guardrails
- Do not create duplicate project context files.
- Keep references aligned with `_index.json` when adding or renaming docs.
- Prefer minimal structural changes unless explicitly requested.
