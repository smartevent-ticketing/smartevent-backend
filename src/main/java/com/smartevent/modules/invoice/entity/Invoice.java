package com.smartevent.modules.invoice.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "invoice_code", nullable = false, unique = true, length = 100)
    private String invoiceCode;

    @Column(name = "billing_email", nullable = false)
    private String billingEmail;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "file_id")
    private UUID fileId; // Liên kết tới file PDF hóa đơn lưu trên MinIO (nếu có)

    public Invoice(UUID orderId, UUID userId, String invoiceCode, String billingEmail,
                   BigDecimal subtotal, BigDecimal discountAmount, BigDecimal feeAmount, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.userId = userId;
        this.invoiceCode = invoiceCode;
        this.billingEmail = billingEmail;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.feeAmount = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.status = InvoiceStatus.ISSUED;
        this.issuedAt = Instant.now();
    }
}