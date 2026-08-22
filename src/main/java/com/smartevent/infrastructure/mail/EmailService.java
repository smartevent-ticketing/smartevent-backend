package com.smartevent.infrastructure.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendTicketEmail(String recipientEmail, String ticketCode, String eventName, String seatCode, String qrCodeBase64) {
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
                    <img src="%s" alt="Mã QR Vé" style="width: 220px; height: 220px; border: 2px solid #cbd5e1; border-radius: 8px;" />
                </div>
                <p style="color: #dc2626; font-size: 13px; text-align: center;">* Vui lòng bảo mật mã QR này và xuất trình tại cổng soát vé khi đến sự kiện.</p>
            </div>
            """.formatted(eventName, ticketCode, (seatCode != null ? seatCode : "Khu Tự Do (GA)"), qrCodeBase64);

        sendHtmlEmail(recipientEmail, subject, htmlContent);
    }

    public void sendInvoiceEmail(String recipientEmail, String invoiceCode, BigDecimal totalAmount, String billingDate) {
        sendInvoiceEmailWithPdf(recipientEmail, invoiceCode, totalAmount, billingDate, null);
    }

    public void sendInvoiceEmailWithPdf(String recipientEmail, String invoiceCode, BigDecimal totalAmount, String billingDate, byte[] pdfAttachment) {
        String formattedAmount = NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(totalAmount);
        String subject = "🧾 Hóa Đơn Điện Tử Đơn Hàng #" + invoiceCode + " - Smart Event";
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 12px; background-color: #ffffff;">
                <h2 style="color: #059669; text-align: center;">🧾 HÓA ĐƠN ĐIỆN TỬ (VAT INVOICE)</h2>
                <p>Kính chào quý khách,</p>
                <p>Giao dịch mua vé của quý khách đã được xác nhận thanh toán thành công.</p>
                <div style="background-color: #f0fdf4; padding: 15px; border-radius: 8px; margin: 15px 0;">
                    <p><strong>Mã tra cứu hóa đơn:</strong> <span style="color: #059669; font-weight: bold;">%s</span></p>
                    <p><strong>Thời gian xuất:</strong> %s</p>
                    <p><strong>Tổng tiền thanh toán:</strong> <span style="font-size: 18px; font-weight: bold; color: #dc2626;">%s</span></p>
                </div>
                <p>Hóa đơn điện tử PDF chính thức đã được đính kèm trực tiếp trong email này.</p>
            </div>
            """.formatted(invoiceCode, billingDate, formattedAmount);

        sendHtmlEmailWithAttachment(recipientEmail, subject, htmlContent, "HoaDon_" + invoiceCode + ".pdf", pdfAttachment);
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlBody) {
        sendHtmlEmailWithAttachment(toEmail, subject, htmlBody, null, null);
    }

    private void sendHtmlEmailWithAttachment(String toEmail, String subject, String htmlBody, String attachmentFilename, byte[] attachmentBytes) {
        if (mailSender == null) {
            log.info("[MOCK MAIL SENDER] Sẽ gửi email tới: {} | Tiêu đề: {}", toEmail, subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("smartevent.tickets@gmail.com", "Smart Event Ticketing Platform");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            if (attachmentBytes != null && attachmentFilename != null) {
                helper.addAttachment(attachmentFilename, new org.springframework.core.io.ByteArrayResource(attachmentBytes));
            }

            mailSender.send(message);
            log.info("Đã gửi email thành công tới: {}", toEmail);
        } catch (Exception ex) {
            log.error("Không thể gửi email tới {}: {}", toEmail, ex.getMessage());
            throw new RuntimeException("Lỗi gửi email: " + ex.getMessage(), ex);
        }
    }
}
