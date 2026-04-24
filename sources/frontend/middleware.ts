/**
 * Route protection middleware — presence-check only.
 *
 * Source: 04-RESEARCH.md §Pattern 4 + Next.js 16 file-conventions docs.
 *
 * T-04-02 mitigation: matcher enumerates EXACTLY /checkout, /profile, /admin
 * prefixes — no wildcard / whitelist logic. Public routes (/, /products, /login,
 * /register, /cart, /search) are intentionally not matched.
 *
 * Deprecation note: Next.js 16 renamed this convention to proxy.ts and emits a
 * deprecation warning; keeping middleware.ts per D-12 and Pitfall 6. The rename
 * is a Phase-5+ follow-up (`npx @next/codemod@canary middleware-to-proxy .`).
 *
 * NOTE: presence check only. Backend still validates the JWT on every call via
 * the Authorization: Bearer header — this middleware never reads or validates
 * the token itself.
 */

import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const authPresent = req.cookies.get('auth_present')?.value;
  if (!authPresent) {
    const returnTo = encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search);
    const loginUrl = new URL(`/login?returnTo=${returnTo}`, req.url);
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*'],
};
