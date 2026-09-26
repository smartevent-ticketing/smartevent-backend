package com.smartevent.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.Locale;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String senderEmail;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    public void sendTicketEmail(String recipientEmail, String ticketCode, String eventName, String seatCode, String qrCodeBase64) {
        byte[] qrImage = decodeQrImage(qrCodeBase64);
        String subject = "🎟️ Vé Điện Tử Cho Sự Kiện: " + eventName + " [Mã Vé: " + ticketCode + "]";
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background-color: #ffffff;">
                <h2 style="color: #4f46e5; text-align: center;">🎟️ THÔNG BÁO VÉ ĐIỆN TỬ SMART EVENT</h2>
                <p>Kính chào quý khách,</p>
                <p>Cảm ơn quý khách đã mua vé tại hệ thống Smart Event Ticketing. Dưới đây là thông tin vé vào cửa của bạn:</p>
                <div style="background-color: #f8fafc; padding: 15px; border-radius: 8px; margin: 15px 0;">
                    <p><strong>Sự kiện:</strong> %s</p>
                    <p><strong>Mã vé:</strong> <span style="color: #4f46e5; font-weight: bold;">%s</span></p>
                    <p><strong>Vị trí ghế:</strong> %s</p>
                </div>
                <div style="text-align: center; margin: 20px 0;">
                    <p><strong>MÃ QR VÀO CỬA (QUÉT TẠI CỔNG):</strong></p>
                    <img src="cid:ticket-qr" alt="Mã QR Vé" style="width: 220px; height: 220px; border: 2px solid #cbd5e1; border-radius: 8px;" />
                </div>
                <p>Nếu không thấy ảnh QR trong email, vui lòng mở tệp PNG đính kèm.</p>
                <p style="color: #dc2626; font-size: 13px; text-align: center;">* Vui lòng bảo mật mã QR này và xuất trình tại cổng soát vé khi đến sự kiện.</p>
            </div>
            """.formatted(HtmlUtils.htmlEscape(eventName), HtmlUtils.htmlEscape(ticketCode),
                    HtmlUtils.htmlEscape(seatCode != null ? seatCode : "Khu Tự Do (GA)"));

        sendHtmlEmailWithAttachment(recipientEmail, subject, htmlContent,
                "Ve_" + ticketCode + ".png", qrImage, qrImage);
    }

    public void sendInvoiceEmail(String recipientEmail, String invoiceCode, BigDecimal totalAmount, String billingDate) {
        sendInvoiceEmailWithPdf(recipientEmail, invoiceCode, totalAmount, billingDate, null);
    }

    public void sendInvoiceEmailWithPdf(String recipientEmail, String invoiceCode, BigDecimal totalAmount, String billingDate, byte[] pdfAttachment) {
        String formattedAmount = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalAmount);
        String subject = "🧾 Xác Nhận Thanh Toán Đơn Hàng #" + invoiceCode + " - Smart Event";
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background-color: #ffffff;">
                <h2 style="color: #059669; text-align: center;">🧾 XÁC NHẬN THANH TOÁN SMART EVENT</h2>
                <p>Kính chào quý khách,</p>
                <p>Giao dịch mua vé của quý khách đã được xác nhận thanh toán thành công.</p>
                <div style="background-color: #f0fdf4; padding: 15px; border-radius: 8px; margin: 15px 0;">
                    <p><strong>Mã chứng từ đơn hàng:</strong> <span style="color: #059669; font-weight: bold;">%s</span></p>
                    <p><strong>Thời gian xuất:</strong> %s</p>
                    <p><strong>Tổng tiền thanh toán:</strong> <span style="font-size: 18px; font-weight: bold; color: #dc2626;">%s</span></p>
                </div>
                <p>Tệp PDF tổng hợp thông tin đơn hàng được đính kèm trong email này. Đây chưa phải hóa đơn thuế có ký số.</p>
            </div>
            """.formatted(invoiceCode, billingDate, formattedAmount);

        sendHtmlEmailWithAttachment(recipientEmail, subject, htmlContent, "HoaDon_" + invoiceCode + ".pdf", pdfAttachment, null);
    }

    private void sendHtmlEmailWithAttachment(String toEmail, String subject, String htmlBody,
                                             String attachmentFilename, byte[] attachmentBytes, byte[] inlineQrImage) {
        if (!mailEnabled) throw new MailSendException("Email delivery is disabled; message was not sent");
        if (mailSender == null) throw new MailSendException("SMTP sender is unavailable; message was not sent");
        if (!StringUtils.hasText(senderEmail)) throw new MailSendException("SMTP sender address is not configured");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(senderEmail, "Smart Event Ticketing Platform");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (inlineQrImage != null) {
                helper.addInline("ticket-qr", new ByteArrayResource(inlineQrImage), "image/png");
            }

            if (attachmentBytes != null && attachmentFilename != null) {
                helper.addAttachment(attachmentFilename, new ByteArrayResource(attachmentBytes));
            }

            mailSender.send(message);
            log.info("Đã gửi email thành công tới: {}", toEmail);
        } catch (Exception ex) {
            log.error("Không thể gửi email tới {}: {}", toEmail, ex.getMessage());
            throw new RuntimeException("Lỗi gửi email: " + ex.getMessage(), ex);
        }
    }

    private byte[] decodeQrImage(String dataUrl) {
        String prefix = "data:image/png;base64,";
        if (!StringUtils.hasText(dataUrl) || !dataUrl.startsWith(prefix)) {
            throw new IllegalArgumentException("QR image must be a PNG data URL");
        }
        try {
            byte[] image = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
            if (image.length < 8 || image.length > 1024 * 1024
                    || image[0] != (byte) 0x89 || image[1] != 'P' || image[2] != 'N' || image[3] != 'G') {
                throw new IllegalArgumentException("Invalid QR PNG image");
            }
            return image;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid QR PNG image", ex);
        }
    }
}
