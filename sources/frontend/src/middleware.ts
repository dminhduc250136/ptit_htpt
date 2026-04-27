/**
 * Edge middleware — Phase 9 / Plan 09-01 (AUTH-06 closure).
 *
 * D-01: file root `middleware.ts` bị xóa (stale duplicate).
 * D-02: matcher mở rộng cover /admin, /account, /profile, /checkout.
 * D-03: giữ logic /403 redirect cho non-ADMIN tại /admin/* (Phase 6 D-09).
 *
 * NOTE: Edge runtime KHÔNG verify JWT signature — `auth_present` chỉ
 * UX presence-check; `user_role` cookie cho admin gating UX.
 * Bảo mật thật ở backend qua Authorization: Bearer header.
 */
import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(req: NextRequest) {
  const { pathname } = req.nextUrl;
  const authPresent = req.cookies.get('auth_present')?.value;

  if (!authPresent) {
    const url = req.nextUrl.clone();
    url.pathname = '/login';
    url.searchParams.set('returnTo', pathname + req.nextUrl.search);
    return NextResponse.redirect(url);
  }

  if (pathname.startsWith('/admin')) {
    const userRole = req.cookies.get('user_role')?.value;
    if (!userRole?.includes('ADMIN')) {
      const url = req.nextUrl.clone();
      url.pathname = '/403';
      return NextResponse.redirect(url);
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/admin/:path*', '/account/:path*', '/profile/:path*', '/checkout/:path*'],
};
