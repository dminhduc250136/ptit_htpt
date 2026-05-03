/**
 * Strip Vietnamese diacritics + lowercase to produce keyword-friendly tokens.
 * Example: "Điện thoại Iphone" → "dien thoai iphone"
 */
export function normalizeVn(s: string): string {
  return s
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/đ/g, 'd')
    .trim();
}

/**
 * Escape user-supplied strings before embedding inside XML attributes / elements
 * passed to Claude. Mitigates T-22-02 (prompt-injection via product names / messages).
 */
export function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
}
