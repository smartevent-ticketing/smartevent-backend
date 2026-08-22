package com.smartevent.modules.ticket.dto.event;

import com.smartevent.common.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record TicketIssuedEvent(
        UUID eventId,
        UUID ticketId,
        String ticketCode,
        UUID eventIdRef,
        String eventName,
        UUID ownerUserId,
        String ownerEmail,
        String seatCode,
        String ticketTypeName,
        String qrCodeBase64,
        Instant occurredAt
) implements DomainEvent {

    public TicketIssuedEvent(UUID ticketId, String ticketCode, UUID eventIdRef, String eventName,
                             UUID ownerUserId, String ownerEmail, String seatCode, String ticketTypeName, String qrCodeBase64) {
        this(UUID.randomUUID(), ticketId, ticketCode, eventIdRef, eventName, ownerUserId, ownerEmail, seatCode, ticketTypeName, qrCodeBase64, Instant.now());
    }

    @Override
    public String eventType() {
        return "TICKET_ISSUED";
    }
}
