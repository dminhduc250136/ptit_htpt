/**
 * Notification service API — thin wrapper.
 *
 * Per plan guidance: MVP does not show a notifications inbox to customers.
 * notification-service exposes dispatch/template admin endpoints; this module
 * is minimal and reserved for future wiring.
 *
 * Source: 04-RESEARCH.md §Pattern 2; Pitfall 7.
 */

// ===== NOTIFICATION SERVICE API =====

import type { paths as _NotificationsPaths } from '@/types/api/notifications.generated';
import { httpGet } from './http';

export type _PathsSurface = _NotificationsPaths;

/** List dispatched notifications (reserved — not used by MVP UAT path). */
export function listMyDispatches(): Promise<unknown> {
  return httpGet<unknown>(`/api/notifications/notifications/dispatches`);
}
