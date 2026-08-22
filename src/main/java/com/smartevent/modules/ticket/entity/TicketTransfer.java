package com.smartevent.modules.ticket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_transfers")
@Getter
@Setter
@NoArgsConstructor
public class TicketTransfer {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "from_user_id", nullable = false)
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType = "DIRECT_TRANSFER"; // RESALE, ADMIN, REFUND_REISSUE, DIRECT_TRANSFER

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "COMPLETED";

    @Column(name = "transferred_at", nullable = false, updatable = false)
    private Instant transferredAt;

    public TicketTransfer(UUID ticketId, UUID fromUserId, UUID toUserId, String sourceType, UUID sourceId) {
        this.ticketId = ticketId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.sourceType = sourceType != null ? sourceType : "DIRECT_TRANSFER";
        this.sourceId = sourceId;
        this.status = "COMPLETED";
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.transferredAt == null) {
            this.transferredAt = Instant.now();
        }
    }
}