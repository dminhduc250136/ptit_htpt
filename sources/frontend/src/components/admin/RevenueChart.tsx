/**
 * Phase 19 / Plan 19-04 (ADMIN-01) — Revenue line chart.
 * D-12: stroke var(--primary). D-13: VN tooltip "{n} ₫" + DD/MM date axis.
 * D-15 empty state: "Chưa có dữ liệu trong khoảng này".
 */
'use client';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from 'recharts';
import { vnNumber, vnDate } from '@/lib/chartFormat';
import type { RevenuePoint } from '@/services/charts';

export function RevenueChart({ data }: { data: RevenuePoint[] }) {
  if (data.length === 0) {
    return (
      <p style={{ color: 'var(--on-surface-variant)' }}>Chưa có dữ liệu trong khoảng này</p>
    );
  }
  return (
    <ResponsiveContainer width="100%" height={250}>
      <LineChart data={data} margin={{ top: 10, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--outline)" />
        <XAxis
          dataKey="date"
          tickFormatter={(iso: string) => vnDate(iso)}
          tick={{ fontSize: 12 }}
        />
        <YAxis tickFormatter={(n: number) => vnNumber(n)} tick={{ fontSize: 12 }} />
        <Tooltip
          formatter={(value) => `${vnNumber(Number(value))} ₫`}
          labelFormatter={(iso) => vnDate(String(iso))}
        />
        <Line
          type="monotone"
          dataKey="value"
          stroke="var(--primary)"
          strokeWidth={2}
          dot={false}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
