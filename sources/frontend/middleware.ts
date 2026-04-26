/**
 * Route protection middleware — presence-check + admin role check.
 *
 * Source: 04-RESEARCH.md §Pattern 4 + Phase 6 D-07/D-08/D-09.
 *
 * D-07: matcher thêm /account/:path*
 * D-08: admin role check đọc user_role cookie (non-httpOnly, set khi login)
 * D-09: non-ADMIN truy cập /admin/* → redirect /403
 * D-10: unauthenticated → redirect /login?returnTo=<path>
 *
 * NOTE: Edge Runtime không thể verify JWT signature — dùng user_role cookie
 * approach (D-08). Cookie chỉ cho UX redirect, không phải security enforcement thật.
 * Backend vẫn validate JWT trên mỗi API call qua Authorization: Bearer header.
 */

import { NextRequest, NextResponse } from 'next/server';

export function middleware(req: NextRequest) {
  const authPresent = req.cookies.get('auth_present')?.value;
  if (!authPresent) {
    const returnTo = encodeURIComponent(req.nextUrl.pathname + req.nextUrl.search);
    const loginUrl = new URL(`/login?returnTo=${returnTo}`, req.url);
    return NextResponse.redirect(loginUrl);
  }

  // Admin route check — Edge Runtime cannot verify JWT signature (D-08).
  // user_role cookie set at login by token.ts setUserRole().
  if (req.nextUrl.pathname.startsWith('/admin')) {
    const userRole = req.cookies.get('user_role')?.value;
    // includes() safe cho "ADMIN" single value và "ADMIN,USER" comma-separated
    if (!userRole?.includes('ADMIN')) {
      return NextResponse.redirect(new URL('/403', req.url));
    }
  }

  return NextResponse.next();
}

export const config = {
  // D-07: thêm /account/:path*
  matcher: ['/checkout/:path*', '/profile/:path*', '/admin/:path*', '/account/:path*'],
};
