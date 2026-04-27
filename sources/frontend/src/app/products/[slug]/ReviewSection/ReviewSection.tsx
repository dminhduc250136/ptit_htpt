'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/ui/Toast/Toast';
import { useAuth } from '@/providers/AuthProvider';
import { isApiError } from '@/services/errors';
import { listReviews, checkEligibility, submitReview, type ReviewListResponse } from '@/services/reviews';
import type { Review } from '@/types';
import ReviewForm from './ReviewForm';
import ReviewList from './ReviewList';
import styles from './ReviewSection.module.css';

interface ReviewSectionProps {
  productId: string;
  slug: string;
}

export default function ReviewSection({ productId, slug }: ReviewSectionProps) {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState<{ totalElements: number; isLast: boolean } | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadMoreLoading, setLoadMoreLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  const [eligible, setEligible] = useState<boolean | null>(null);

  const loadPage = useCallback(async (p: number, append: boolean) => {
    if (p === 0) setLoading(true); else setLoadMoreLoading(true);
    setFailed(false);
    try {
      const res: ReviewListResponse = await listReviews(productId, p, 10);
      setReviews((prev) => append ? [...prev, ...res.content] : res.content);
      setMeta({ totalElements: res.totalElements, isLast: res.isLast });
      setPage(p);
    } catch {
      if (p === 0) setFailed(true);
      else showToast('Tải thêm đánh giá thất bại. Vui lòng thử lại.', 'error');
    } finally {
      setLoading(false);
      setLoadMoreLoading(false);
    }
  }, [productId, showToast]);

  useEffect(() => { loadPage(0, false); }, [loadPage]);

  // D-08: chỉ gọi eligibility khi đã login (D-09)
  useEffect(() => {
    if (!user) { setEligible(null); return; }
    let cancelled = false;
    checkEligibility(productId)
      .then((r) => { if (!cancelled) setEligible(r.eligible); })
      .catch(() => { if (!cancelled) setEligible(false); }); // fail-safe: hide form
    return () => { cancelled = true; };
  }, [productId, user]);

  const handleSubmit = useCallback(async (body: { rating: number; content?: string }) => {
    try {
      await submitReview(productId, body);
      showToast('Đã gửi đánh giá', 'success');
      await loadPage(0, false);   // D-07: reload page 1
    } catch (err) {
      if (isApiError(err)) {
        if (err.code === 'REVIEW_NOT_ELIGIBLE') {
          showToast('Bạn chưa mua sản phẩm này.', 'error');
          setEligible(false);
          return;
        }
        if (err.code === 'REVIEW_ALREADY_EXISTS') {
          showToast('Bạn đã đánh giá sản phẩm này rồi.', 'error');
          return;
        }
      }
      showToast('Đã xảy ra lỗi. Vui lòng thử lại.', 'error');
      throw err; // để form không reset nếu lỗi
    }
  }, [productId, showToast, loadPage]);

  const emptyVariant: 'guest' | 'eligible' | 'not-eligible' =
    !user ? 'guest' : eligible === true ? 'eligible' : 'not-eligible';

  return (
    <section className={styles.section} aria-label="Đánh giá sản phẩm">
      {!user && (
        <p className={styles.hint}>
          Đăng nhập để đánh giá sản phẩm.{' '}
          <Link href={`/login?redirect=/products/${encodeURIComponent(slug)}`} className={styles.hintLink}>
            Đăng nhập
          </Link>
        </p>
      )}

      {user && eligible === null && (
        <div className={styles.eligibilitySkeleton}>
          <div className={styles.skeletonCard} />
        </div>
      )}

      {user && eligible === true && <ReviewForm onSubmit={handleSubmit} />}

      {user && eligible === false && (
        <p className={styles.hint}>Chỉ người đã mua sản phẩm này mới có thể đánh giá.</p>
      )}

      <ReviewList
        reviews={reviews}
        totalElements={meta?.totalElements ?? 0}
        isLast={meta?.isLast ?? true}
        loading={loading}
        loadMoreLoading={loadMoreLoading}
        failed={failed}
        emptyVariant={emptyVariant}
        onLoadMore={() => loadPage(page + 1, true)}
        onRetry={() => loadPage(0, false)}
      />
    </section>
  );
}
