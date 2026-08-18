package com.smartevent.ticketing.modules.event.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import com.smartevent.ticketing.common.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_seats")
@Getter
@Setter
@NoArgsConstructor
public class EventSeat extends BaseEntity {

    @Column(name = "event_area_id", nullable = false)
    private UUID eventAreaId;

    @Column(name = "row_name", nullable = false, length = 50)
    private String rowName;

    @Column(name = "seat_number", nullable = false, length = 50)
    private String seatNumber;

    @Column(name = "label", length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    public EventSeat(UUID eventAreaId, String rowName, String seatNumber, String label, SeatStatus status, Instant holdExpiresAt) {
        this.eventAreaId = eventAreaId;
        this.rowName = rowName;
        this.seatNumber = seatNumber;
        this.label = label;
        this.status = status;
        this.holdExpiresAt = holdExpiresAt;
    }
}
