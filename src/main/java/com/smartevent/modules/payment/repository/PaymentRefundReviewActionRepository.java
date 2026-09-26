package com.smartevent.modules.payment.repository;

import com.smartevent.modules.payment.entity.PaymentRefundReviewAction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRefundReviewActionRepository extends JpaRepository<PaymentRefundReviewAction, UUID> {
    List<PaymentRefundReviewAction> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);
}
