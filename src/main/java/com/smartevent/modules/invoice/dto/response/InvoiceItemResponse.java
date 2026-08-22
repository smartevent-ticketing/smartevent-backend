package com.smartevent.modules.invoice.dto.response;

import com.smartevent.modules.invoice.entity.InvoiceItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceItemResponse(
        UUID id,
        UUID orderItemId,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Instant createdAt
) {
    public static InvoiceItemResponse fromEntity(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getOrderItemId(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getCreatedAt()
        );
    }
}