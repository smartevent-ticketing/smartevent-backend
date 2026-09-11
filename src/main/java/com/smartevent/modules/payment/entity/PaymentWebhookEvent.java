package com.smartevent.modules.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_webhook_provider_event", columnNames = {"provider", "provider_event_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 100)
    private String providerEventId;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "payload_hash", length = 128)
    private String payloadHash;

    // SỬA TẠI ĐÂY: Khai báo ánh xạ chuẩn sang cột kiểu JSONB của PostgreSQL
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "RECEIVED";

    public PaymentWebhookEvent(String provider, String providerEventId, String transactionId,
                               String payloadHash, String payloadJson) {
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.transactionId = transactionId;
        this.payloadHash = payloadHash;
        this.payloadJson = payloadJson;
        this.status = "RECEIVED";
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }
}
