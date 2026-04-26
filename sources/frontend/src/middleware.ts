import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (pathname.startsWith('/admin')) {
    const authPresent = request.cookies.get('auth_present')?.value;
    const userRole = request.cookies.get('user_role')?.value;

    if (!authPresent) {
      const url = request.nextUrl.clone();
      url.pathname = '/login';
      url.searchParams.set('returnTo', pathname);
      return NextResponse.redirect(url);
    }

    if (userRole !== 'ADMIN') {
      const url = request.nextUrl.clone();
      url.pathname = '/403';
      return NextResponse.redirect(url);
    }
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/admin/:path*'],
};
