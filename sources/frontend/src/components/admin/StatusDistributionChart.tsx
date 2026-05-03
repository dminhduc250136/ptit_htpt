/**
 * Phase 19 / Plan 19-04 (ADMIN-03) — Status distribution pie chart.
 * D-12 semantic per-slice colors via STATUS_COLORS. D-13 VN labels.
 * D-15 empty state: "Chưa có đơn hàng nào".
 * Pitfall #2: Cell deprecated 3.7+ nhưng 3.8.1 vẫn hoạt động (chỉ console warning) — accept.
 */
'use client';
import {
  PieChart,
  Pie,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import { statusLabel, STATUS_COLORS } from '@/lib/chartFormat';
import type { StatusPoint } from '@/services/charts';

export function StatusDistributionChart({ data }: { data: StatusPoint[] }) {
  if (data.length === 0) {
    return <p style={{ color: 'var(--on-surface-variant)' }}>Chưa có đơn hàng nào</p>;
  }
  const display = data.map((d) => ({
    name: statusLabel(d.status),
    value: d.count,
    status: d.status,
  }));
  return (
    <ResponsiveContainer width="100%" height={250}>
      <PieChart>
        <Pie
          data={display}
          dataKey="value"
          nameKey="name"
          cx="50%"
          cy="50%"
          outerRadius={80}
          innerRadius={40}
          label
        >
          {display.map((entry) => (
            <Cell
              key={entry.status}
              fill={STATUS_COLORS[entry.status] ?? 'var(--outline)'}
            />
          ))}
        </Pie>
        <Tooltip formatter={(v) => `${Number(v)} đơn`} />
        <Legend wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  );
}
