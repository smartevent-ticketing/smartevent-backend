package com.smartevent.modules.event.dto.response;

import com.smartevent.common.enums.SeatStatus;
import com.smartevent.modules.event.entity.EventSeat;

import java.time.Instant;
import java.util.UUID;

public record EventSeatResponse(
        UUID id,
        UUID eventAreaId,
        String rowName,
        String seatNumber,
        String label,
        SeatStatus status,
        Instant holdExpiresAt
) {
    public static EventSeatResponse from(EventSeat seat) {
        return new EventSeatResponse(
                seat.getId(),
                seat.getEventAreaId(),
                seat.getRowName(),
                seat.getSeatNumber(),
                seat.getLabel(),
                seat.getStatus(),
                seat.getHoldExpiresAt()
        );
    }
}
