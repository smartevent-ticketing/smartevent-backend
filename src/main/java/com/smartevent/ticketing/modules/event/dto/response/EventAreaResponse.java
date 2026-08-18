package com.smartevent.ticketing.modules.event.dto.response;

import com.smartevent.ticketing.common.enums.AreaType;
import com.smartevent.ticketing.modules.event.entity.EventArea;

import java.time.Instant;
import java.util.UUID;

public record EventAreaResponse(
        UUID id,
        UUID eventId,
        String name,
        AreaType areaType,
        Integer capacity,
        Integer sortOrder,
        String description,
        long totalSeatsConfigured, // Số lượng ghế thực tế đã sinh trong DB
        Instant createdAt,
        Instant updatedAt
) {
    public static EventAreaResponse of(EventArea area, long totalSeatsConfigured) {
        return new EventAreaResponse(
                area.getId(),
                area.getEventId(),
                area.getName(),
                area.getAreaType(),
                area.getCapacity(),
                area.getSortOrder(),
                area.getDescription(),
                totalSeatsConfigured,
                area.getCreatedAt(),
                area.getUpdatedAt()
        );
    }
}