import { chatPgPool } from './pg';

let initialized = false;
let initPromise: Promise<void> | null = null;

/**
 * Idempotent schema init for chat_svc. Once-per-process: subsequent calls return immediately
 * after first successful run. Uses to_regclass guard so re-running on existing schema costs
 * one query, not full DDL execution. user_id is VARCHAR(36) to match user_svc.users.id (D-19).
 */
export async function ensureSchema(): Promise<void> {
  if (initialized) return;
  if (initPromise) return initPromise;
  initPromise = (async () => {
    const exists = await chatPgPool.query<{ exists: string | null }>(
      `SELECT to_regclass('chat_svc.chat_sessions') AS exists`,
    );
    if (exists.rows[0].exists) {
      initialized = true;
      return;
    }
    await chatPgPool.query(`
      CREATE SCHEMA IF NOT EXISTS chat_svc;
      CREATE TABLE IF NOT EXISTS chat_svc.chat_sessions (
        id BIGSERIAL PRIMARY KEY,
        user_id VARCHAR(36) NOT NULL,
        title VARCHAR(200),
        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_updated
        ON chat_svc.chat_sessions (user_id, updated_at DESC);
      CREATE TABLE IF NOT EXISTS chat_svc.chat_messages (
        id BIGSERIAL PRIMARY KEY,
        session_id BIGINT NOT NULL REFERENCES chat_svc.chat_sessions(id) ON DELETE CASCADE,
        role VARCHAR(16) NOT NULL CHECK (role IN ('user','assistant')),
        content TEXT NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT now()
      );
      CREATE INDEX IF NOT EXISTS idx_chat_messages_session_created
        ON chat_svc.chat_messages (session_id, created_at);
    `);
    initialized = true;
  })();
  await initPromise;
}
