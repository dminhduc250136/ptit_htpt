// src/lib/orderLabels.ts
// Phase 17 / D-04: extract Vietnamese label maps để reuse cho admin + user order pages.
// Source verbatim từ profile/orders/[id]/page.tsx:13-32.

export const statusMap: Record<
  string,
  { label: string; variant: 'default' | 'new' | 'hot' | 'sale' | 'out-of-stock' }
> = {
  PENDING:   { label: 'Chờ xác nhận', variant: 'default' },
  CONFIRMED: { label: 'Đã xác nhận',  variant: 'new' },
  SHIPPING:  { label: 'Đang giao',    variant: 'hot' },
  DELIVERED: { label: 'Đã giao',      variant: 'sale' },
  CANCELLED: { label: 'Đã hủy',       variant: 'out-of-stock' },
};

export const paymentMethodMap: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  BANK_TRANSFER: 'Chuyển khoản ngân hàng',
  E_WALLET: 'Ví điện tử',
};

export const paymentStatusMap: Record<string, string> = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  FAILED: 'Thanh toán thất bại',
  REFUNDED: 'Đã hoàn tiền',
};
