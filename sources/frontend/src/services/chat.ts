/**
 * Chat REST wrappers — typed thin client for Next.js BFF chat endpoints.
 *
 * Phase 22 Plan 05.
 *
 * NOTE on origin: `services/http.ts` targets `NEXT_PUBLIC_API_BASE_URL` which points
 * at the Spring api-gateway. The chat session/messages routes live on Next.js itself
 * (same origin as the page), so we use `window.fetch` against relative `/api/chat/*`
 * paths directly. The standard envelope `{ data: ... }` is preserved.
 *
 * Threat T-22-04 mitigated server-side (owner check inside the API route);
 * this client merely renders whatever the server returns.
 */
import { getAccessToken } from './token';

export interface ChatSessionDto {
  id: number;
  title: string;
  updatedAt: string;
}

export interface ChatMessageDto {
  role: 'user' | 'assistant';
  content: string;
}

interface Envelope<T> {
  timestamp?: string;
  status?: number;
  message?: string;
  data: T;
}

async function getJson<T>(path: string): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const res = await fetch(path, { headers });
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const env = (await res.json()) as Envelope<T>;
  return env.data;
}

export async function listChatSessions(limit = 20): Promise<ChatSessionDto[]> {
  const data = await getJson<{ content: ChatSessionDto[] }>(
    `/api/chat/sessions?limit=${limit}`,
  );
  return data?.content ?? [];
}

export async function getChatMessages(sessionId: number): Promise<ChatMessageDto[]> {
  const data = await getJson<{ content: ChatMessageDto[] }>(
    `/api/chat/sessions/${sessionId}/messages`,
  );
  return data?.content ?? [];
}
