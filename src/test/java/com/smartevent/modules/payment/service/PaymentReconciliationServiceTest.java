package com.smartevent.modules.payment.service;

import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.common.error.BusinessException;
import com.smartevent.modules.payment.dto.request.UpdateRefundReviewRequest;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.entity.PaymentRefundReviewAction;
import com.smartevent.modules.payment.repository.PaymentRefundReviewActionRepository;
import com.smartevent.modules.payment.repository.PaymentRefundReviewRepository;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentReconciliationServiceTest {
    private final PaymentRefundReviewRepository reviews = mock(PaymentRefundReviewRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentRefundReviewActionRepository actions = mock(PaymentRefundReviewActionRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final PaymentReconciliationService service = new PaymentReconciliationService(reviews, payments, actions, orders);

    @Test
    void buyerCreatesOneManualReviewForPaidOrder() {
        UUID orderId = UUID.randomUUID(), buyerId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(order.getUserId()).thenReturn(buyerId);
        when(order.getStatus()).thenReturn(OrderStatus.PAID);
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(reviews.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.empty());
        Payment payment = new Payment(orderId, PaymentMethod.VNPAY, "VNPAY", new BigDecimal("500000"));
        payment.setStatus(PaymentStatus.SUCCESS);
        when(payments.findByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(java.util.List.of(payment));
        when(reviews.save(any(PaymentRefundReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRefundReview review = service.requestReviewByBuyer(orderId, buyerId, "Sự kiện bị hủy, cần hỗ trợ");
        assertEquals(orderId, review.getOrderId());
        assertTrue(review.getReason().contains("Sự kiện bị hủy"));
        assertEquals(RefundReviewStatus.REQUIRED, review.getStatus());
    }

    @Test
    void buyerCannotRequestReviewForAnotherUsersOrder() {
        UUID orderId = UUID.randomUUID();
        Order order = mock(Order.class);
        when(order.getUserId()).thenReturn(UUID.randomUUID());
        when(orders.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        assertThrows(BusinessException.class, () -> service.requestReviewByBuyer(orderId,
                UUID.randomUUID(), "Cần hỗ trợ giao dịch"));
        verifyNoInteractions(payments, reviews);
    }

    @Test
    void adminRecordsManualRefundWithEvidenceAndAudit() {
        UUID reviewId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        PaymentRefundReview review = review();
        review.setId(reviewId);
        when(reviews.findWithLockById(reviewId)).thenReturn(Optional.of(review));
        when(reviews.save(review)).thenReturn(review);

        PaymentRefundReview saved = service.updateReview(reviewId, adminId,
                new UpdateRefundReviewRequest(RefundReviewStatus.REFUNDED_CONFIRMED,
                        "Đã đối chiếu giao dịch hoàn ngoài hệ thống", "VNPAY-REF-123"));

        assertEquals(RefundReviewStatus.REFUNDED_CONFIRMED, saved.getStatus());
        assertEquals("VNPAY-REF-123", saved.getEvidenceReference());
        assertEquals(adminId, saved.getUpdatedByUserId());
        assertNotNull(saved.getResolvedAt());
        verify(actions).save(argThat(action -> action.getReviewId().equals(reviewId)
                && action.getPreviousStatus() == RefundReviewStatus.REQUIRED
                && action.getNewStatus() == RefundReviewStatus.REFUNDED_CONFIRMED
                && action.getAdminUserId().equals(adminId)));
    }

    @Test
    void cannotClaimRefundWithoutEvidence() {
        UUID reviewId = UUID.randomUUID();
        PaymentRefundReview review = review();
        review.setId(reviewId);
        when(reviews.findWithLockById(reviewId)).thenReturn(Optional.of(review));

        assertThrows(BusinessException.class, () -> service.updateReview(reviewId, UUID.randomUUID(),
                new UpdateRefundReviewRequest(RefundReviewStatus.REFUNDED_CONFIRMED, "Đã hoàn", null)));
        verify(reviews, never()).save(any());
        verifyNoInteractions(actions);
    }

    private PaymentRefundReview review() {
        Payment payment = new Payment(UUID.randomUUID(), PaymentMethod.VNPAY, "VNPAY", BigDecimal.TEN);
        payment.setId(UUID.randomUUID());
        return new PaymentRefundReview(payment, "LATE_PAYMENT_EXPIRED");
    }
}
