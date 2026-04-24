import Link from 'next/link';

export default function NotFound() {
  return (
    <div
      style={{
        padding: 'var(--space-6) var(--space-4)',
        textAlign: 'center',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 'var(--space-3)',
      }}
    >
      <h1 style={{ fontSize: 'var(--text-headline-lg)' }}>Không tìm thấy trang</h1>
      <p style={{ color: 'var(--on-surface-variant)' }}>
        Trang bạn yêu cầu không tồn tại hoặc đã bị di chuyển.
      </p>
      <Link
        href="/"
        style={{
          color: 'var(--primary)',
          textDecoration: 'underline',
          fontWeight: 'var(--weight-medium)',
        }}
      >
        Về trang chủ
      </Link>
    </div>
  );
}
