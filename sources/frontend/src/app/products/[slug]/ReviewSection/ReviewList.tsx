'use client';

import Button from '@/components/ui/Button/Button';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import type { Review } from '@/types';
import styles from './ReviewSection.module.css';

interface ReviewListProps {
  reviews: Review[];
  totalElements: number;
  isLast: boolean;
  loading: boolean;
  loadMoreLoading: boolean;
  failed: boolean;
  emptyVariant: 'guest' | 'eligible' | 'not-eligible';
  onLoadMore: () => void;
  onRetry: () => void;
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
  const { reviews, totalElements, isLast, loading, loadMoreLoading, failed, emptyVariant, onLoadMore, onRetry } = props;

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
      <h3 className={styles.listHeading}>Đánh giá từ khách hàng ({totalElements})</h3>
      <ul className={styles.reviewList} role="list">
        {reviews.map((review) => {
          const initial = review.reviewerName?.[0]?.toUpperCase() ?? '?';
          return (
            <li key={review.id} className={styles.reviewItem}>
              <div className={styles.reviewHeader}>
                <span className={styles.avatar} aria-hidden="true">{initial}</span>
                <span className={styles.reviewerName}>{review.reviewerName}</span>
                <ReadOnlyStars rating={review.rating} />
                <span className="sr-only">{review.rating} sao</span>
                <span className={styles.reviewDate}>{relativeOrAbsolute(review.createdAt)}</span>
              </div>
              {review.content && (
                <p className={styles.reviewContent}>{review.content}</p>
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
