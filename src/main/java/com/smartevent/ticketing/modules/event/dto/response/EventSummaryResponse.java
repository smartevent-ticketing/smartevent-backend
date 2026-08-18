package com.smartevent.ticketing.modules.event.dto.response;

import com.smartevent.ticketing.common.enums.EventStatus;
import com.smartevent.ticketing.modules.event.entity.Event;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryResponse(
        UUID id,
        String name,
        String slug,
        String venueName,
        String city,
        UUID bannerFileId,
        Instant startTime,
        Instant endTime,
        EventStatus status
) {
    public static EventSummaryResponse of(Event event, String venueName, UUID bannerFileId) {
        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getSlug(),
                venueName,
                event.getCity(),
                bannerFileId,
                event.getStartTime(),
                event.getEndTime(),
                event.getStatus()
        );
    }
}