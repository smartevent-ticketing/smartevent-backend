package com.smartevent.modules.event.dto.response;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.modules.event.entity.Event;

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
        Integer maxTicketsPerUser,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        BigDecimal price,
        String currency,
        UUID ticketTypeId,
        UUID salePhaseId,
        Instant priceObservedAt,
        String priceBasis
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
                event.getMaxTicketsPerUser(),
                event.getPublishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt(), null, null, null, null, null, null
        );
    }

    public EventResponse withPrice(PriceEvidence evidence, Instant observedAt) {
        if (evidence == null) return this;
        return new EventResponse(id, organizerId, name, slug, description, venue, categories, files,
            startTime, endTime, status, city, resaleEnabled, maxResalePriceMultiplier, resaleDeadlineHoursBefore,
            virtualQueueEnabled, queueBatchSize, maxTicketsPerUser, publishedAt, createdAt, updatedAt, evidence.price(), "VND",
            evidence.ticketTypeId(), evidence.salePhaseId(), observedAt, "TICKET_FACE_VALUE");
    }

    public static EventResponse from(Event event) {
        return of(event, null, List.of(), List.of());
    }
}
