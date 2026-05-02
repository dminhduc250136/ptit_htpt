'use client';

import React, { useState, useRef, useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styles from './Header.module.css';
import { useAuth } from '@/providers/AuthProvider';
import { logout as apiLogout } from '@/services/auth';
import { useCart } from '@/hooks/useCart';

export default function Header() {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isUserMenuOpen, setIsUserMenuOpen] = useState(false);
  const { isAuthenticated, user, logout } = useAuth();
  const { data: cartItems = [] } = useCart();
  const cartCount = cartItems.reduce((sum, i) => sum + i.quantity, 0);
  const router = useRouter();
  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setIsUserMenuOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    apiLogout();
    logout();
    setIsUserMenuOpen(false);
    router.push('/login');
  };

  const initials = user?.name
    ? user.name.split(' ').map(w => w[0]).slice(0, 2).join('').toUpperCase()
    : 'U';

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

          {/* Account / User Menu */}
          {isAuthenticated && user ? (
            <div className={styles.userMenu} ref={userMenuRef}>
              <button
                className={styles.userBtn}
                onClick={() => setIsUserMenuOpen(!isUserMenuOpen)}
                aria-label="Menu tài khoản"
              >
                <div className={styles.userAvatar}>{initials}</div>
              </button>
              {isUserMenuOpen && (
                <div className={styles.dropdown}>
                  <div className={styles.dropdownUser}>
                    <p className={styles.dropdownName}>{user.name || 'Người dùng'}</p>
                    <p className={styles.dropdownEmail}>{user.email}</p>
                  </div>
                  <Link href="/profile" className={styles.dropdownItem} onClick={() => setIsUserMenuOpen(false)}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                    Thông tin tài khoản
                  </Link>
                  <Link href="/profile/orders" className={styles.dropdownItem} onClick={() => setIsUserMenuOpen(false)}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
                    Đơn hàng của tôi
                  </Link>
                  <div className={styles.dropdownDivider} />
                  <button className={`${styles.dropdownItem} ${styles.dropdownItemDanger}`} onClick={handleLogout}>
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
                    Đăng xuất
                  </button>
                </div>
              )}
            </div>
          ) : (
            <Link href="/login" className={styles.actionBtn} aria-label="Đăng nhập">
              <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                <circle cx="12" cy="7" r="4" />
              </svg>
            </Link>
          )}

          {/* Cart */}
          <Link href="/cart" className={styles.actionBtn} aria-label="Giỏ hàng">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
            <span className={styles.cartBadge}>{cartCount}</span>
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

      {/* Search Bar */}
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
            <button className={styles.searchClose} onClick={() => setIsSearchOpen(false)} aria-label="Đóng">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </div>
      )}

      {/* Mobile Menu */}
      {isMobileMenuOpen && (
        <div className={styles.mobileOverlay} onClick={() => setIsMobileMenuOpen(false)}>
          <nav className={styles.mobileMenu} onClick={e => e.stopPropagation()}>
            <Link href="/products" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>Sản phẩm</Link>
            <Link href="/collections" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>Bộ sưu tập</Link>
            <Link href="/deals" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>Ưu đãi</Link>
            <Link href="/about" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>Về chúng tôi</Link>
            {isAuthenticated ? (
              <button className={styles.mobileLink} onClick={handleLogout} style={{ background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', width: '100%', color: 'var(--error)' }}>
                Đăng xuất
              </button>
            ) : (
              <Link href="/login" className={styles.mobileLink} onClick={() => setIsMobileMenuOpen(false)}>Đăng nhập</Link>
            )}
          </nav>
        </div>
      )}
    </header>
  );
}
