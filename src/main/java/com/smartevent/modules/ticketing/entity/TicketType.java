package com.smartevent.modules.ticketing.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.TicketTypeStatus;
import jakarta.persistence.*;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TicketTypeStatus status = TicketTypeStatus.ACTIVE;

    public TicketType(UUID eventId, UUID eventAreaId, String name, String description, TicketTypeStatus status) {
        this.eventId = eventId;
        this.eventAreaId = eventAreaId;
        this.name = name;
        this.description = description;
        this.status = status != null ? status : TicketTypeStatus.ACTIVE;
    }

}

