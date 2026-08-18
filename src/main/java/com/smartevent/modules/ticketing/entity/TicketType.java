package com.smartevent.modules.ticketing.entity;

import com.smartevent.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "ticket_types")
@Getter
@Setter
@NoArgsConstructor
public class TicketType extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_area_id", nullable = false)
    private UUID eventAreaId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    public TicketType(UUID eventId, UUID eventAreaId, String name, String description, String status) {
        this.eventId = eventId;
        this.eventAreaId = eventAreaId;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : "ACTIVE";
    }
}

