/**
 * Phase 19 / Plan 19-04 — Chart formatting helpers.
 * D-12: semantic status colors. D-13: Vietnamese labels + Intl formatters.
 */

export const STATUS_COLORS: Record<string, string> = {
  PENDING: '#f59e0b', // D-12
  CONFIRMED: '#3b82f6',
  SHIPPED: '#06b6d4',
  DELIVERED: '#10b981',
  CANCELLED: '#dc2626',
};

export function statusLabel(s: string): string {
  return (
    {
      PENDING: 'Chờ xử lý', // D-13
      CONFIRMED: 'Đã xác nhận',
      SHIPPED: 'Đang giao',
      DELIVERED: 'Đã giao',
      CANCELLED: 'Đã huỷ',
    } as Record<string, string>
  )[s] ?? s;
}

export const vnNumber = (n: number) => new Intl.NumberFormat('vi-VN').format(n);

export const vnDate = (iso: string) => {
  const d = new Date(iso);
  return new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit' }).format(d);
};
