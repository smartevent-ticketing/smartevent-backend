package com.smartevent.modules.ticket.entity;

import com.smartevent.common.enums.CheckinResult;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_checkins")
@Getter
@Setter
@NoArgsConstructor
public class TicketCheckin {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "checked_by_user_id")
    private UUID checkedByUserId;

    @Column(name = "gate_name", length = 100)
    private String gateName;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private CheckinResult result; // SUCCESS, INVALID, DUPLICATE

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt;

    public TicketCheckin(UUID ticketId, UUID eventId, UUID checkedByUserId, String gateName, CheckinResult result) {
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.checkedByUserId = checkedByUserId;
        this.gateName = gateName;
        this.result = result;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.checkedAt == null) {
            this.checkedAt = Instant.now();
        }
    }
}