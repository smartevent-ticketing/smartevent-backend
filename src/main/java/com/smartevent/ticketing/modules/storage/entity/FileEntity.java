package com.smartevent.ticketing.modules.storage.entity;

import com.smartevent.ticketing.common.enums.FileScanStatus;
import com.smartevent.ticketing.common.enums.FileVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
public class FileEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "bucket_name", nullable = false, length = 100)
    private String bucketName;

    @Column(name = "object_name", nullable = false, length = 500)
    private String objectName;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FileVisibility visibility = FileVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 30)
    private FileScanStatus scanStatus = FileScanStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    // Constructor tiện lợi cho Service khi lưu file mới
    public FileEntity(UUID ownerId, String bucketName, String objectName,
                      String originalName, String contentType, Long fileSize,
                      String checksum, FileVisibility visibility) {
        this.ownerId = ownerId;
        this.bucketName = bucketName;
        this.objectName = objectName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.visibility = visibility;
    }

}
