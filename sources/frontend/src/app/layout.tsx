import type { Metadata } from "next";
import { Be_Vietnam_Pro } from "next/font/google";
import "./globals.css";
import Header from "@/components/layout/Header/Header";
import Footer from "@/components/layout/Footer/Footer";
import { AuthProvider } from "@/providers/AuthProvider";
import { ToastProvider } from "@/components/ui/Toast/Toast";

const beVietnamPro = Be_Vietnam_Pro({
  variable: "--font-be-vietnam-pro",
  subsets: ["latin", "vietnamese"],
  weight: ["300", "400", "500", "600", "700", "800"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "The Digital Atélier | Mua sắm đẳng cấp",
  description:
    "Trải nghiệm mua sắm kỹ thuật số đẳng cấp. Chúng tôi mang đến sự tinh tế trong từng sản phẩm và niềm tin trong từng giao dịch.",
  keywords: ["thương mại điện tử", "mua sắm online", "thời trang", "phụ kiện"],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi" className={beVietnamPro.variable}>
      <body>
        <AuthProvider>
          <ToastProvider>
            <Header />
            <main style={{ flex: 1 }}>{children}</main>
            <Footer />
          </ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
