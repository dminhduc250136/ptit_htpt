import { Pool } from 'pg';

const globalForPg = globalThis as unknown as { _chatPgPool?: Pool };

export const chatPgPool: Pool =
  globalForPg._chatPgPool ??
  new Pool({
    host: process.env.DB_HOST ?? 'postgres',
    port: Number(process.env.DB_PORT ?? 5432),
    database: process.env.DB_NAME ?? 'tmdt',
    user: process.env.DB_USER ?? 'tmdt',
    password: process.env.DB_PASSWORD ?? 'tmdt',
    max: 10,
    idleTimeoutMillis: 30_000,
  });

if (process.env.NODE_ENV !== 'production') globalForPg._chatPgPool = chatPgPool;
