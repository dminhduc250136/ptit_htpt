import { verifyJwtFromRequest } from '@/lib/chat/auth';
import { ensureSchema } from '@/lib/chat/schema-init';
import { listSessions } from '@/lib/chat/messages-repo';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function ok<T>(data: T): Response {
  return new Response(
    JSON.stringify({
      timestamp: new Date().toISOString(),
      status: 200,
      message: 'OK',
      data,
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}

function err(status: number, code: string, message: string): Response {
  return new Response(
    JSON.stringify({ status, code, message, error: code }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

export async function GET(req: Request): Promise<Response> {
  let claims;
  try {
    claims = await verifyJwtFromRequest(req);
  } catch {
    return err(401, 'AUTH_FAILED', 'Phiên đăng nhập không hợp lệ');
  }

  try {
    await ensureSchema();
  } catch {
    return err(500, 'DB_INIT_FAILED', 'Lỗi khởi tạo lưu trữ chat');
  }

  const url = new URL(req.url);
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') ?? 20), 1), 50);
  const before = url.searchParams.get('before') ?? undefined;

  try {
    const sessions = await listSessions(claims.userId, limit, before);
    return ok({ content: sessions, limit, before: before ?? null });
  } catch (e) {
    const msg = e instanceof Error ? e.message : 'unknown';
    return err(500, 'INTERNAL_ERROR', `Không thể tải sessions: ${msg}`);
  }
}
