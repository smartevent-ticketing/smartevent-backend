package com.smartevent.modules.ticket.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket extends BaseEntity {
    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "current_owner_user_id", nullable = false)
    private UUID currentOwnerUserId;

    @Column(name = "original_buyer_user_id", nullable = false)
    private UUID originalBuyerUserId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_seat_id")
    private UUID eventSeatId;

    @Column(name = "event_area_id")
    private UUID eventAreaId;

    @Column(name = "ticket_type_id")
    private UUID ticketTypeId;

    @Column(name = "sale_phase_id")
    private UUID salePhaseId;

    @Column(name = "ticket_code", nullable = false, unique = true)
    private String ticketCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketStatus status = TicketStatus.ISSUED;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public Ticket(UUID orderItemId, UUID currentOwnerUserId, UUID originalBuyerUserId,
                  UUID eventId, UUID eventSeatId, UUID eventAreaId, UUID ticketTypeId,
                  UUID salePhaseId, String ticketCode) {
        this.orderItemId = orderItemId;
        this.currentOwnerUserId = currentOwnerUserId;
        this.originalBuyerUserId = originalBuyerUserId;
        this.eventId = eventId;
        this.eventSeatId = eventSeatId;
        this.eventAreaId = eventAreaId;
        this.ticketTypeId = ticketTypeId;
        this.salePhaseId = salePhaseId;
        this.ticketCode = ticketCode;
        this.status = TicketStatus.ISSUED;
        this.issuedAt = Instant.now();
    }
    public boolean isIssued() {
        return this.status == TicketStatus.ISSUED;
    }
    public boolean isUsed() {
        return this.status == TicketStatus.USED;
    }
}
