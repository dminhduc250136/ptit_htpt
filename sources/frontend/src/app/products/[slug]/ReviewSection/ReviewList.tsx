'use client';

import { useState } from 'react';
import Button from '@/components/ui/Button/Button';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import type { Review, SortKey } from '@/types';
import ReviewForm from './ReviewForm';
import styles from './ReviewSection.module.css';

interface ReviewListProps {
  reviews: Review[];
  totalElements: number;
  isLast: boolean;
  loading: boolean;
  loadMoreLoading: boolean;
  failed: boolean;
  emptyVariant: 'guest' | 'eligible' | 'not-eligible';
  // Phase 21
  currentUserId?: string;
  editWindowHours: number;
  sort: SortKey;
  onSortChange: (s: SortKey) => void;
  onEdit: (reviewId: string, body: { rating?: number; content?: string }) => Promise<void>;
  onDelete: (reviewId: string) => Promise<void>;
  // existing
  onLoadMore: () => void;
  onRetry: () => void;
}

function isEditExpired(createdAtIso: string, editWindowHours: number): boolean {
  return (Date.now() - new Date(createdAtIso).getTime()) > editWindowHours * 3600 * 1000;
}

function relativeOrAbsolute(iso: string): string {
  const d = new Date(iso);
  const diffDays = Math.floor((Date.now() - d.getTime()) / (1000 * 60 * 60 * 24));
  if (diffDays < 7) {
    const rtf = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' });
    return rtf.format(-diffDays, 'day');
  }
  return new Intl.DateTimeFormat('vi-VN').format(d);
}

function ReadOnlyStars({ rating }: { rating: number }) {
  return (
    <span className={styles.readOnlyStars} aria-hidden="true">
      {[0, 1, 2, 3, 4].map((i) => {
        const filled = i < Math.floor(rating);
        return (
          <svg key={i} width="14" height="14" viewBox="0 0 24 24"
            fill={filled ? 'var(--secondary-container)' : 'none'}
            stroke={filled ? 'var(--secondary-container)' : 'var(--outline-variant)'}
            strokeWidth="1.5">
            <polygon points="12,2 15,9 22,9 16,14 18,21 12,17 6,21 8,14 2,9 9,9" />
          </svg>
        );
      })}
    </span>
  );
}

export default function ReviewList(props: ReviewListProps) {
  const {
    reviews, totalElements, isLast, loading, loadMoreLoading, failed, emptyVariant,
    currentUserId, editWindowHours, sort, onSortChange, onEdit, onDelete,
    onLoadMore, onRetry,
  } = props;

  // Phase 21 REV-04: track per-item edit form open state
  const [editingId, setEditingId] = useState<string | null>(null);

  if (failed) return <RetrySection onRetry={onRetry} />;
  if (loading) {
    return (
      <div className={styles.skeletonList}>
        <div className={styles.skeletonCard} />
        <div className={styles.skeletonCard} />
        <div className={styles.skeletonCard} />
      </div>
    );
  }
  if (reviews.length === 0) {
    const emptyCopy =
      emptyVariant === 'guest'
        ? 'Chưa có đánh giá nào. Hãy là người đầu tiên đánh giá sản phẩm này!'
        : emptyVariant === 'eligible'
          ? 'Chưa có đánh giá nào. Hãy chia sẻ cảm nhận của bạn!'
          : 'Chưa có đánh giá nào.';
    return <p className={styles.emptyState}>{emptyCopy}</p>;
  }

  return (
    <div>
      {/* Phase 21 D-13: sort dropdown header (default newest KHÔNG ghi vào URL) */}
      <div className={styles.listHeader}>
        <h3 className={styles.listHeading}>Đánh giá từ khách hàng ({totalElements})</h3>
        <select
          className={styles.sortDropdown}
          value={sort}
          onChange={(e) => onSortChange(e.target.value as SortKey)}
          aria-label="Sắp xếp đánh giá"
        >
          <option value="newest">Mới nhất</option>
          <option value="rating_desc">Đánh giá cao nhất</option>
          <option value="rating_asc">Đánh giá thấp nhất</option>
        </select>
      </div>
      <ul className={styles.reviewList} role="list">
        {reviews.map((review) => {
          const initial = review.reviewerName?.[0]?.toUpperCase() ?? '?';
          const isOwner = !!currentUserId && review.userId === currentUserId;
          const editExpired = isEditExpired(review.createdAt, editWindowHours);
          const isEditing = editingId === review.id;
          return (
            <li key={review.id} className={styles.reviewItem}>
              <div className={styles.reviewHeader}>
                <span className={styles.avatar} aria-hidden="true">{initial}</span>
                <span className={styles.reviewerName}>{review.reviewerName}</span>
                <ReadOnlyStars rating={review.rating} />
                <span className="sr-only">{review.rating} sao</span>
                <span className={styles.reviewDate}>{relativeOrAbsolute(review.createdAt)}</span>
              </div>

              {isOwner && isEditing ? (
                <ReviewForm
                  mode="edit"
                  initialValues={{ rating: review.rating, content: review.content ?? '' }}
                  onCancel={() => setEditingId(null)}
                  onSubmit={async (data) => {
                    try {
                      await onEdit(review.id, data);
                      setEditingId(null);   // close form chỉ khi success
                    } catch {
                      /* ReviewSection đã toast — giữ form mở để user thử lại */
                    }
                  }}
                />
              ) : (
                <>
                  {review.content && (
                    <p className={styles.reviewContent}>{review.content}</p>
                  )}
                  {isOwner && (
                    <div className={styles.actionsRow}>
                      <button
                        type="button"
                        className={styles.actionLink}
                        disabled={editExpired}
                        title={editExpired ? 'Đã quá thời hạn chỉnh sửa (24h)' : undefined}
                        onClick={() => setEditingId(review.id)}
                      >
                        Sửa
                      </button>
                      <button
                        type="button"
                        className={`${styles.actionLink} ${styles.actionDanger}`}
                        onClick={() => onDelete(review.id)}
                      >
                        Xoá
                      </button>
                    </div>
                  )}
                </>
              )}
            </li>
          );
        })}
      </ul>
      {!isLast && (
        <div className={styles.loadMoreRow}>
          <Button
            variant="secondary"
            onClick={onLoadMore}
            loading={loadMoreLoading}
            disabled={loadMoreLoading}
            aria-label="Xem thêm đánh giá"
          >
            Xem thêm đánh giá
          </Button>
        </div>
      )}
    </div>
  );
}
