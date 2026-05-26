'use client';

import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Button from '@/components/ui/Button/Button';
import StarWidget from './StarWidget';
import styles from './ReviewSection.module.css';

const reviewSchema = z.object({
  rating: z.number().min(1, 'Vui lòng chọn số sao để tiếp tục').max(5),
  content: z.string().max(500, 'Nhận xét tối đa 500 ký tự').optional(),
});
type ReviewFormData = z.infer<typeof reviewSchema>;

interface ReviewFormProps {
  /** Phase 21 REV-04: 'edit' bật nút Huỷ + label "Lưu thay đổi" + KHÔNG reset sau submit. */
  mode?: 'create' | 'edit';
  /** Phase 21 REV-04: prefill rating + content khi mode='edit'. */
  initialValues?: { rating: number; content?: string };
  onSubmit: (data: { rating: number; content?: string }) => Promise<void>;
  /** Phase 21 REV-04: chỉ render nút "Huỷ" khi mode='edit'. */
  onCancel?: () => void;
}

export default function ReviewForm({
  mode = 'create',
  initialValues,
  onSubmit,
  onCancel,
}: ReviewFormProps) {
  const [submitting, setSubmitting] = useState(false);
  const {
    register,
    handleSubmit,
    setValue,
    watch,
    reset,
    formState: { errors },
  } = useForm<ReviewFormData>({
    resolver: zodResolver(reviewSchema),
    defaultValues: {
      rating: initialValues?.rating ?? 0,
      content: initialValues?.content ?? '',
    },
  });

  // Pitfall 9: reset khi mode/initialValues đổi (component remount-less swap giữa các review item)
  useEffect(() => {
    reset({
      rating: initialValues?.rating ?? 0,
      content: initialValues?.content ?? '',
    });
  }, [mode, initialValues?.rating, initialValues?.content, reset]);

  const rating = watch('rating') ?? 0;
  const content = watch('content') ?? '';
  const charCount = content.length;
  const charClass = charCount >= 450 ? styles.charCountWarn : styles.charCount;

  const submitHandler = handleSubmit(async (data) => {
    try {
      setSubmitting(true);
      await onSubmit({ rating: data.rating, content: data.content?.trim() || undefined });
      // D-07: chỉ reset khi mode='create' — mode='edit' để parent unmount form qua setEditingId(null)
      if (mode === 'create') {
        reset({ rating: 0, content: '' });
      }
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <form onSubmit={submitHandler} noValidate className={styles.form}>
      <label className={styles.formLabel}>
        {mode === 'edit' ? 'Chỉnh sửa đánh giá' : 'Đánh giá của bạn'}
      </label>
      <input type="hidden" {...register('rating', { valueAsNumber: true })} aria-hidden="true" tabIndex={-1} />
      <StarWidget
        value={rating}
        onChange={(n) => setValue('rating', n, { shouldValidate: true })}
        disabled={submitting}
      />
      {errors.rating && (
        <p role="alert" className={styles.fieldError}>{errors.rating.message}</p>
      )}

      <label htmlFor="review-content" className={styles.textareaLabel}>
        Nhận xét (không bắt buộc)
      </label>
      <textarea
        id="review-content"
        rows={4}
        maxLength={500}
        placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm này..."
        className={styles.textarea}
        disabled={submitting}
        {...register('content')}
      />
      <div className={charClass} aria-live="polite">{charCount}/500</div>
      {errors.content && (
        <p role="alert" className={styles.fieldError}>{errors.content.message}</p>
      )}

      <div className={styles.submitRow}>
        {mode === 'edit' && onCancel && (
          <Button type="button" variant="secondary" onClick={onCancel} disabled={submitting}>
            Huỷ
          </Button>
        )}
        <Button type="submit" variant="primary" loading={submitting} disabled={submitting}>
          {mode === 'edit' ? 'Lưu thay đổi' : 'Gửi đánh giá'}
        </Button>
      </div>
    </form>
  );
}
