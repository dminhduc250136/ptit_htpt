'use client';

import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { useToast } from '@/components/ui/Toast/Toast';
import { useAuth } from '@/providers/AuthProvider';
import { isApiError } from '@/services/errors';
import {
  checkEligibility,
  editReview,
  listReviews,
  softDeleteReview,
  submitReview,
  type ReviewListResponse,
} from '@/services/reviews';
import type { Review, SortKey } from '@/types';
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

  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();

  const initialSort = ((): SortKey => {
    const raw = searchParams.get('sort');
    if (raw === 'rating_desc' || raw === 'rating_asc' || raw === 'newest') return raw;
    return 'newest';
  })();
  const [sort, setSort] = useState<SortKey>(initialSort);
  const [editWindowHours, setEditWindowHours] = useState<number>(24);

  const [reviews, setReviews] = useState<Review[]>([]);
  const [page, setPage] = useState(0);
  const [meta, setMeta] = useState<{ totalElements: number; isLast: boolean } | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadMoreLoading, setLoadMoreLoading] = useState(false);
  const [failed, setFailed] = useState(false);

  const [eligible, setEligible] = useState<boolean | null>(null);

  const loadPage = useCallback(async (p: number, append: boolean, sortOverride?: SortKey) => {
    if (p === 0) setLoading(true); else setLoadMoreLoading(true);
    setFailed(false);
    try {
      const sortKey = sortOverride ?? sort;
      const res: ReviewListResponse = await listReviews(productId, p, 10, sortKey);
      setReviews((prev) => append ? [...prev, ...res.content] : res.content);
      setMeta({ totalElements: res.totalElements, isLast: res.isLast });
      setPage(p);
      if (res.config?.editWindowHours) setEditWindowHours(res.config.editWindowHours);
    } catch {
      if (p === 0) setFailed(true);
      else showToast('Tải thêm đánh giá thất bại. Vui lòng thử lại.', 'error');
    } finally {
      setLoading(false);
      setLoadMoreLoading(false);
    }
  }, [productId, showToast, sort]);

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

  // Phase 21 REV-05: sort change → URL persist (default newest KHÔNG ghi vào URL — D-13) + refetch page 0
  const onSortChange = useCallback((newSort: SortKey) => {
    const params = new URLSearchParams(searchParams.toString());
    if (newSort === 'newest') params.delete('sort');
    else params.set('sort', newSort);
    const qs = params.toString();
    router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    setSort(newSort);
    loadPage(0, false, newSort);
  }, [pathname, router, searchParams, loadPage]);

  // Phase 21 REV-04: author edit handler — error envelope mapping per CONTEXT specifics 242-244
  const handleEdit = useCallback(async (reviewId: string, body: { rating?: number; content?: string }) => {
    try {
      await editReview(productId, reviewId, body);
      showToast('Đã cập nhật đánh giá', 'success');
      await loadPage(0, false, sort);
    } catch (err) {
      if (isApiError(err)) {
        if (err.code === 'REVIEW_EDIT_WINDOW_EXPIRED') {
          showToast('Đã quá thời hạn chỉnh sửa (24h kể từ lúc đăng)', 'error');
          throw err;
        }
        if (err.code === 'REVIEW_NOT_OWNER') {
          showToast('Bạn không có quyền chỉnh sửa review này', 'error');
          throw err;
        }
        if (err.code === 'REVIEW_NOT_FOUND') {
          showToast('Review không tồn tại hoặc đã bị xoá', 'error');
          throw err;
        }
      }
      showToast('Đã xảy ra lỗi. Vui lòng thử lại.', 'error');
      throw err;   // re-throw để ReviewList giữ form open cho user retry
    }
  }, [productId, sort, showToast, loadPage]);

  // Phase 21 REV-04: author delete handler — window.confirm (D-23)
  const handleDelete = useCallback(async (reviewId: string) => {
    if (!window.confirm('Xoá đánh giá này? Hành động không thể hoàn tác.')) return;
    try {
      await softDeleteReview(productId, reviewId);
      showToast('Đã xoá đánh giá', 'success');
      await loadPage(0, false, sort);
    } catch {
      showToast('Không thể xoá đánh giá. Vui lòng thử lại.', 'error');
    }
  }, [productId, sort, showToast, loadPage]);

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
        currentUserId={user?.id}
        editWindowHours={editWindowHours}
        sort={sort}
        onSortChange={onSortChange}
        onEdit={handleEdit}
        onDelete={handleDelete}
        onLoadMore={() => loadPage(page + 1, true)}
        onRetry={() => loadPage(0, false)}
      />
    </section>
  );
}
