package com.smartevent.ticketing.modules.event.dto.response;

import com.smartevent.ticketing.common.enums.EventStatus;
import com.smartevent.ticketing.modules.event.entity.Event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID organizerId,
        String name,
        String slug,
        String description,
        VenueResponse venue,
        List<CategoryResponse> categories,
        List<EventFileResponse> files,
        Instant startTime,
        Instant endTime,
        EventStatus status,
        String city,
        Boolean resaleEnabled,
        BigDecimal maxResalePriceMultiplier,
        Integer resaleDeadlineHoursBefore,
        Boolean virtualQueueEnabled,
        Integer queueBatchSize,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static EventResponse of(
            Event event,
            VenueResponse venue,
            List<CategoryResponse> categories,
            List<EventFileResponse> files
    ) {
        return new EventResponse(
                event.getId(),
                event.getOrganizerId(),
                event.getName(),
                event.getSlug(),
                event.getDescription(),
                venue,
                categories,
                files,
                event.getStartTime(),
                event.getEndTime(),
                event.getStatus(),
                event.getCity(),
                event.getResaleEnabled(),
                event.getMaxResalePriceMultiplier(),
                event.getResaleDeadlineHoursBefore(),
                event.getVirtualQueueEnabled(),
                event.getQueueBatchSize(),
                event.getPublishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}