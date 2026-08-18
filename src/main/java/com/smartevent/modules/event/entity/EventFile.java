package com.smartevent.modules.event.entity;

import com.smartevent.common.enums.EventFileType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "event_files")
public class EventFile {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 30)
    private EventFileType fileType;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public EventFile(UUID eventId, UUID fileId, EventFileType fileType, Integer sortOrder) {
        this.eventId = eventId;
        this.fileId = fileId;
        this.fileType = fileType;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }
}

