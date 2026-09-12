package com.smartevent.modules.ticketing.dto.response;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.TicketTypeStatus;
import com.smartevent.modules.ticketing.entity.TicketType;

import java.time.Instant;
import java.util.UUID;

public record TicketTypeResponse(
        UUID id,
        UUID eventId,
        UUID eventAreaId,
        String areaName,
        AreaType areaType,
        String name,
        String description,
        TicketTypeStatus status,
        Instant createdAt
) {
    public static TicketTypeResponse of(TicketType ticketType, String areaName, AreaType areaType) {
        return new TicketTypeResponse(
                ticketType.getId(),
                ticketType.getEventId(),
                ticketType.getEventAreaId(),
                areaName,
                areaType,
                ticketType.getName(),
                ticketType.getDescription(),
                ticketType.getStatus(),
                ticketType.getCreatedAt()
        );
    }
}

