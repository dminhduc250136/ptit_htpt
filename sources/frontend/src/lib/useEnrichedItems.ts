// src/lib/useEnrichedItems.ts
// Phase 17 / D-01: parallel fetch product detail (image+brand) cho mỗi unique
// productId trong order.items. Dùng Promise.allSettled để 1 product 404 không
// kill toàn bộ render. Cleanup `cancelled` flag để tránh setState after unmount.
'use client';

import { useEffect, useState } from 'react';
import { getProductById } from '@/services/products';
import type { OrderItem } from '@/types';

export type EnrichedItem = OrderItem & {
  thumbnailUrl?: string;
  brand?: string;
};

type EnrichmentMap = Record<string, { thumbnailUrl?: string; brand?: string }>;

export function useEnrichedItems(items: OrderItem[] | undefined): EnrichedItem[] {
  const [map, setMap] = useState<EnrichmentMap>({});

  useEffect(() => {
    if (!items?.length) return;
    const uniqueIds = [...new Set(items.map(i => i.productId))];
    let cancelled = false;
    Promise.allSettled(uniqueIds.map(id => getProductById(id))).then(results => {
      if (cancelled) return;
      const next: EnrichmentMap = {};
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value) {
          next[uniqueIds[i]] = {
            thumbnailUrl: r.value.thumbnailUrl,
            brand: r.value.brand,
          };
        }
      });
      setMap(next);
    });
    return () => { cancelled = true; };
  }, [items]);

  return (items ?? []).map(it => ({ ...it, ...map[it.productId] }) as EnrichedItem);
}
