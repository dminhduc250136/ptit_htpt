import { chatPgPool } from './pg';
import type { ChatMessageRow } from './types';

/**
 * Raw pg queries for chat sessions/messages. Parameterized throughout — only column lists
 * and ORDER BY clauses are static literals. Owner-check inside listMessages mitigates T-22-04.
 */

export async function createSession(userId: string, title: string): Promise<number> {
  const safeTitle = Array.from(title).slice(0, 50).join('').trim() || 'Đoạn chat mới';
  const { rows } = await chatPgPool.query<{ id: string }>(
    `INSERT INTO chat_svc.chat_sessions (user_id, title) VALUES ($1, $2) RETURNING id`,
    [userId, safeTitle],
  );
  return Number(rows[0].id);
}

/**
 * Sliding window: last 20 messages for the session, returned in chronological order
 * (D-06 — keeps context window bounded as conversation grows).
 */
export async function loadHistory(sessionId: number): Promise<ChatMessageRow[]> {
  const { rows } = await chatPgPool.query<{ role: 'user' | 'assistant'; content: string }>(
    `SELECT role, content FROM chat_svc.chat_messages
     WHERE session_id = $1 ORDER BY created_at DESC LIMIT 20`,
    [sessionId],
  );
  return rows.reverse().map((r) => ({ role: r.role, content: r.content }));
}

export async function appendUserMessage(sessionId: number, content: string): Promise<void> {
  await chatPgPool.query(
    `INSERT INTO chat_svc.chat_messages (session_id, role, content) VALUES ($1, 'user', $2)`,
    [sessionId, content],
  );
}

export async function appendAssistantMessage(sessionId: number, content: string): Promise<void> {
  await chatPgPool.query(
    `INSERT INTO chat_svc.chat_messages (session_id, role, content) VALUES ($1, 'assistant', $2)`,
    [sessionId, content],
  );
}

export async function touchSession(sessionId: number): Promise<void> {
  await chatPgPool.query(
    `UPDATE chat_svc.chat_sessions SET updated_at = now() WHERE id = $1`,
    [sessionId],
  );
}

export async function listSessions(
  userId: string,
  limit: number,
  before?: string,
): Promise<Array<{ id: number; title: string; updatedAt: string }>> {
  const params: unknown[] = [userId];
  let where = 'user_id = $1';
  if (before) {
    params.push(before);
    where += ` AND updated_at < $${params.length}`;
  }
  params.push(Math.min(limit, 50));
  const { rows } = await chatPgPool.query<{ id: string; title: string; updated_at: string }>(
    `SELECT id, title, updated_at FROM chat_svc.chat_sessions
     WHERE ${where} ORDER BY updated_at DESC LIMIT $${params.length}`,
    params,
  );
  return rows.map((r) => ({ id: Number(r.id), title: r.title, updatedAt: r.updated_at }));
}

/**
 * Returns ALL messages of a session in chronological order. Throws 'NOT_FOUND' if session
 * absent, 'FORBIDDEN' if caller is not the owner (T-22-04 elevation-of-privilege guard).
 */
export async function listMessages(userId: string, sessionId: number): Promise<ChatMessageRow[]> {
  const owner = await chatPgPool.query<{ user_id: string }>(
    `SELECT user_id FROM chat_svc.chat_sessions WHERE id = $1`,
    [sessionId],
  );
  if (owner.rowCount === 0) throw new Error('NOT_FOUND');
  if (owner.rows[0].user_id !== userId) throw new Error('FORBIDDEN');
  const { rows } = await chatPgPool.query<{ role: 'user' | 'assistant'; content: string }>(
    `SELECT role, content FROM chat_svc.chat_messages
     WHERE session_id = $1 ORDER BY created_at ASC`,
    [sessionId],
  );
  return rows.map((r) => ({ role: r.role, content: r.content }));
}
