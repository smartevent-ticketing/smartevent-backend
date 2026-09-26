package com.smartevent.modules.payment.entity;

import com.smartevent.common.enums.RefundReviewStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_refund_review_actions")
@Getter
@NoArgsConstructor
public class PaymentRefundReviewAction {
    @Id private UUID id;
    @Column(name = "review_id", nullable = false) private UUID reviewId;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false) private RefundReviewStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false) private RefundReviewStatus newStatus;
    @Column(nullable = false, columnDefinition = "TEXT") private String note;
    @Column(name = "evidence_reference") private String evidenceReference;
    @Column(name = "admin_user_id", nullable = false) private UUID adminUserId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public PaymentRefundReviewAction(UUID reviewId, RefundReviewStatus previousStatus,
                                     RefundReviewStatus newStatus, String note,
                                     String evidenceReference, UUID adminUserId) {
        this.id = UUID.randomUUID();
        this.reviewId = reviewId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.note = note;
        this.evidenceReference = evidenceReference;
        this.adminUserId = adminUserId;
        this.createdAt = Instant.now();
    }
}
