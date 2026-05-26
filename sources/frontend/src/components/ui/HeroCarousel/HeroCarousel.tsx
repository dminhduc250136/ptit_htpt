'use client';

/**
 * HeroCarousel — banner trang chủ dạng carousel tự lướt.
 *
 * Thay cho hero tĩnh 1 khung: 5 slide chuyển động, mỗi slide có ảnh nền +
 * tiêu đề + mô tả + nút CTA. Tự chuyển sau mỗi 5s, tạm dừng khi hover/focus.
 * Có dot indicator + nút prev/next để điều khiển thủ công.
 *
 * Ảnh dùng 2 file hero có sẵn (hero-primary/secondary.webp) luân phiên — khi
 * có ảnh banner riêng chỉ cần thay trường `image` trong mảng SLIDES.
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import styles from './HeroCarousel.module.css';

interface Slide {
  badge: string;
  title: string;
  highlight: string;
  description: string;
  ctaLabel: string;
  ctaHref: string;
  image: string;
}

/** 5 slide nội dung banner. Sửa text/ảnh/link tại đây. */
const SLIDES: Slide[] = [
  {
    badge: 'Bộ sưu tập Thu Đông 2024',
    title: 'Nghệ thuật',
    highlight: 'chế tác thủ công',
    description:
      'Khám phá bộ sưu tập thu đông mới nhất với chất liệu cao cấp và thiết kế tinh xảo từ những nghệ nhân hàng đầu.',
    ctaLabel: 'Khám phá ngay',
    ctaHref: '/products',
    image: '/hero/hero-primary.webp',
  },
  {
    badge: 'Ưu đãi giới hạn',
    title: 'Giảm đến 40%',
    highlight: 'cho đơn hàng đầu tiên',
    description:
      'Đăng ký thành viên hôm nay để nhận ưu đãi độc quyền và miễn phí vận chuyển toàn quốc.',
    ctaLabel: 'Xem ưu đãi',
    ctaHref: '/deals',
    image: '/hero/hero-secondary.webp',
  },
  {
    badge: 'Hàng mới về',
    title: 'Phụ kiện công nghệ',
    highlight: 'đẳng cấp 2024',
    description:
      'Cập nhật những sản phẩm công nghệ mới nhất — bảo hành chính hãng, giao nhanh trong 24h.',
    ctaLabel: 'Mua ngay',
    ctaHref: '/products',
    image: '/hero/hero-primary.webp',
  },
  {
    badge: 'Tuyển chọn kỹ lưỡng',
    title: 'Chất lượng',
    highlight: 'làm nên khác biệt',
    description:
      'Mỗi sản phẩm đều qua kiểm định nghiêm ngặt — cam kết hoàn tiền nếu không hài lòng.',
    ctaLabel: 'Tìm hiểu thêm',
    ctaHref: '/about',
    image: '/hero/hero-secondary.webp',
  },
  {
    badge: 'Thành viên thân thiết',
    title: 'Tích điểm',
    highlight: 'đổi quà hấp dẫn',
    description:
      'Mỗi giao dịch đều được tích điểm — đổi lấy voucher và quà tặng độc quyền cho thành viên.',
    ctaLabel: 'Đăng ký ngay',
    ctaHref: '/register',
    image: '/hero/hero-primary.webp',
  },
];

const AUTOPLAY_MS = 5000;

export default function HeroCarousel() {
  const [current, setCurrent] = useState(0);
  const [paused, setPaused] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const goTo = useCallback((index: number) => {
    // Vòng tròn: -1 → slide cuối, length → slide đầu.
    setCurrent((index + SLIDES.length) % SLIDES.length);
  }, []);

  const next = useCallback(() => goTo(current + 1), [current, goTo]);
  const prev = useCallback(() => goTo(current - 1), [current, goTo]);

  // Tự động chuyển slide — dừng khi paused (hover/focus).
  useEffect(() => {
    if (paused) return;
    timerRef.current = setInterval(() => {
      setCurrent((c) => (c + 1) % SLIDES.length);
    }, AUTOPLAY_MS);
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [paused]);

  return (
    <section
      className={styles.carousel}
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={() => setPaused(false)}
      aria-label="Banner khuyến mãi"
    >
      {/* Track — các slide xếp ngang, dịch chuyển bằng translateX */}
      <div
        className={styles.track}
        style={{ transform: `translateX(-${current * 100}%)` }}
      >
        {SLIDES.map((slide, i) => (
          <div className={styles.slide} key={i} aria-hidden={i !== current}>
            <Image
              src={slide.image}
              alt=""
              fill
              sizes="100vw"
              priority={i === 0}
              className={styles.slideImage}
            />
            <div className={styles.overlay} />
            <div className={styles.slideContent}>
              <span className={styles.badge}>{slide.badge}</span>
              <h1 className={styles.title}>
                {slide.title} <br />
                <span className={styles.highlight}>{slide.highlight}</span>
              </h1>
              <p className={styles.description}>{slide.description}</p>
              <Link href={slide.ctaHref} className={styles.cta} tabIndex={i === current ? 0 : -1}>
                {slide.ctaLabel}
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <line x1="5" y1="12" x2="19" y2="12" />
                  <polyline points="12 5 19 12 12 19" />
                </svg>
              </Link>
            </div>
          </div>
        ))}
      </div>

      {/* Nút điều hướng */}
      <button className={`${styles.navArrow} ${styles.navPrev}`} onClick={prev} aria-label="Banner trước">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="15 18 9 12 15 6" />
        </svg>
      </button>
      <button className={`${styles.navArrow} ${styles.navNext}`} onClick={next} aria-label="Banner kế tiếp">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <polyline points="9 18 15 12 9 6" />
        </svg>
      </button>

      {/* Dot indicator */}
      <div className={styles.dots}>
        {SLIDES.map((_, i) => (
          <button
            key={i}
            className={`${styles.dot} ${i === current ? styles.dotActive : ''}`}
            onClick={() => goTo(i)}
            aria-label={`Chuyển tới banner ${i + 1}`}
            aria-current={i === current ? 'true' : undefined}
          />
        ))}
      </div>
    </section>
  );
}
