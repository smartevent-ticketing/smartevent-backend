package com.smartevent.modules.invoice.support;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class PdfInvoiceGenerator {

    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    public byte[] generateInvoicePdf(Invoice invoice, List<InvoiceItem> items, String buyerName, String buyerEmail) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);
            document.open();

            // Cấu hình Font hỗ trợ đầy đủ Tiếng Việt Unicode UTF-8
            com.lowagie.text.pdf.BaseFont baseFont;
            try {
                baseFont = com.lowagie.text.pdf.BaseFont.createFont("C:/Windows/Fonts/arial.ttf", com.lowagie.text.pdf.BaseFont.IDENTITY_H, com.lowagie.text.pdf.BaseFont.EMBEDDED);
            } catch (Exception e) {
                baseFont = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA, com.lowagie.text.pdf.BaseFont.WINANSI, com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
            }

            Font titleFont = new Font(baseFont, 18, Font.BOLD);
            Font headerFont = new Font(baseFont, 10, Font.BOLD);
            Font bodyFont = new Font(baseFont, 9, Font.NORMAL);
            Font totalFont = new Font(baseFont, 11, Font.BOLD);
            Font metaFont = new Font(baseFont, 10, Font.NORMAL);

            // 1. Header
            Paragraph title = new Paragraph("HÓA ĐƠN ĐIỆN TỬ / ELECTRONIC INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph company = new Paragraph("SMART EVENT TICKETING PLATFORM - MST: 0101234567\n", new Font(baseFont, 10, Font.NORMAL));
            company.setAlignment(Element.ALIGN_CENTER);
            document.add(company);

            document.add(new Paragraph("\n"));

            // 2. Metadata Info
            document.add(new Paragraph("Mã Hóa Đơn (Invoice Code): " + invoice.getInvoiceCode(), metaFont));
            document.add(new Paragraph("Ngày Phát Hành (Issued Date): " + (invoice.getIssuedAt() != null ? dateFormatter.format(invoice.getIssuedAt()) : "N/A"), metaFont));
            document.add(new Paragraph("Khách Hàng (Customer): " + buyerName + " (" + buyerEmail + ")", metaFont));
            document.add(new Paragraph("Trạng Thái (Status): " + invoice.getStatus().name(), metaFont));
            document.add(new Paragraph("\n"));

            // 3. Line Items Table
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4f, 1f, 2f, 2f});

            // Table Header
            addHeaderCell(table, "Mô tả sản phẩm (Description)", headerFont);
            addHeaderCell(table, "SL (Qty)", headerFont);
            addHeaderCell(table, "Đơn giá (Unit Price)", headerFont);
            addHeaderCell(table, "Thành tiền (Amount)", headerFont);

            // Table Rows
            for (InvoiceItem item : items) {
                table.addCell(new Phrase(item.getDescription(), bodyFont));
                table.addCell(new Phrase(String.valueOf(item.getQuantity()), bodyFont));
                table.addCell(new Phrase(currencyFormatter.format(item.getUnitPrice()), bodyFont));
                table.addCell(new Phrase(currencyFormatter.format(item.getTotalPrice()), bodyFont));
            }

            document.add(table);
            document.add(new Paragraph("\n"));

            // 4. Financial Summary
            Paragraph total = new Paragraph("TỔNG CỘNG THANH TOÁN (TOTAL AMOUNT): " + currencyFormatter.format(invoice.getTotalAmount()), totalFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi sinh hóa đơn PDF: " + e.getMessage(), e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }
}
