This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.

## Phase 4 Dev Workflow

### Prerequisites
- Node.js ≥ 18.17
- Docker Desktop (for running all 6 backend services)
- Backend stack running locally: `docker compose up -d` from repo root

### Environment
Copy `.env.example` to `.env.local`:
```bash
cp .env.example .env.local
# Edit NEXT_PUBLIC_API_BASE_URL if your gateway runs on a non-default host/port
```

### Regenerate API types
The `services/*` modules depend on auto-generated TypeScript types from each backend service's `/v3/api-docs`. When a backend DTO changes, regenerate:
```bash
# 1. Ensure all 6 services are healthy
curl http://localhost:8081/v3/api-docs   # users
curl http://localhost:8082/v3/api-docs   # products
curl http://localhost:8083/v3/api-docs   # orders
curl http://localhost:8084/v3/api-docs   # payments
curl http://localhost:8085/v3/api-docs   # inventory
curl http://localhost:8086/v3/api-docs   # notifications

# 2. Run codegen
npm run gen:api

# 3. Commit generated files
git add src/types/api/*.generated.ts
git commit -m "chore(frontend): regenerate API types"
```

### Route protection
`middleware.ts` at the project root protects these paths:
- `/checkout/*`
- `/profile/*`
- `/admin/*`

It checks for the `auth_present` cookie (set on login, cleared on logout or 401 from services/http.ts). JWT validation happens on the backend; the middleware is a presence-check only.

### Error handling contract
- `VALIDATION_ERROR` (400) → inline field errors + top banner
- `UNAUTHORIZED` (401) → silent redirect to `/login?returnTo=<path>`
- `FORBIDDEN` (403) → toast "Bạn không có quyền"
- `CONFLICT` (409) → recovery modal (stock shortage or payment failure)
- `NOT_FOUND` (404) → context-dependent (empty list or 404 page)
- `INTERNAL_ERROR` / 5xx / network → toast + inline RetrySection

See `.planning/phases/04-frontend-contract-alignment-e2e-validation/04-UI-SPEC.md` for UI contracts.
