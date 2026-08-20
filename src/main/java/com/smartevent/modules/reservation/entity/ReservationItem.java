package com.smartevent.modules.reservation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_items")
@Getter
@Setter
@NoArgsConstructor
public class ReservationItem {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "sale_phase_id", nullable = false)
    private UUID salePhaseId;

    @Column(name = "event_seat_id")
    private UUID eventSeatId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReservationItem(UUID reservationId, UUID ticketTypeId, UUID salePhaseId,
                           UUID eventSeatId, Integer quantity, BigDecimal unitPrice) {
        this.reservationId = reservationId;
        this.ticketTypeId = ticketTypeId;
        this.salePhaseId = salePhaseId;
        this.eventSeatId = eventSeatId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }
    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
    public BigDecimal getTotalPrice() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
