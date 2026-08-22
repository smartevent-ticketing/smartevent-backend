package com.smartevent.modules.invoice.support;

import com.smartevent.common.enums.InvoiceStatus;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PdfInvoiceGeneratorTest {

    @Test
    @DisplayName("Sinh file PDF hóa đơn điện tử thực tế và lưu ra file mẫu sample_invoice.pdf")
    void generateInvoicePdf_Success() throws Exception {
        PdfInvoiceGenerator generator = new PdfInvoiceGenerator();

        UUID invoiceId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Invoice invoice = new Invoice(
                orderId,
                userId,
                "INV-20260823-8899AABB",
                "trinhdanghuy29@gmail.com",
                BigDecimal.valueOf(5000000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(5000000)
        );
        invoice.setId(invoiceId);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setIssuedAt(Instant.now());

        InvoiceItem item1 = new InvoiceItem(
                invoiceId,
                UUID.randomUUID(),
                "Vé VIP Diamond - Concert Mỹ Tâm 'My Soul 1981' (Khu VIP - Hàng A - Ghế A-12)",
                1,
                BigDecimal.valueOf(2500000),
                BigDecimal.valueOf(2500000)
        );

        InvoiceItem item2 = new InvoiceItem(
                invoiceId,
                UUID.randomUUID(),
                "Vé VIP Diamond - Concert Mỹ Tâm 'My Soul 1981' (Khu VIP - Hàng A - Ghế A-13)",
                1,
                BigDecimal.valueOf(2500000),
                BigDecimal.valueOf(2500000)
        );

        byte[] pdfBytes = generator.generateInvoicePdf(
                invoice,
                List.of(item1, item2),
                "Trịnh Đăng Huy",
                "trinhdanghuy29@gmail.com"
        );

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 1000, "File PDF phải có dung lượng lớn hơn 1KB");

        // Ghi file PDF mẫu ra thư mục gốc để người dùng có thể mở xem trực tiếp
        Path samplePdfPath = Paths.get("..", "sample_invoice.pdf").toAbsolutePath().normalize();
        try (FileOutputStream fos = new FileOutputStream(samplePdfPath.toFile())) {
            fos.write(pdfBytes);
        }

        System.out.println(">>> Đã sinh thành công file hóa đơn PDF thực tế tại: " + samplePdfPath);
    }
}
