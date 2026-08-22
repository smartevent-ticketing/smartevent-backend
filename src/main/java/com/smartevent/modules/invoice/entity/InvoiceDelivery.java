package com.smartevent.modules.invoice.entity;

import com.smartevent.common.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_deliveries")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceDelivery {

    @Id
    @UuidGenerator // 👈 Tự động sinh UUIDv4 bằng Hibernate Generator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel = "EMAIL";

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "provider_message_id")
    private String providerMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InvoiceDelivery(UUID invoiceId, String recipientEmail) {
        this.invoiceId = invoiceId;
        this.recipientEmail = recipientEmail;
        this.channel = "EMAIL";
        this.status = DeliveryStatus.PENDING;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}