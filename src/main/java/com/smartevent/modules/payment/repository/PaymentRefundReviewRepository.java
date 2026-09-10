package com.smartevent.modules.payment.repository;

import com.smartevent.modules.payment.entity.PaymentRefundReview;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundReviewRepository extends JpaRepository<PaymentRefundReview, UUID> {
    boolean existsByPaymentId(UUID paymentId);
    Page<PaymentRefundReview> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
