'use client';

/**
 * /checkout/result — Trang kết quả thanh toán MoMo (Phase 26.1 / PAY-02).
 *
 * Flow:
 * 1. MoMo redirect về URL này kèm query params (resultCode, orderId=paymentSessionId, transId, ...).
 * 2. Forward toàn bộ query string sang GET /api/payments/momo/return để resolve orderId nội bộ
 *    (KHÔNG tin resultCode để cập nhật DB — chỉ hiển thị sơ bộ, D-16, T-26.1-12).
 * 3. Poll GET /api/orders/{orderId} mỗi 3s tối đa 5 lần, dừng sớm khi paymentStatus != PENDING.
 * 4. Render 5 trạng thái theo UI-SPEC §Copywriting Contract (verbatim).
 */

import React, { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Button from '@/components/ui/Button/Button';
import RetrySection from '@/components/ui/RetrySection/RetrySection';
import { getOrderById } from '@/services/orders';
import { getMomoReturn } from '@/services/payments';
import { isApiError } from '@/services/errors';
import type { Order } from '@/types';

// === Hằng số polling (UI-SPEC §Interaction Contract) ===
const POLL_INTERVAL_MS = 3000; // 3 giây
const POLL_MAX_ATTEMPTS = 5;   // tối đa 5 lần (~15s tổng)

// === 5 trạng thái render (UI-SPEC §Copywriting Contract — verbatim) ===
type ResultState =
  | 'resolving'    // đang resolve orderId từ return endpoint
  | 'polling'      // đang poll payment_status
  | 'success'      // PAID — thanh toán thành công
  | 'failed'       // FAILED — thanh toán thất bại
  | 'cancelled'    // huỷ (resultCode=1006 OR user cancel tại cổng MoMo)
  | 'timeout'      // poll hết hạn vẫn PENDING
  | 'empty'        // thiếu tham số / invalid return
  | 'error';       // lỗi mạng khi poll

const COPY: Record<
  Exclude<ResultState, 'resolving' | 'error'>,
  { heading: string; body: string }
> = {
  polling: {
    heading: 'Đang xác nhận thanh toán',
    body: 'Vui lòng đợi trong giây lát, chúng tôi đang xác nhận giao dịch của bạn với MoMo.',
  },
  success: {
    heading: 'Thanh toán thành công',
    body: 'Đơn hàng của bạn đã được thanh toán. Cảm ơn bạn đã mua sắm.',
  },
  failed: {
    heading: 'Thanh toán thất bại',
    body: 'Giao dịch không thành công hoặc bị từ chối. Đơn hàng vẫn được giữ — bạn có thể thử thanh toán lại.',
  },
  cancelled: {
    heading: 'Đã huỷ thanh toán',
    body: 'Bạn đã huỷ giao dịch. Đơn hàng vẫn được giữ ở trạng thái chờ thanh toán.',
  },
  timeout: {
    heading: 'Chưa nhận được xác nhận',
    body: 'Chúng tôi chưa nhận được xác nhận từ MoMo. Trạng thái đơn sẽ tự cập nhật — vui lòng kiểm tra lại trong trang đơn hàng sau ít phút.',
  },
  empty: {
    heading: 'Không tìm thấy thông tin thanh toán',
    body: 'Liên kết kết quả không hợp lệ hoặc đã hết hạn. Vui lòng kiểm tra đơn hàng trong tài khoản của bạn.',
  },
};

/**
 * Suy luận trạng thái sơ bộ từ MoMo resultCode (T-26.1-12: CHỈ để render ban đầu, KHÔNG update DB).
 * Nguồn sự thật là payment_status poll qua GET /api/orders (T-26.1-04 mitigate).
 */
function codeToInitialState(code: string | null): ResultState {
  if (code === '0' || code === '9000') return 'polling'; // thành công / pending authorized — chờ IPN
  if (code === '1006') return 'cancelled';               // user huỷ tại cổng MoMo
  return 'failed';                                       // mọi code khác → thất bại sơ bộ (vẫn poll)
}

/** Map payment_status từ BE sang ResultState terminal. */
function paymentStatusToState(status: string): ResultState {
  if (status === 'PAID') return 'success';
  if (status === 'FAILED') return 'failed';
  return 'polling'; // PENDING hoặc unknown → tiếp tục poll
}

// ============================================================
// Inner component (cần bọc Suspense vì dùng useSearchParams)
// ============================================================

function ResultPageContent() {
  const searchParams = useSearchParams();

  // MoMo redirect query params (D-16)
  const momoResultCode = searchParams.get('resultCode');
  // orderId từ MoMo = paymentSessionId (KHÔNG phải orderId nội bộ — cần resolve qua getMomoReturn)
  const momoOrderId = searchParams.get('orderId');

  const [state, setState] = useState<ResultState>('resolving');
  const [orderId, setOrderId] = useState<string | null>(null);
  const [order, setOrder] = useState<Order | null>(null);

  // Track poll attempts để tránh race condition
  const pollAttemptsRef = useRef(0);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // === Bước 1: Resolve orderId nội bộ từ MoMo return endpoint ===
  useEffect(() => {
    // Thiếu tham số bắt buộc (orderId = paymentSessionId) → empty state ngay
    if (!momoOrderId) {
      setState('empty');
      return;
    }

    const resolveOrderId = async () => {
      try {
        // Forward NGUYÊN query string — payment-service verify chữ ký SHA256 (T-26.1-14 mitigate)
        const result = await getMomoReturn(new URLSearchParams(window.location.search));
        if (!result.valid || !result.orderId) {
          setState('empty');
          return;
        }
        setOrderId(result.orderId);
        // Render sơ bộ từ resultCode (T-26.1-12: CHỈ hiển thị, KHÔNG update DB)
        setState(codeToInitialState(momoResultCode));
      } catch {
        // Nếu không resolve được orderId → empty state (không block user)
        setState('empty');
      }
    };

    resolveOrderId();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // chỉ chạy 1 lần khi mount

  // === Bước 2: Poll payment_status sau khi có orderId ===
  const startPolling = useCallback((oid: string) => {
    if (intervalRef.current) clearInterval(intervalRef.current);
    pollAttemptsRef.current = 0;

    intervalRef.current = setInterval(async () => {
      pollAttemptsRef.current += 1;

      try {
        const data = await getOrderById(oid);
        setOrder(data);

        const ps = data.paymentStatus ?? 'PENDING';
        if (ps !== 'PENDING') {
          // Trạng thái terminal — dừng poll
          clearInterval(intervalRef.current!);
          intervalRef.current = null;
          setState(paymentStatusToState(ps));
          return;
        }
      } catch (err) {
        if (isApiError(err)) {
          // Lỗi mạng / server — hiển thị RetrySection
          clearInterval(intervalRef.current!);
          intervalRef.current = null;
          setState('error');
          return;
        }
      }

      // Hết 5 lần mà vẫn PENDING → timeout
      if (pollAttemptsRef.current >= POLL_MAX_ATTEMPTS) {
        clearInterval(intervalRef.current!);
        intervalRef.current = null;
        setState('timeout');
      }
    }, POLL_INTERVAL_MS);
  }, []);

  // Khởi động poll khi orderId có (state thay từ 'resolving' sang 'polling' / 'cancelled')
  useEffect(() => {
    if (!orderId) return;
    // Chỉ poll khi state ban đầu là 'polling' (resultCode=0)
    // Với 'cancelled'/'failed' từ resultCode → không cần poll thêm,
    // nhưng vẫn cố poll 1 lần để lấy trạng thái thật từ IPN nếu đã về (T-26.1-04 mitigate)
    startPolling(orderId);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [orderId, startPolling]);

  // === Retry poll thủ công (RetrySection) ===
  const handleRetryPoll = useCallback(() => {
    if (!orderId) return;
    setState('polling');
    startPolling(orderId);
  }, [orderId, startPolling]);

  // === Render: resolving spinner ===
  if (state === 'resolving') {
    return (
      <ResultLayout>
        <div style={{ color: 'var(--on-surface-variant)', textAlign: 'center' }}>
          <Spinner />
          <p style={{ marginTop: 'var(--space-3)', fontSize: 'var(--text-body-lg)' }}>
            Đang xử lý kết quả thanh toán...
          </p>
        </div>
      </ResultLayout>
    );
  }

  // === Render: lỗi mạng khi poll ===
  if (state === 'error') {
    return (
      <ResultLayout>
        <RetrySection
          onRetry={handleRetryPoll}
          heading="Không tải được trạng thái đơn hàng. Thử lại."
          body="Đã xảy ra lỗi khi kiểm tra trạng thái thanh toán."
        />
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center', marginTop: 'var(--space-4)' }}>
          <Button variant="secondary" href="/">Về trang chủ</Button>
        </div>
      </ResultLayout>
    );
  }

  // === Render: polling spinner ===
  if (state === 'polling') {
    return (
      <ResultLayout>
        <div style={{ color: 'var(--on-surface-variant)', textAlign: 'center' }}>
          <Spinner />
          <h1 style={{ fontSize: 'var(--text-headline-md)', fontWeight: 'var(--weight-semibold)', marginTop: 'var(--space-4)', color: 'var(--on-surface)' }}>
            {COPY.polling.heading}
          </h1>
          <p style={{ fontSize: 'var(--text-body-lg)', lineHeight: 'var(--leading-relaxed)', marginTop: 'var(--space-3)', color: 'var(--on-surface-variant)' }}>
            {COPY.polling.body}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center', marginTop: 'var(--space-5)' }}>
          <Button variant="secondary" href="/">Về trang chủ</Button>
        </div>
      </ResultLayout>
    );
  }

  // === Render: 4 trạng thái terminal (success / failed / cancelled / timeout / empty) ===
  const copy = COPY[state as keyof typeof COPY];

  const isSuccess = state === 'success';
  const isFailed = state === 'failed';
  const isCancelled = state === 'cancelled';

  const iconColor = isSuccess
    ? 'var(--success, #10b981)'
    : isFailed
    ? 'var(--error)'
    : isCancelled
    ? 'var(--on-surface-variant)'
    : 'var(--on-surface-variant)';

  return (
    <ResultLayout>
      {/* Icon trạng thái */}
      <div style={{ textAlign: 'center', fontSize: 48 }} aria-hidden>
        {isSuccess ? '✅' : isFailed ? '❌' : isCancelled ? '🚫' : state === 'timeout' ? '⏳' : '❓'}
      </div>

      {/* Heading + body */}
      <h1 style={{
        fontSize: 'var(--text-headline-md)',
        fontWeight: 'var(--weight-semibold)',
        lineHeight: 'var(--leading-tight)',
        marginTop: 'var(--space-4)',
        color: iconColor,
        textAlign: 'center',
      }}>
        {copy?.heading}
      </h1>
      <p style={{
        fontSize: 'var(--text-body-lg)',
        lineHeight: 'var(--leading-relaxed)',
        marginTop: 'var(--space-3)',
        color: 'var(--on-surface-variant)',
        textAlign: 'center',
      }}>
        {copy?.body}
      </p>

      {/* CTA buttons */}
      <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'center', marginTop: 'var(--space-5)', flexWrap: 'wrap' }}>
        {isSuccess && orderId && (
          <Button variant="primary" href={`/profile/orders/${orderId}`}>
            Xem đơn hàng
          </Button>
        )}
        {(isFailed || isCancelled) && (
          <Button variant="primary" href="/checkout">
            Thử thanh toán lại
          </Button>
        )}
        {(state === 'timeout' || state === 'empty') && orderId && (
          <Button variant="primary" href={`/profile/orders/${orderId}`}>
            Xem đơn hàng
          </Button>
        )}
        <Button variant="secondary" href="/">Về trang chủ</Button>
      </div>

      {/* Thông tin đơn hàng nếu đã resolve */}
      {order && orderId && (
        <p style={{ marginTop: 'var(--space-4)', fontSize: 'var(--text-label-lg)', color: 'var(--on-surface-variant)', textAlign: 'center' }}>
          Mã đơn: <strong>{orderId.slice(0, 8)}</strong>
        </p>
      )}
    </ResultLayout>
  );
}

// ============================================================
// Layout wrapper — card trắng giữa trang
// ============================================================

function ResultLayout({ children }: { children: React.ReactNode }) {
  return (
    <div style={{
      minHeight: '60vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 'var(--space-6) var(--space-3)',
    }}>
      <div style={{
        background: 'var(--surface-container-lowest)',
        borderRadius: 'var(--radius-xl)',
        padding: 'var(--space-6) var(--space-5)',
        maxWidth: 520,
        width: '100%',
        boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
      }}>
        {children}
      </div>
    </div>
  );
}

// ============================================================
// Spinner inline (không component mới — UI-SPEC §Component reuse)
// ============================================================

function Spinner() {
  return (
    <div
      role="status"
      aria-label="Đang tải"
      style={{
        display: 'inline-block',
        width: 40,
        height: 40,
        border: '3px solid var(--surface-container)',
        borderTop: '3px solid var(--primary)',
        borderRadius: '50%',
        animation: 'spin 1s linear infinite',
      }}
    />
  );
}

// ============================================================
// Export default — bọc Suspense vì dùng useSearchParams
// (pattern: checkout/page.tsx lines 530-537)
// ============================================================

export default function CheckoutResultPage() {
  return (
    <>
      {/* Inline keyframe animation cho spinner (không tạo CSS module mới) */}
      <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>
      <Suspense fallback={
        <div style={{ minHeight: '60vh', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ color: 'var(--on-surface-variant)' }}>Đang tải...</div>
        </div>
      }>
        <ResultPageContent />
      </Suspense>
    </>
  );
}
