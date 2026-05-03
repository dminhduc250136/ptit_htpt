import { jwtVerify } from 'jose';

const SECRET = new TextEncoder().encode(
  process.env.JWT_SECRET ?? 'dev-jwt-secret-key-minimum-32-characters-for-hs256-ok',
);

export interface JwtClaims {
  userId: string;
  roles: string[];
}

/**
 * Verify Bearer JWT (HS256) from Authorization header. Throws 'AUTH_MISSING' or 'AUTH_INVALID'.
 * Roles claim accepted as string array OR comma-separated string (matches user-service convention).
 */
export async function verifyJwtFromRequest(req: Request): Promise<JwtClaims> {
  const auth = req.headers.get('authorization');
  if (!auth?.startsWith('Bearer ')) throw new Error('AUTH_MISSING');
  const token = auth.slice(7);
  const { payload } = await jwtVerify(token, SECRET, { algorithms: ['HS256'] });
  const userId = String(payload.sub ?? '');
  if (!userId) throw new Error('AUTH_INVALID');
  const rolesRaw = (payload as Record<string, unknown>).roles ?? '';
  const roles = Array.isArray(rolesRaw)
    ? rolesRaw.map(String)
    : String(rolesRaw)
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
  return { userId, roles };
}

export function requireAdmin(claims: JwtClaims): void {
  if (!claims.roles.includes('ADMIN')) throw new Error('FORBIDDEN');
}
