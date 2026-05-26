'use client';

/**
 * Admin Coupons page — Phase 20 / COUP-02 (D-21, D-22).
 *
 * List + filter + CRUD modal (rhf+zod) + toggle active + delete confirm.
 * Mirror pattern admin/products/page.tsx + dùng rhf+zod cho stricter validation.
 *
 * Threat mitigations:
 *   - T-20-06-01: Admin layout đã guard role (BE JwtRoleGuard verify)
 *   - T-20-06-02: rhf+zod chỉ là UX; BE @Pattern + @DecimalMin authoritative
 *   - T-20-06-06: D-14 hard-delete chỉ khi used_count=0; nếu có redemption →
 *     409 COUPON_HAS_REDEMPTIONS → modal lỗi gợi ý "tắt thay vì xoá".
 */

import React, { useCallback, useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Badge from '@/components/ui/Badge/Badge';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { useToast } from '@/components/ui/Toast/Toast';
import {
  listAdminCoupons,
  createCoupon,
  updateCoupon,
  toggleCouponActive,
  deleteCoupon,
  type CouponUpsertBody,
} from '@/services/admin-coupons';
import { isApiError } from '@/services/errors';
import { formatCouponError } from '@/lib/couponErrorMessages';
import type { AdminCoupon } from '@/types';

// Zod schema cho coupon form (D-21 form fields)
const couponSchema = z
  .object({
    code: z
      .string()
      .regex(/^[A-Z0-9_-]{3,32}$/, 'Mã phải gồm 3-32 ký tự A-Z, 0-9, _, -'),
    type: z.enum(['PERCENT', 'FIXED']),
    value: z.coerce.number().positive('Giá trị phải lớn hơn 0'),
    minOrderAmount: z.coerce.number().min(0, 'Đơn tối thiểu không âm'),
    noLimit: z.boolean(),
    maxTotalUses: z.coerce.number().int().positive().nullable().optional(),
    noExpiry: z.boolean(),
    expiresAt: z.string().nullable().optional(),
    active: z.boolean(),
  })
  .refine((d) => !(d.type === 'PERCENT' && d.value > 100), {
    message: 'Phần trăm tối đa 100%',
    path: ['value'],
  })
  .refine((d) => d.noLimit || (d.maxTotalUses != null && d.maxTotalUses > 0), {
    message: 'Vui lòng nhập số lượt tối đa hoặc chọn không giới hạn',
    path: ['maxTotalUses'],
  })
  .refine((d) => d.noExpiry || (typeof d.expiresAt === 'string' && d.expiresAt.length > 0), {
    message: 'Vui lòng nhập ngày hết hạn hoặc chọn không hết hạn',
    path: ['expiresAt'],
  });

// rhf v7 + zod v4 với z.coerce.number(): input type là `unknown`,
// output type là `number`. Dùng z.input/z.output để tách:
type CouponFormInput = z.input<typeof couponSchema>;
type CouponFormData = z.output<typeof couponSchema>;

const emptyForm: CouponFormInput = {
  code: '',
  type: 'PERCENT',
  value: 10,
  minOrderAmount: 0,
  noLimit: true,
  maxTotalUses: null,
  noExpiry: true,
  expiresAt: null,
  active: true,
};

export default function AdminCouponsPage() {
  const [coupons, setCoupons] = useState<AdminCoupon[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [editTarget, setEditTarget] = useState<AdminCoupon | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminCoupon | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [activeFilter, setActiveFilter] = useState<'ALL' | 'true' | 'false'>('ALL');
  const { showToast } = useToast();

  const {
    control,
    register,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors },
  } = useForm<CouponFormInput, unknown, CouponFormData>({
    resolver: zodResolver(couponSchema),
    defaultValues: emptyForm,
  });

  const noLimit = watch('noLimit');
  const noExpiry = watch('noExpiry');
  const couponType = watch('type');

  const load = useCallback(async () => {
    setLoading(true);
    setFailed(false);
    try {
      const params: { q?: string; active?: boolean } = {};
      if (search.trim()) params.q = search.trim();
      if (activeFilter !== 'ALL') params.active = activeFilter === 'true';
      const resp = await listAdminCoupons(params);
      setCoupons(resp?.content ?? []);
    } catch {
      setFailed(true);
    } finally {
      setLoading(false);
    }
  }, [search, activeFilter]);

  useEffect(() => {
    load();
  }, [load]);

  const openAddModal = () => {
    setEditTarget(null);
    reset(emptyForm);
    setShowModal(true);
  };

  const openEditModal = (c: AdminCoupon) => {
    setEditTarget(c);
    reset({
      code: c.code,
      type: c.type,
      value: c.value,
      minOrderAmount: c.minOrderAmount,
      noLimit: c.maxTotalUses === null,
      maxTotalUses: c.maxTotalUses,
      noExpiry: c.expiresAt === null,
      // datetime-local format: YYYY-MM-DDTHH:mm
      expiresAt: c.expiresAt ? c.expiresAt.slice(0, 16) : null,
      active: c.active,
    });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditTarget(null);
  };

  const onSubmit = async (data: CouponFormData) => {
    setSaving(true);
    try {
      const body: CouponUpsertBody = {
        code: data.code.toUpperCase(),
        type: data.type,
        value: data.value,
        minOrderAmount: data.minOrderAmount,
        maxTotalUses: data.noLimit ? null : (data.maxTotalUses ?? null),
        expiresAt: data.noExpiry
          ? null
          : data.expiresAt
            ? new Date(data.expiresAt).toISOString()
            : null,
        active: data.active,
      };
      if (editTarget) {
        await updateCoupon(editTarget.id, body);
        showToast('Coupon đã được cập nhật', 'success');
      } else {
        await createCoupon(body);
        showToast('Coupon đã được tạo', 'success');
      }
      closeModal();
      await load();
    } catch (err) {
      const fallback = editTarget ? 'Không thể cập nhật coupon' : 'Không thể tạo coupon';
      if (isApiError(err)) {
        const msg = formatCouponError(err.code, err.details) ?? err.message ?? fallback;
        showToast(msg, 'error');
      } else {
        showToast(fallback, 'error');
      }
    } finally {
      setSaving(false);
    }
  };

  const handleToggleActive = async (c: AdminCoupon) => {
    try {
      await toggleCouponActive(c.id, !c.active);
      showToast(c.active ? 'Đã tắt coupon' : 'Đã bật coupon', 'success');
      await load();
    } catch {
      showToast('Không thể thay đổi trạng thái coupon', 'error');
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleteError(null);
    try {
      await deleteCoupon(deleteTarget.id);
      showToast('Coupon đã được xoá', 'success');
      setDeleteTarget(null);
      await load();
    } catch (err) {
      // T-20-06-06: BE 409 COUPON_HAS_REDEMPTIONS → modal lỗi gợi ý tắt thay vì xoá
      if (isApiError(err) && err.code === 'COUPON_HAS_REDEMPTIONS') {
        setDeleteError('Coupon đã có người dùng — vui lòng tắt thay vì xoá');
      } else {
        setDeleteError('Không thể xoá coupon. Vui lòng thử lại');
      }
    }
  };

  const formatExpiry = (iso: string | null) => {
    if (!iso) return 'Không hết hạn';
    return new Date(iso).toLocaleDateString('vi-VN');
  };

  const formatValue = (c: AdminCoupon) =>
    c.type === 'PERCENT'
      ? `${c.value}%`
      : `${new Intl.NumberFormat('vi-VN').format(c.value)} đ`;

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý coupon</h1>
        <Button onClick={openAddModal}>+ Thêm coupon</Button>
      </div>

      <div className={styles.toolbar}>
        <Input
          placeholder="Tìm theo mã..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          value={activeFilter}
          onChange={(e) => setActiveFilter(e.target.value as 'ALL' | 'true' | 'false')}
          className={styles.select}
          aria-label="Lọc trạng thái"
        >
          <option value="ALL">Tất cả</option>
          <option value="true">Đang bật</option>
          <option value="false">Đã tắt</option>
        </select>
        <span className={styles.count}>{coupons.length} coupon</span>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Loại</th>
              <th>Giá trị</th>
              <th>Đơn tối thiểu</th>
              <th>Đã dùng / Tối đa</th>
              <th>Hết hạn</th>
              <th>Trạng thái</th>
              <th>Hành động</th>
            </tr>
          </thead>
          <tbody>
            {loading &&
              [...Array(5)].map((_, i) => (
                <tr key={i}>
                  <td colSpan={8}>
                    <div className="skeleton" style={{ height: 40, borderRadius: 'var(--radius-md)' }} />
                  </td>
                </tr>
              ))}
            {!loading && failed && (
              <tr>
                <td colSpan={8}>
                  <RetrySection onRetry={load} loading={loading} />
                </td>
              </tr>
            )}
            {!loading && !failed && coupons.length === 0 && (
              <tr>
                <td colSpan={8}>
                  <div className={styles.emptyState}>Chưa có coupon nào</div>
                </td>
              </tr>
            )}
            {!loading &&
              !failed &&
              coupons.map((c) => (
                <tr key={c.id}>
                  <td>
                    <strong>{c.code}</strong>
                  </td>
                  <td>{c.type === 'PERCENT' ? 'Phần trăm' : 'Số tiền cố định'}</td>
                  <td>{formatValue(c)}</td>
                  <td>{new Intl.NumberFormat('vi-VN').format(c.minOrderAmount)} đ</td>
                  <td>
                    {c.usedCount} / {c.maxTotalUses ?? '∞'}
                  </td>
                  <td>{formatExpiry(c.expiresAt)}</td>
                  <td>
                    <Badge variant={c.active ? 'success' : 'default'}>
                      {c.active ? 'Đang bật' : 'Đã tắt'}
                    </Badge>
                  </td>
                  <td>
                    <div className={styles.actions}>
                      <button
                        className={styles.actionBtn}
                        onClick={() => openEditModal(c)}
                        aria-label="Sửa coupon"
                        title="Sửa"
                      >
                        ✏️
                      </button>
                      <button
                        className={styles.actionBtn}
                        onClick={() => handleToggleActive(c)}
                        aria-label={c.active ? 'Tắt coupon' : 'Bật coupon'}
                        title={c.active ? 'Tắt' : 'Bật'}
                      >
                        {c.active ? '⏸' : '▶️'}
                      </button>
                      <button
                        className={`${styles.actionBtn} ${styles.deleteBtn}`}
                        onClick={() => {
                          setDeleteTarget(c);
                          setDeleteError(null);
                        }}
                        aria-label="Xoá coupon"
                        title="Xoá"
                      >
                        🗑️
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {/* Modal: Add / Edit */}
      {showModal && (
        <div className={styles.overlay} onClick={closeModal}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>{editTarget ? 'Chỉnh sửa coupon' : 'Thêm coupon mới'}</h3>
              <button className={styles.closeBtn} onClick={closeModal} aria-label="Đóng">
                ✕
              </button>
            </div>
            <form className={styles.modalForm} onSubmit={handleSubmit(onSubmit)}>
              <Input
                label="Mã coupon"
                {...register('code', {
                  onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
                    setValue('code', e.target.value.toUpperCase()),
                })}
                error={errors.code?.message}
                fullWidth
                placeholder="VD: SUMMER2026"
              />

              <div className={styles.formRow}>
                <div className={styles.radioGroup}>
                  <span>Loại</span>
                  <Controller
                    name="type"
                    control={control}
                    render={({ field }) => (
                      <div className={styles.radioInline}>
                        <label>
                          <input
                            type="radio"
                            value="PERCENT"
                            checked={field.value === 'PERCENT'}
                            onChange={() => field.onChange('PERCENT')}
                          />{' '}
                          Phần trăm
                        </label>
                        <label>
                          <input
                            type="radio"
                            value="FIXED"
                            checked={field.value === 'FIXED'}
                            onChange={() => field.onChange('FIXED')}
                          />{' '}
                          Số tiền cố định
                        </label>
                      </div>
                    )}
                  />
                </div>
                <Input
                  label={`Giá trị ${couponType === 'PERCENT' ? '(%)' : '(đ)'}`}
                  type="number"
                  step="any"
                  {...register('value')}
                  error={errors.value?.message}
                  fullWidth
                />
              </div>

              <Input
                label="Đơn tối thiểu (đ)"
                type="number"
                {...register('minOrderAmount')}
                error={errors.minOrderAmount?.message}
                fullWidth
              />

              <div className={styles.formRow}>
                <label className={styles.checkboxLine}>
                  <input type="checkbox" {...register('noLimit')} /> Không giới hạn lượt dùng
                </label>
                {!noLimit && (
                  <Input
                    label="Tối đa lượt dùng"
                    type="number"
                    {...register('maxTotalUses')}
                    error={errors.maxTotalUses?.message}
                    fullWidth
                  />
                )}
              </div>

              <div className={styles.formRow}>
                <label className={styles.checkboxLine}>
                  <input type="checkbox" {...register('noExpiry')} /> Không hết hạn
                </label>
                {!noExpiry && (
                  <Input
                    label="Ngày hết hạn"
                    type="datetime-local"
                    {...register('expiresAt')}
                    error={errors.expiresAt?.message}
                    fullWidth
                  />
                )}
              </div>

              <label className={styles.checkboxLine}>
                <input type="checkbox" {...register('active')} /> Đang bật
              </label>

              <div className={styles.modalActions}>
                <Button variant="secondary" type="button" onClick={closeModal}>
                  Hủy
                </Button>
                <Button type="submit" loading={saving}>
                  {editTarget ? 'Lưu thay đổi' : 'Thêm coupon'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Confirm Delete */}
      {deleteTarget && (
        <div
          className={styles.overlay}
          onClick={() => {
            setDeleteTarget(null);
            setDeleteError(null);
          }}
        >
          <div className={styles.confirmModal} onClick={(e) => e.stopPropagation()}>
            <h3>Xác nhận xoá</h3>
            <p>
              Bạn có chắc muốn xoá coupon <strong>{deleteTarget.code}</strong>?
            </p>
            {deleteError && <p className={styles.errorText}>{deleteError}</p>}
            <div className={styles.confirmActions}>
              <Button
                variant="secondary"
                onClick={() => {
                  setDeleteTarget(null);
                  setDeleteError(null);
                }}
              >
                Hủy
              </Button>
              <Button variant="danger" onClick={handleConfirmDelete}>
                Xoá coupon
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
