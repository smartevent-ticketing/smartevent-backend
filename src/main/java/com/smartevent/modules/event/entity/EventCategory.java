package com.smartevent.modules.event.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "event_categories")
public class EventCategory {

    @EmbeddedId
    private EventCategoryId id;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class EventCategoryId implements Serializable {

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "category_id", nullable = false)
        private UUID categoryId;
    }

    public EventCategory(UUID eventId, UUID categoryId) {
        this.id = new EventCategoryId(eventId, categoryId);
    }
}

