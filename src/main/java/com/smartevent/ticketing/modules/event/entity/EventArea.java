package com.smartevent.ticketing.modules.event.entity;

import com.smartevent.ticketing.common.entity.BaseEntity;
import com.smartevent.ticketing.common.enums.AreaType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "event_areas")
@Getter
@Setter
@NoArgsConstructor
public class EventArea extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "area_type", nullable = false, length = 30)
    private AreaType areaType = AreaType.STANDING;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;


    public EventArea(UUID eventId, String name, AreaType areaType, Integer capacity, Integer sortOrder, String description) {
        this.eventId = eventId;
        this.name = name;
        this.areaType = areaType;
        this.capacity = capacity;
        this.sortOrder = sortOrder;
        this.description = description;
    }
}
