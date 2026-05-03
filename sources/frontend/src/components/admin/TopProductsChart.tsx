/**
 * Phase 19 / Plan 19-04 (ADMIN-02) — Top products horizontal bar chart.
 * D-12: fill var(--secondary). D-13: VN tooltip "{n} sản phẩm".
 * D-15 empty state: "Chưa có sản phẩm bán ra trong khoảng này".
 * Truncate name >20 chars với '…'.
 */
'use client';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from 'recharts';
import { vnNumber } from '@/lib/chartFormat';
import type { TopProductPoint } from '@/services/charts';

export function TopProductsChart({ data }: { data: TopProductPoint[] }) {
  if (data.length === 0) {
    return (
      <p style={{ color: 'var(--on-surface-variant)' }}>
        Chưa có sản phẩm bán ra trong khoảng này
      </p>
    );
  }
  const display = data.map((d) => ({
    ...d,
    shortName: d.name.length > 20 ? d.name.slice(0, 20) + '…' : d.name,
  }));
  return (
    <ResponsiveContainer width="100%" height={250}>
      <BarChart data={display} layout="vertical" margin={{ top: 10, right: 16, left: 80, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--outline)" />
        <XAxis type="number" tickFormatter={(n: number) => vnNumber(n)} tick={{ fontSize: 12 }} />
        <YAxis type="category" dataKey="shortName" tick={{ fontSize: 11 }} width={120} />
        <Tooltip formatter={(v) => `${vnNumber(Number(v))} sản phẩm`} />
        <Bar dataKey="qtySold" fill="var(--secondary)" />
      </BarChart>
    </ResponsiveContainer>
  );
}
