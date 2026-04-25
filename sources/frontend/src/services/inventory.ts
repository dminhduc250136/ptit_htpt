/**
 * Inventory service API — read-only stock queries.
 *
 * Per plan guidance: MVP does not need live stock display; order-service returns
 * STOCK_SHORTAGE on CONFLICT during checkout and that drives the modal recovery
 * flow. This module exposes a minimal read endpoint for future use.
 *
 * Source: 04-RESEARCH.md §Pattern 2; Pitfall 7.
 */

// ===== INVENTORY SERVICE API =====

import type { paths as _InventoryPaths } from '@/types/api/inventory.generated';
import { httpGet } from './http';

export type _PathsSurface = _InventoryPaths;

/** List inventory items (reserved — not used by MVP UAT path). */
export function listInventoryItems(): Promise<unknown> {
  return httpGet<unknown>(`/api/inventory/inventory/items`);
}
