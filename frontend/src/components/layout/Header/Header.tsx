'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import styles from './Header.module.css';

export default function Header() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  return (
    <header className={styles.header}>
      <div className={styles.container}>
        {/* Logo */}
        <Link href="/" className={styles.logo}>
          <span className={styles.logoText}>The Digital</span>
          <span className={styles.logoAccent}>Atélier</span>
        </Link>

        {/* Desktop Navigation */}
        <nav className={styles.nav}>
          <Link href="/products" className={styles.navLink}>Sản phẩm</Link>
          <Link href="/collections" className={styles.navLink}>Bộ sưu tập</Link>
          <Link href="/deals" className={styles.navLink}>Ưu đãi</Link>
          <Link href="/about" className={styles.navLink}>Về chúng tôi</Link>
        </nav>

        {/* Actions */}
        <div className={styles.actions}>
          {/* Search Toggle */}
          <button
            className={styles.actionBtn}
            onClick={() => setIsSearchOpen(!isSearchOpen)}
            aria-label="Tìm kiếm"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </button>

          {/* Account */}
          <Link href="/account" className={styles.actionBtn} aria-label="Tài khoản">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
          </Link>

          {/* Cart */}
          <Link href="/cart" className={styles.actionBtn} aria-label="Giỏ hàng">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
            <span className={styles.cartBadge}>0</span>
          </Link>

          {/* Mobile hamburger */}
          <button
            className={`${styles.hamburger} ${isMobileMenuOpen ? styles.hamburgerOpen : ''}`}
            onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
            aria-label="Menu"
          >
            <span />
            <span />
            <span />
          </button>
        </div>
      </div>

      {/* Search Bar (expandable) */}
      {isSearchOpen && (
        <div className={styles.searchOverlay}>
          <div className={styles.searchContainer}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="8" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
            <input
              type="text"
              placeholder="Tìm kiếm sản phẩm..."
              className={styles.searchInput}
              autoFocus
            />
            <button
              className={styles.searchClose}
              onClick={() => setIsSearchOpen(false)}
              aria-label="Đóng tìm kiếm"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {/* Mobile Menu */}
      {isMobileMenuOpen && (
        <div className={styles.mobileOverlay} onClick={() => setIsMobileMenuOpen(false)}>
          <nav className={styles.mobileMenu} onClick={e => e.stopPropagation()}>
            <Link href="/products" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>
              Sản phẩm
            </Link>
            <Link href="/collections" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>
              Bộ sưu tập
            </Link>
            <Link href="/deals" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>
              Ưu đãi
            </Link>
            <Link href="/about" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>
              Về chúng tôi
            </Link>
          </nav>
        </div>
      )}
    </header>
  );
}
