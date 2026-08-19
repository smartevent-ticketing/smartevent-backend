package com.smartevent.modules.ticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_counters")
@Getter
@Setter
@NoArgsConstructor
public class InventoryCounter {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_area_id", nullable = false)
    private UUID eventAreaId;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "sale_phase_id", nullable = false, unique = true)
    private UUID salePhaseId;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "held_quantity", nullable = false)
    private Integer heldQuantity = 0;

    @Column(name = "sold_quantity", nullable = false)
    private Integer soldQuantity = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InventoryCounter(UUID eventId, UUID eventAreaId, UUID ticketTypeId, UUID salePhaseId, Integer totalQuantity) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventAreaId = eventAreaId;
        this.ticketTypeId = ticketTypeId;
        this.salePhaseId = salePhaseId;
        this.totalQuantity = totalQuantity;
        this.heldQuantity = 0;
        this.soldQuantity = 0;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        this.updatedAt = Instant.now();
    }

    // Helper tính nhanh số vé thực tế còn mua được
    public int getAvailableQuantity() {
        return totalQuantity - heldQuantity - soldQuantity;
    }
}