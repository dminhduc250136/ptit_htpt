/**
 * In-memory sliding window rate limiter (D-24): 20 messages / 5 minutes per userId.
 * Single-instance only — sufficient for MVP. Accept threat T-22-06 (no Redis cluster yet).
 */
const limits = new Map<string, number[]>();
const WINDOW_MS = 5 * 60 * 1000;
const MAX = 20;

export function checkRateLimit(userId: string): boolean {
  const now = Date.now();
  const arr = (limits.get(userId) ?? []).filter((t) => now - t < WINDOW_MS);
  if (arr.length >= MAX) {
    limits.set(userId, arr);
    return false;
  }
  arr.push(now);
  limits.set(userId, arr);
  return true;
}
