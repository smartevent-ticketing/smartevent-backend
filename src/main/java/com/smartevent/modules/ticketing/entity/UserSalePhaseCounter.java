package com.smartevent.modules.ticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sale_phase_counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_sale_phase", columnNames = {"user_id", "sale_phase_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserSalePhaseCounter {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sale_phase_id", nullable = false)
    private UUID salePhaseId;

    @Column(name = "held_quantity", nullable = false)
    private Integer heldQuantity = 0;

    @Column(name = "purchased_quantity", nullable = false)
    private Integer purchasedQuantity = 0;

    @Column(name = "refunded_quantity", nullable = false)
    private Integer refundedQuantity = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public UserSalePhaseCounter(UUID userId, UUID salePhaseId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.salePhaseId = salePhaseId;
        this.heldQuantity = 0;
        this.purchasedQuantity = 0;
        this.refundedQuantity = 0;
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
    // Helper tính số vé thực tế người dùng đang chiếm dụng
    public int getEffectiveOccupiedQuantity() {
        return heldQuantity + purchasedQuantity - refundedQuantity;
    }

}
