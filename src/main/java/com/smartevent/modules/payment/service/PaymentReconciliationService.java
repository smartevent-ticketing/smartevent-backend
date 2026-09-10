package com.smartevent.modules.payment.service;

import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.repository.PaymentRepository;
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
}
