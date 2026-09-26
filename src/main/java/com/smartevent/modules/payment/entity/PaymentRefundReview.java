package com.smartevent.modules.payment.entity;

import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An obligation to reconcile money, not a claim that a refund has been executed. */
@Entity
@Table(name = "payment_refund_reviews")
@Getter
@NoArgsConstructor
public class PaymentRefundReview extends BaseEntity {
    @Column(name = "payment_id", nullable = false, unique = true) private UUID paymentId;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private RefundReviewStatus status = RefundReviewStatus.REQUIRED;
    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "evidence_reference", length = 200) private String evidenceReference;
    @Column(name = "updated_by_user_id") private UUID updatedByUserId;
    @Column(name = "resolved_at") private Instant resolvedAt;

    public PaymentRefundReview(Payment payment, String reason) {
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.amount = payment.getAmount();
        this.reason = reason;
    }

    public void recordManualDecision(RefundReviewStatus nextStatus, String note,
                                     String reference, UUID adminUserId) {
        this.status = nextStatus;
        this.resolutionNote = note;
        this.evidenceReference = reference;
        this.updatedByUserId = adminUserId;
        this.resolvedAt = nextStatus == RefundReviewStatus.REFUNDED_CONFIRMED
                || nextStatus == RefundReviewStatus.CLOSED_NO_REFUND ? Instant.now() : null;
    }
}
