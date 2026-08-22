package com.smartevent.modules.invoice.dto.response;

import com.smartevent.common.enums.InvoiceStatus;
import com.smartevent.modules.invoice.entity.Invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        String invoiceCode,       // VD: "INV-20260822-ABC12345"
        String billingEmail,
        BigDecimal subtotal,      // Tiền gốc
        BigDecimal discountAmount,// Tiền giảm giá
        BigDecimal feeAmount,     // Phí tiện ích
        BigDecimal totalAmount,   // Tổng thanh toán cuối cùng
        InvoiceStatus status,     // ISSUED, VOID, CANCELLED
        Instant issuedAt,
        UUID fileId,              // ID file PDF hóa đơn trên MinIO (nếu có)
        List<InvoiceItemResponse> items,
        Instant createdAt
) {
    public static InvoiceResponse of(Invoice invoice, List<InvoiceItemResponse> items) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getOrderId(),
                invoice.getUserId(),
                invoice.getInvoiceCode(),
                invoice.getBillingEmail(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getFeeAmount(),
                invoice.getTotalAmount(),
                invoice.getStatus(),
                invoice.getIssuedAt(),
                invoice.getFileId(),
                items,
                invoice.getCreatedAt()
        );
    }
}