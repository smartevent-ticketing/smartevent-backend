package com.smartevent.modules.ticketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_phase_rules")
@Getter
@Setter
@NoArgsConstructor
public class TicketPhaseRule {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "sale_phase_id", nullable = false)
    private UUID salePhaseId;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @Column(name = "rule_value", columnDefinition = "TEXT")
    private String ruleValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public TicketPhaseRule(UUID salePhaseId, String ruleType, String ruleValue) {
        this.salePhaseId = salePhaseId;
        this.ruleType = ruleType;
        this.ruleValue = ruleValue;
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
}