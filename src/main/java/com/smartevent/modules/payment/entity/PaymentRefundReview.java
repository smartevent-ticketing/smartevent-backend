package com.smartevent.modules.payment.entity;

import com.smartevent.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
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
    @Column(nullable = false) private String status = "REQUIRED";

    public PaymentRefundReview(Payment payment, String reason) {
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.amount = payment.getAmount();
        this.reason = reason;
    }
}
