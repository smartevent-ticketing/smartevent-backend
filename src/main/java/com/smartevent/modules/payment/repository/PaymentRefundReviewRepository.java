package com.smartevent.modules.payment.repository;

import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRefundReviewRepository extends JpaRepository<PaymentRefundReview, UUID> {
    boolean existsByPaymentId(UUID paymentId);
    Page<PaymentRefundReview> findByStatusOrderByCreatedAtAsc(RefundReviewStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefundReview r where r.id = :id")
    java.util.Optional<PaymentRefundReview> findWithLockById(@Param("id") UUID id);
}
