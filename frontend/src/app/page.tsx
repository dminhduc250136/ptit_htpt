import styles from "./page.module.css";
import ProductCard from "@/components/ui/ProductCard/ProductCard";
import Button from "@/components/ui/Button/Button";
import Badge from "@/components/ui/Badge/Badge";
import { mockProducts, mockCategories } from "@/mock-data/products";
import Link from "next/link";

export default function Home() {
  const featuredProducts = mockProducts.filter(
    (p) => p.tags?.includes("Bán chạy") || p.tags?.includes("Best Seller")
  );
  const newProducts = mockProducts.filter((p) => p.tags?.includes("Mới"));
  const allProducts = mockProducts.slice(0, 8);

  return (
    <>
      {/* ===== HERO SECTION ===== */}
      <section className={styles.hero}>
        <div className={styles.heroContent}>
          <Badge variant="new">Bộ sưu tập Thu Đông 2024</Badge>
          <h1 className={styles.heroTitle}>
            Nghệ thuật <br />
            <span className={styles.heroAccent}>chế tác thủ công</span>
          </h1>
          <p className={styles.heroDescription}>
            Khám phá bộ sưu tập thu đông mới nhất với chất liệu cao cấp và
            thiết kế tinh xảo từ những nghệ nhân hàng đầu.
          </p>
          <div className={styles.heroActions}>
            <Link href="/products">
              <Button size="lg">Khám phá ngay</Button>
            </Link>
            <Link href="/collections">
              <Button variant="secondary" size="lg">
                Xem bộ sưu tập
              </Button>
            </Link>
          </div>
        </div>
        <div className={styles.heroVisual}>
          <div className={styles.heroImagePrimary}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600"
              alt="Sản phẩm nổi bật"
              className={styles.heroImg}
            />
          </div>
          <div className={styles.heroImageSecondary}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400"
              alt="Phụ kiện cao cấp"
              className={styles.heroImg}
            />
          </div>
        </div>
      </section>

      {/* ===== CATEGORIES SECTION ===== */}
      <section className={styles.categoriesSection}>
        <div className={styles.container}>
          <div className={styles.sectionHeader}>
            <h2 className={styles.sectionTitle}>Danh mục sản phẩm</h2>
            <p className={styles.sectionSubtitle}>
              Khám phá các dòng sản phẩm được tuyển chọn kỹ lưỡng
            </p>
          </div>
          <div className={styles.categoriesGrid}>
            {mockCategories.map((cat) => (
              <Link
                key={cat.id}
                href={`/products?category=${cat.slug}`}
                className={styles.categoryCard}
              >
                <div className={styles.categoryIcon}>
                  <svg
                    width="28"
                    height="28"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                  >
                    <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z" />
                    <line x1="7" y1="7" x2="7.01" y2="7" />
                  </svg>
                </div>
                <span className={styles.categoryName}>{cat.name}</span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ===== FEATURED PRODUCTS ===== */}
      <section className={styles.productsSection}>
        <div className={styles.container}>
          <div className={styles.sectionHeader}>
            <div>
              <h2 className={styles.sectionTitle}>Sản phẩm nổi bật</h2>
              <p className={styles.sectionSubtitle}>
                Những sản phẩm được yêu thích nhất bởi khách hàng
              </p>
            </div>
            <Link href="/products">
              <Button variant="tertiary">
                Xem tất cả →
              </Button>
            </Link>
          </div>
          <div className={styles.productsGrid}>
            {featuredProducts.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        </div>
      </section>

      {/* ===== BANNER / VALUE PROPOSITION ===== */}
      <section className={styles.valueSection}>
        <div className={styles.container}>
          <div className={styles.valueGrid}>
            <div className={styles.valueItem}>
              <div className={styles.valueIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
              </div>
              <h3 className={styles.valueTitle}>Cam kết chính hãng</h3>
              <p className={styles.valueDesc}>100% sản phẩm chính hãng từ những thương hiệu uy tín hàng đầu</p>
            </div>
            <div className={styles.valueItem}>
              <div className={styles.valueIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <rect x="1" y="3" width="15" height="13" />
                  <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
                  <circle cx="5.5" cy="18.5" r="2.5" />
                  <circle cx="18.5" cy="18.5" r="2.5" />
                </svg>
              </div>
              <h3 className={styles.valueTitle}>Miễn phí vận chuyển</h3>
              <p className={styles.valueDesc}>Giao hàng miễn phí toàn quốc cho đơn từ 1.000.000đ</p>
            </div>
            <div className={styles.valueItem}>
              <div className={styles.valueIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <polyline points="23 4 23 10 17 10" />
                  <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
                </svg>
              </div>
              <h3 className={styles.valueTitle}>Đổi trả dễ dàng</h3>
              <p className={styles.valueDesc}>Chính sách đổi trả linh hoạt trong 30 ngày</p>
            </div>
            <div className={styles.valueItem}>
              <div className={styles.valueIcon}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72" />
                </svg>
              </div>
              <h3 className={styles.valueTitle}>Hỗ trợ 24/7</h3>
              <p className={styles.valueDesc}>Đội ngũ tư vấn chuyên nghiệp sẵn sàng hỗ trợ bạn</p>
            </div>
          </div>
        </div>
      </section>

      {/* ===== ALL PRODUCTS PREVIEW ===== */}
      <section className={styles.productsSection}>
        <div className={styles.container}>
          <div className={styles.sectionHeader}>
            <div>
              <h2 className={styles.sectionTitle}>Sản phẩm</h2>
              <p className={styles.sectionSubtitle}>
                Khám phá toàn bộ bộ sưu tập của chúng tôi
              </p>
            </div>
            <Link href="/products">
              <Button variant="tertiary">
                Xem tất cả →
              </Button>
            </Link>
          </div>
          <div className={styles.productsGrid}>
            {allProducts.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        </div>
      </section>
    </>
  );
}
