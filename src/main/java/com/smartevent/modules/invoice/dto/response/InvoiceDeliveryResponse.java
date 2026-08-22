package com.smartevent.modules.invoice.dto.response;

import com.smartevent.common.enums.DeliveryStatus;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;

import java.time.Instant;
import java.util.UUID;

public record InvoiceDeliveryResponse(
        UUID id,
        UUID invoiceId,
        String channel,
        String recipientEmail,
        DeliveryStatus status,
        Instant sentAt,
        String providerMessageId,
        Instant createdAt
) {
    public static InvoiceDeliveryResponse fromEntity(InvoiceDelivery delivery) {
        return new InvoiceDeliveryResponse(
                delivery.getId(),
                delivery.getInvoiceId(),
                delivery.getChannel(),
                delivery.getRecipientEmail(),
                delivery.getStatus(),
                delivery.getSentAt(),
                delivery.getProviderMessageId(),
                delivery.getCreatedAt()
        );
    }
}