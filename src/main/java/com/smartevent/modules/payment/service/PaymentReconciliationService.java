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
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.common.enums.OrderStatus;
import java.util.Optional;
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
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Optional<PaymentRefundReview> findBuyerReview(UUID orderId, UUID buyerId) {
        verifyBuyer(orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng")), buyerId);
        return reviewRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional
    public PaymentRefundReview requestReviewByBuyer(UUID orderId, UUID buyerId, String reason) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));
        verifyBuyer(order, buyerId);
        Optional<PaymentRefundReview> existing = reviewRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
        if (existing.isPresent()) return existing.get();
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "Chỉ có thể yêu cầu hỗ trợ cho đơn đã thanh toán");
        }
        String detail = reason == null ? "" : reason.trim();
        if (detail.length() < 10 || detail.length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lý do hỗ trợ cần từ 10 đến 200 ký tự");
        }
        Payment payment = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(Payment::isSuccess).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "Không tìm thấy giao dịch đã thanh toán"));
        return reviewRepository.save(new PaymentRefundReview(payment, "CUSTOMER_REQUEST: " + detail));
    }

    private void verifyBuyer(Order order, UUID buyerId) {
        if (!order.getUserId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đơn hàng này");
        }
    }

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
