package com.smartevent.ticketing.modules.event.dto.response;

import com.smartevent.ticketing.modules.event.entity.Venue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VenueResponse (
        UUID id,
        String name,
        String address,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        Integer capacity,
        String status,
        Instant createdAt
) {
    public static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getAddress(),
                venue.getCity(),
                venue.getLatitude(),
                venue.getLongitude(),
                venue.getCapacity(),
                venue.getStatus(),
                venue.getCreatedAt());
    }
}
