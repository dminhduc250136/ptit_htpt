import React from 'react';
import Link from 'next/link';
import styles from './Footer.module.css';

export default function Footer() {
  return (
    <footer className={styles.footer}>
      <div className={styles.container}>
        {/* Brand */}
        <div className={styles.brand}>
          <div className={styles.logo}>
            <span className={styles.logoText}>The Digital</span>
            <span className={styles.logoAccent}>Atélier</span>
          </div>
          <p className={styles.tagline}>
            Trải nghiệm mua sắm kỹ thuật số đẳng cấp. Chúng tôi mang đến sự tinh tế trong từng sản phẩm và niềm tin trong từng giao dịch.
          </p>
        </div>

        {/* Links Groups */}
        <div className={styles.linksGroup}>
          <h4 className={styles.groupTitle}>Khám phá</h4>
          <Link href="/about" className={styles.link}>Về chúng tôi</Link>
          <Link href="/products" className={styles.link}>Sản phẩm mới</Link>
          <Link href="/deals" className={styles.link}>Ưu đãi thành viên</Link>
        </div>

        <div className={styles.linksGroup}>
          <h4 className={styles.groupTitle}>Hỗ trợ</h4>
          <Link href="/contact" className={styles.link}>Liên hệ</Link>
          <Link href="/shipping" className={styles.link}>Vận chuyển</Link>
          <Link href="/returns" className={styles.link}>Đổi trả hàng</Link>
        </div>

        <div className={styles.linksGroup}>
          <h4 className={styles.groupTitle}>Pháp lý</h4>
          <Link href="/privacy" className={styles.link}>Chính sách bảo mật</Link>
          <Link href="/terms" className={styles.link}>Điều khoản dịch vụ</Link>
        </div>
      </div>

      {/* Bottom Bar */}
      <div className={styles.bottomBar}>
        <div className={styles.bottomContainer}>
          <p className={styles.copyright}>© 2024 The Digital Atélier. Tất cả quyền được bảo lưu.</p>
        </div>
      </div>
    </footer>
  );
}
