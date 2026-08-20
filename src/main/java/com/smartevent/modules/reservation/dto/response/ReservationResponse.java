package com.smartevent.modules.reservation.dto.response;

import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.modules.reservation.entity.Reservation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID userId,
        UUID eventId,
        String eventName,
        ReservationStatus status,
        Instant expiresAt,
        BigDecimal totalAmount,
        List<ReservationItemResponse> items,
        Instant createdAt
) {
    public static ReservationResponse of(
            Reservation reservation,
            String eventName,
            BigDecimal totalAmount,
            List<ReservationItemResponse> items
    ) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getEventId(),
                eventName,
                reservation.getStatus(),
                reservation.getExpiresAt(),
                totalAmount != null ? totalAmount : BigDecimal.ZERO,
                items != null ? items : List.of(),
                reservation.getCreatedAt()
        );
    }
}