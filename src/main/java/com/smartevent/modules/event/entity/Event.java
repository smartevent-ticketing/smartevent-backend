package com.smartevent.modules.event.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.EventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events")
public class Event extends BaseEntity {
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Column(name = "resale_enabled", nullable = false)
    private Boolean resaleEnabled = false;

    @Column(name = "max_resale_price_multiplier", precision = 5, scale = 2)
    private BigDecimal maxResalePriceMultiplier;

    @Column(name = "resale_deadline_hours_before")
    private Integer resaleDeadlineHoursBefore;

    @Column(name = "virtual_queue_enabled", nullable = false)
    private Boolean virtualQueueEnabled = false;

    @Column(name = "queue_batch_size")
    private Integer queueBatchSize = 50;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "published_at")
    private Instant publishedAt;
}

