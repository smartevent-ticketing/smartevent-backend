package com.smartevent.modules.payment.service;

import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.payment.dto.request.UpdateRefundReviewRequest;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.entity.PaymentRefundReviewAction;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.payment.repository.PaymentRefundReviewActionRepository;
import com.smartevent.modules.payment.repository.PaymentRefundReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {
    private final PaymentRefundReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundReviewActionRepository actionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReview(Payment payment, String reason) {
        if (!reviewRepository.existsByPaymentId(payment.getId())) {
            reviewRepository.save(new PaymentRefundReview(payment, reason));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReviewForOrder(UUID orderId, String reason) {
        paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(Payment::isSuccess).forEach(payment -> requireReview(payment, reason));
    }

    @Transactional
    public PaymentRefundReview updateReview(UUID reviewId, UUID adminUserId, UpdateRefundReviewRequest request) {
        PaymentRefundReview review = reviewRepository.findWithLockById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ đối soát"));
        RefundReviewStatus previous = review.getStatus();
        RefundReviewStatus next = request.status();
        if (previous == next) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Trạng thái đối soát không thay đổi");
        }
        if ((previous == RefundReviewStatus.REFUNDED_CONFIRMED || previous == RefundReviewStatus.CLOSED_NO_REFUND)
                && next != RefundReviewStatus.REQUIRED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Hồ sơ đã đóng; cần mở lại trước khi cập nhật");
        }
        String note = request.note().trim();
        String reference = request.evidenceReference() == null ? null : request.evidenceReference().trim();
        if (next == RefundReviewStatus.REFUNDED_CONFIRMED && (reference == null || reference.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cần mã giao dịch hoặc bằng chứng hoàn tiền đã xác minh");
        }
        review.recordManualDecision(next, note, reference, adminUserId);
        PaymentRefundReview saved = reviewRepository.save(review);
        actionRepository.save(new PaymentRefundReviewAction(reviewId, previous, next, note, reference, adminUserId));
        return saved;
    }
}
