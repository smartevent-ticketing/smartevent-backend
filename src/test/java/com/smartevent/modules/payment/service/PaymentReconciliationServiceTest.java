package com.smartevent.modules.payment.service;

import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.common.error.BusinessException;
import com.smartevent.modules.payment.dto.request.UpdateRefundReviewRequest;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.entity.PaymentRefundReviewAction;
import com.smartevent.modules.payment.repository.PaymentRefundReviewActionRepository;
import com.smartevent.modules.payment.repository.PaymentRefundReviewRepository;
import com.smartevent.modules.payment.repository.PaymentRepository;
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
    private final PaymentReconciliationService service = new PaymentReconciliationService(reviews, payments, actions);

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
