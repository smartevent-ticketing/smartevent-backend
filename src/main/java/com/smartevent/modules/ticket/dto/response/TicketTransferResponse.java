package com.smartevent.modules.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TicketTransferResponse(
        UUID transferId,
        UUID ticketId,
        String ticketCode,
        UUID fromUserId,
        UUID toUserId,
        String toUserEmail,
        String status,
        Instant transferredAt
) {}