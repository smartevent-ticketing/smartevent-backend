package com.smartevent.modules.reservation.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    public Reservation(UUID userId, UUID eventId, Instant expiresAt, String idempotencyKey) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = ReservationStatus.PENDING;
        this.expiresAt = expiresAt;
        this.idempotencyKey = idempotencyKey;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }
    public boolean isPending() {
        return this.status == ReservationStatus.PENDING;
    }
}
