package com.smartevent.modules.ordering.dto.event;

import com.smartevent.common.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderPaidEvent(
        UUID eventId,
        UUID orderId,
        String orderCode,
        UUID userId,
        String customerEmail,
        BigDecimal totalAmount,
        Instant occurredAt
) implements DomainEvent {

    public OrderPaidEvent(UUID orderId, String orderCode, UUID userId, String customerEmail, BigDecimal totalAmount) {
        this(UUID.randomUUID(), orderId, orderCode, userId, customerEmail, totalAmount, Instant.now());
    }

    @Override
    public String eventType() {
        return "ORDER_PAID";
    }
}
