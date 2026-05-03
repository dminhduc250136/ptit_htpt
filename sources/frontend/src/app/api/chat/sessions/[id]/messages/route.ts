import { verifyJwtFromRequest } from '@/lib/chat/auth';
import { ensureSchema } from '@/lib/chat/schema-init';
import { listMessages } from '@/lib/chat/messages-repo';

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

export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
): Promise<Response> {
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

  const { id } = await params;
  const sessionId = Number(id);
  if (!Number.isFinite(sessionId) || sessionId <= 0) {
    return err(400, 'INVALID_SESSION_ID', 'sessionId không hợp lệ');
  }

  try {
    const messages = await listMessages(claims.userId, sessionId);
    return ok({ sessionId, content: messages });
  } catch (e) {
    const msg = e instanceof Error ? e.message : 'unknown';
    if (msg === 'FORBIDDEN') {
      return err(403, 'FORBIDDEN', 'Không có quyền truy cập session này');
    }
    if (msg === 'NOT_FOUND') {
      return err(404, 'NOT_FOUND', 'Session không tồn tại');
    }
    return err(500, 'INTERNAL_ERROR', `Không thể tải lịch sử: ${msg}`);
  }
}
