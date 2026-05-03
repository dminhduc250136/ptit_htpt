import { anthropicClient, SYSTEM_PROMPT_VN } from '@/lib/chat/anthropic';
import { verifyJwtFromRequest } from '@/lib/chat/auth';
import { checkRateLimit } from '@/lib/chat/rate-limit';
import { ensureSchema } from '@/lib/chat/schema-init';
import { searchProductsForContext, buildContextXml } from '@/lib/chat/product-context';
import {
  createSession,
  loadHistory,
  appendUserMessage,
  appendAssistantMessage,
  touchSession,
} from '@/lib/chat/messages-repo';
import { escapeXml } from '@/lib/chat/vn-text';

// CRITICAL: pg + long-lived stream KHÔNG tương thích Edge runtime (Pitfall 1).
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const MAX_INPUT_LEN = 2000; // D-25
const MAX_TOKENS = 1024; // D-25

interface ChatStreamBody {
  sessionId?: number | null;
  message?: string;
}

function jsonError(status: number, code: string, message: string): Response {
  return new Response(JSON.stringify({ status, code, message }), {
    status,
    headers: { 'Content-Type': 'application/json; charset=utf-8' },
  });
}

export async function POST(req: Request): Promise<Response> {
  // 1) Auth (T-22-03 chỉ allow Bearer JWT hợp lệ)
  let claims;
  try {
    claims = await verifyJwtFromRequest(req);
  } catch {
    return jsonError(401, 'AUTH_FAILED', 'Phiên đăng nhập không hợp lệ');
  }

  // 2) Rate limit BEFORE any expensive work (T-22-06)
  if (!checkRateLimit(claims.userId)) {
    return jsonError(429, 'RATE_LIMITED', 'Bạn nhắn quá nhanh, thử lại sau ít phút');
  }

  // 3) Body parse + validate
  let body: ChatStreamBody;
  try {
    body = (await req.json()) as ChatStreamBody;
  } catch {
    return jsonError(400, 'INVALID_BODY', 'Body không hợp lệ');
  }
  const message = (body.message ?? '').trim();
  if (!message) return jsonError(400, 'EMPTY_MESSAGE', 'Tin nhắn không được rỗng');
  if (message.length > MAX_INPUT_LEN) {
    return jsonError(400, 'MESSAGE_TOO_LONG', `Tin nhắn vượt quá ${MAX_INPUT_LEN} ký tự`);
  }

  // 4) Ensure schema (idempotent, no-op sau lần boot đầu)
  try {
    await ensureSchema();
  } catch {
    return jsonError(500, 'DB_INIT_FAILED', 'Không thể khởi tạo lưu trữ chat');
  }

  // 5) Resolve session: tạo mới nếu chưa có (auto-title 50 codepoints đầu)
  let sessionId = typeof body.sessionId === 'number' ? body.sessionId : null;
  if (sessionId == null) {
    const title = Array.from(message).slice(0, 50).join('');
    try {
      sessionId = await createSession(claims.userId, title);
    } catch {
      return jsonError(500, 'DB_WRITE_FAILED', 'Không thể tạo phiên chat mới');
    }
  }

  // 6) Persist user message TRƯỚC khi stream (durable kể cả khi Anthropic fail)
  try {
    await appendUserMessage(sessionId, message);
  } catch {
    return jsonError(500, 'DB_WRITE_FAILED', 'Không thể lưu tin nhắn');
  }

  // 7) Load sliding window history (20 messages = ~10 turns), rồi product context
  const history = await loadHistory(sessionId);
  const products = await searchProductsForContext(message);
  const ctxXml = buildContextXml(products);
  const userBlock =
    `<product_context>\n${ctxXml}\n</product_context>\n` +
    `<user_question>${escapeXml(message)}</user_question>`;

  // 8) historyForApi DROP trailing entry: loadHistory gọi SAU appendUserMessage (step 6)
  //    nên message cuối cùng trong history chính là tin nhắn user vừa lưu — chúng ta sẽ
  //    re-inject phiên bản đã wrap product_context làm turn cuối.
  const historyForApi = history.slice(0, -1).map((h) => ({
    role: h.role,
    content: h.content,
  }));

  // 9) Stream từ Anthropic + forward về client (newline-delimited JSON, T-22-07 abort wired)
  const encoder = new TextEncoder();
  const abortController = new AbortController();
  req.signal.addEventListener('abort', () => abortController.abort());

  const sessionIdNum = sessionId; // capture cho closure

  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const send = (obj: unknown) =>
        controller.enqueue(encoder.encode(JSON.stringify(obj) + '\n'));
      let assistantText = '';
      try {
        const messageStream = anthropicClient.messages.stream(
          {
            model: 'claude-haiku-4-5',
            max_tokens: MAX_TOKENS,
            system: [
              {
                type: 'text',
                text: SYSTEM_PROMPT_VN,
                cache_control: { type: 'ephemeral' }, // D-04 prompt caching
              },
            ],
            messages: [
              ...historyForApi,
              {
                role: 'user',
                content: [
                  {
                    type: 'text',
                    text: userBlock,
                    cache_control: { type: 'ephemeral' }, // D-04
                  },
                ],
              },
            ],
          },
          { signal: abortController.signal },
        );

        for await (const event of messageStream) {
          if (
            event.type === 'content_block_delta' &&
            event.delta.type === 'text_delta'
          ) {
            assistantText += event.delta.text;
            send({ type: 'delta', text: event.delta.text });
          }
        }

        const finalMessage = await messageStream.finalMessage();
        await appendAssistantMessage(sessionIdNum, assistantText);
        await touchSession(sessionIdNum);
        console.log(
          `[chat] session=${sessionIdNum} usage input=${finalMessage.usage.input_tokens} ` +
            `cache_read=${finalMessage.usage.cache_read_input_tokens ?? 0} ` +
            `cache_write=${finalMessage.usage.cache_creation_input_tokens ?? 0} ` +
            `output=${finalMessage.usage.output_tokens}`,
        );
        send({ type: 'done', sessionId: sessionIdNum, usage: finalMessage.usage });
        controller.close();
      } catch (err) {
        const msg = err instanceof Error ? err.message : 'stream_failed';
        console.warn('[chat] stream error', msg);
        send({ type: 'error', error: msg, sessionId: sessionIdNum });
        controller.close();
      }
    },
    cancel() {
      console.log(`[chat] abort signal received session=${sessionIdNum}`);
      abortController.abort();
    },
  });

  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no',
    },
  });
}
