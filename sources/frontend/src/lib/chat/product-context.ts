import { normalizeVn, escapeXml } from './vn-text';
import type { ChatProduct } from './types';

const GATEWAY = process.env.API_GATEWAY_URL ?? 'http://api-gateway:8080';

/**
 * Keyword search via product-service REST (D-18, D-16). Returns up to 5 products.
 * Falls back to "recently updated" feed if keyword search returns < 3 hits, so the
 * model always has at least some catalog grounding for non-product chitchat.
 *
 * T-22-08 mitigation: keyword goes through encodeURIComponent — no raw SQL from user input.
 */
export async function searchProductsForContext(userMessage: string): Promise<ChatProduct[]> {
  const norm = normalizeVn(userMessage);
  const tokens = norm.split(/\s+/).filter((t) => t.length >= 2).slice(0, 6);
  const keyword = tokens.join(' ');
  const baseUrl = `${GATEWAY}/api/products`;
  const searchUrl = `${baseUrl}?keyword=${encodeURIComponent(keyword)}&size=5`;

  let products: ChatProduct[] = [];
  try {
    const res = await fetch(searchUrl, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
    if (res.ok) {
      const env = await res.json();
      products = (env?.data?.content ?? []).map(mapProduct);
    }
  } catch {
    /* fall through to fallback */
  }

  if (products.length < 3) {
    try {
      const fbRes = await fetch(`${baseUrl}?size=5&sort=updatedAt,desc`, { cache: 'no-store' });
      if (fbRes.ok) {
        const fbEnv = await fbRes.json();
        products = (fbEnv?.data?.content ?? []).slice(0, 5).map(mapProduct);
      }
    } catch {
      /* return whatever we have */
    }
  }
  return products;
}

function mapProduct(p: Record<string, unknown>): ChatProduct {
  return {
    id: String(p.id ?? ''),
    name: String(p.name ?? ''),
    price: Number(p.price ?? 0),
    brand: p.brand != null ? String(p.brand) : null,
    stock: p.stock != null ? Number(p.stock) : null,
  };
}

/**
 * Render product list as XML attribute-only tags. All user-visible string fields are
 * escapeXml()'d to prevent prompt-injection through product names (T-22-02).
 */
export function buildContextXml(products: ChatProduct[]): string {
  return products
    .map(
      (p) =>
        `<product id="${escapeXml(p.id)}" name="${escapeXml(p.name)}" price="${p.price}" brand="${escapeXml(p.brand ?? '')}" stock="${p.stock ?? 0}"/>`,
    )
    .join('\n');
}
