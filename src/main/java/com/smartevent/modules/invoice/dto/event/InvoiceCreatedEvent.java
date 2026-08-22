package com.smartevent.modules.invoice.dto.event;

import com.smartevent.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceCreatedEvent(
        UUID eventId,
        UUID invoiceId,
        UUID deliveryId,
        String invoiceCode,
        UUID orderId,
        UUID userId,
        String billingEmail,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal feeAmount,
        BigDecimal totalAmount,
        Instant occurredAt
) implements DomainEvent {

    public InvoiceCreatedEvent(UUID invoiceId, UUID deliveryId, String invoiceCode, UUID orderId, UUID userId,
                               String billingEmail, BigDecimal subtotal, BigDecimal discountAmount,
                               BigDecimal feeAmount, BigDecimal totalAmount) {
        this(UUID.randomUUID(), invoiceId, deliveryId, invoiceCode, orderId, userId, billingEmail, subtotal, discountAmount, feeAmount, totalAmount, Instant.now());
    }

    public InvoiceCreatedEvent(UUID invoiceId, UUID deliveryId, String invoiceCode, UUID orderId, UUID userId, String billingEmail, BigDecimal totalAmount) {
        this(invoiceId, deliveryId, invoiceCode, orderId, userId, billingEmail, totalAmount, BigDecimal.ZERO, BigDecimal.ZERO, totalAmount);
    }

    public InvoiceCreatedEvent(UUID invoiceId, String invoiceCode, UUID orderId, UUID userId, String billingEmail, BigDecimal totalAmount) {
        this(invoiceId, null, invoiceCode, orderId, userId, billingEmail, totalAmount, BigDecimal.ZERO, BigDecimal.ZERO, totalAmount);
    }

    @Override
    public String eventType() {
        return "INVOICE_CREATED";
    }
}
