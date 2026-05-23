package com.ptit.htpt.notificationservice.service.email;

import java.util.Map;

/**
 * Phase 27 / Plan 27-03: 6 template HTML tiếng Việt cho email notification.
 *
 * <p>Mỗi hằng override 2 abstract method: subject() và render(Map vars).
 * HTML inline-CSS, lang="vi", font Arial, max-width 600px, màu accent #2563eb.
 * Dùng Java text block + .formatted() — KHÔNG template engine ngoài (D-09).
 *
 * <p>Vars map keys cho mỗi template:
 * - ACCOUNT_VERIFICATION: fullName, verifyUrl
 * - PASSWORD_RESET: fullName, resetUrl
 * - ORDER_CONFIRMATION: orderId, totalAmount, currency, itemCount
 * - ORDER_SHIPPED: orderId
 * - ORDER_DELIVERED: orderId
 * - ORDER_CANCELLED: orderId
 */
public enum MailTemplate {

    ACCOUNT_VERIFICATION {
        @Override
        public String subject() {
            return "Xác minh địa chỉ email của bạn";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Xác minh email</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#2563eb;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Xác minh địa chỉ email</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">Xin chào <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;color:#374151;font-size:14px;">
                                Cảm ơn bạn đã đăng ký tài khoản. Vui lòng nhấn nút bên dưới để xác minh địa chỉ email của bạn.
                              </p>
                              <p style="text-align:center;margin:0 0 24px;">
                                <a href="%s"
                                   style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:6px;font-size:15px;font-weight:bold;">
                                  Xác minh email
                                </a>
                              </p>
                              <p style="margin:0;color:#6b7280;font-size:12px;">
                                Liên kết có hiệu lực trong <strong>24 giờ</strong>. Nếu bạn không tạo tài khoản, hãy bỏ qua email này.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "fullName"), esc(vars, "verifyUrl"));
        }
    },

    PASSWORD_RESET {
        @Override
        public String subject() {
            return "Đặt lại mật khẩu";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Đặt lại mật khẩu</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#2563eb;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Đặt lại mật khẩu</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">Xin chào <strong>%s</strong>,</p>
                              <p style="margin:0 0 24px;color:#374151;font-size:14px;">
                                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Nhấn nút bên dưới để tiếp tục.
                              </p>
                              <p style="text-align:center;margin:0 0 24px;">
                                <a href="%s"
                                   style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;padding:12px 28px;border-radius:6px;font-size:15px;font-weight:bold;">
                                  Đặt lại mật khẩu
                                </a>
                              </p>
                              <p style="margin:0;color:#6b7280;font-size:12px;">
                                Liên kết có hiệu lực trong <strong>1 giờ</strong>. Nếu bạn không yêu cầu, hãy bỏ qua email này.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "fullName"), esc(vars, "resetUrl"));
        }
    },

    ORDER_CONFIRMATION {
        @Override
        public String subject() {
            return "Xác nhận đơn hàng";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Xác nhận đơn hàng</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#2563eb;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Xác nhận đơn hàng</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">
                                Cảm ơn bạn đã đặt hàng!
                              </p>
                              <table width="100%%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:6px;margin-bottom:24px;">
                                <tr>
                                  <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;background:#f9fafb;">
                                    <span style="color:#6b7280;font-size:13px;">Mã đơn hàng</span>
                                  </td>
                                  <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;text-align:right;">
                                    <strong style="color:#111827;font-size:14px;">%s</strong>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;background:#f9fafb;">
                                    <span style="color:#6b7280;font-size:13px;">Số sản phẩm</span>
                                  </td>
                                  <td style="padding:12px 16px;border-bottom:1px solid #e5e7eb;text-align:right;">
                                    <strong style="color:#111827;font-size:14px;">%s sản phẩm</strong>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:12px 16px;background:#f9fafb;">
                                    <span style="color:#6b7280;font-size:13px;">Tổng tiền</span>
                                  </td>
                                  <td style="padding:12px 16px;text-align:right;">
                                    <strong style="color:#2563eb;font-size:16px;">%s %s</strong>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:0;color:#6b7280;font-size:12px;">
                                Đơn hàng của bạn đang được xử lý. Chúng tôi sẽ thông báo khi đơn hàng được giao.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "orderId"), esc(vars, "itemCount"), esc(vars, "totalAmount"), esc(vars, "currency"));
        }
    },

    ORDER_SHIPPED {
        @Override
        public String subject() {
            return "Đơn hàng đang được giao";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Đơn hàng đang được giao</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#2563eb;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Đơn hàng đang được giao</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">
                                Đơn hàng <strong>#%s</strong> của bạn đã được chuyển sang trạng thái đang giao.
                              </p>
                              <p style="margin:0;color:#374151;font-size:14px;">
                                Vui lòng chuẩn bị để nhận hàng. Nếu bạn có câu hỏi, hãy liên hệ bộ phận hỗ trợ khách hàng.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "orderId"));
        }
    },

    ORDER_DELIVERED {
        @Override
        public String subject() {
            return "Đơn hàng đã giao thành công";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Đơn hàng đã giao thành công</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#16a34a;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Đơn hàng đã giao thành công</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">
                                Đơn hàng <strong>#%s</strong> đã được giao thành công!
                              </p>
                              <p style="margin:0;color:#374151;font-size:14px;">
                                Cảm ơn bạn đã tin tưởng mua sắm tại cửa hàng của chúng tôi. Chúc bạn hài lòng với sản phẩm.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "orderId"));
        }
    },

    ORDER_CANCELLED {
        @Override
        public String subject() {
            return "Đơn hàng đã bị huỷ";
        }

        @Override
        public String render(Map<String, String> vars) {
            return """
                    <!DOCTYPE html>
                    <html lang="vi">
                    <head>
                      <meta charset="UTF-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1.0">
                      <title>Đơn hàng đã bị huỷ</title>
                    </head>
                    <body style="margin:0;padding:0;background:#f4f4f5;font-family:Arial,sans-serif;">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 16px;">
                        <tr><td align="center">
                          <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;background:#ffffff;border-radius:8px;overflow:hidden;">
                            <tr><td style="background:#dc2626;padding:24px 32px;">
                              <h1 style="margin:0;color:#ffffff;font-size:20px;">Đơn hàng đã bị huỷ</h1>
                            </td></tr>
                            <tr><td style="padding:32px;">
                              <p style="margin:0 0 16px;color:#111827;font-size:16px;">
                                Đơn hàng <strong>#%s</strong> của bạn đã bị huỷ.
                              </p>
                              <p style="margin:0;color:#374151;font-size:14px;">
                                Nếu bạn có thắc mắc về đơn hàng này, vui lòng liên hệ bộ phận hỗ trợ khách hàng để được giải đáp.
                              </p>
                            </td></tr>
                          </table>
                        </td></tr>
                      </table>
                    </body>
                    </html>
                    """.formatted(esc(vars, "orderId"));
        }
    };

    public abstract String subject();

    public abstract String render(Map<String, String> vars);

    /**
     * CR-01: HTML-escape giá trị user-supplied trước khi chèn vào template.
     * Chống HTML/script injection khi fullName (hoặc field khác) chứa ký tự đặc biệt.
     * Trả "" nếu key không tồn tại — tránh in chuỗi "null" vào email.
     */
    protected static String esc(Map<String, String> vars, String key) {
        String value = vars.get(key);
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
