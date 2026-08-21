package com.smartevent.modules.ordering.entity;

import com.smartevent.common.entity.BaseEntity;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "order_code", nullable = false, unique = true, length = 50)
    private String orderCode;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "VND";

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_payment_method", length = 30)
    private PaymentMethod selectedPaymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    public Order(UUID userId, UUID reservationId, String orderCode, BigDecimal subtotal,
                 BigDecimal discountAmount, BigDecimal feeAmount, BigDecimal totalAmount,
                 Instant paymentDeadline, String customerNote, PaymentMethod selectedPaymentMethod) {
        this.userId = userId;
        this.reservationId = reservationId;
        this.orderCode = orderCode;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.feeAmount = feeAmount != null ? feeAmount : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.currency = "VND";
        this.paymentDeadline = paymentDeadline;
        this.customerNote = customerNote;
        this.selectedPaymentMethod = selectedPaymentMethod;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public boolean isPendingPayment() {
        return this.status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isPaid() {
        return this.status == OrderStatus.PAID;
    }

    public boolean isExpired() {
        return this.paymentDeadline != null && Instant.now().isAfter(this.paymentDeadline);
    }
}
