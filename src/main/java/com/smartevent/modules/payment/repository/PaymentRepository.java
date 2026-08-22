package com.smartevent.modules.payment.repository;

import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // 1. Lấy toàn bộ lịch sử các lần bấm thanh toán của một đơn hàng (mới nhất lên đầu)
    List<Payment> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    // 2. Tìm bản ghi thanh toán theo mã giao dịch của cổng thanh toán (VD: vnp_TransactionNo)
    Optional<Payment> findByProviderAndTransactionId(String provider, String transactionId);

    // 3. Lấy giao dịch gần nhất của đơn hàng theo trạng thái (VD: INITIATED)
    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);
}
