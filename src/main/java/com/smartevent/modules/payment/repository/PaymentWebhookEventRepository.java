package com.smartevent.modules.payment.repository;

import com.smartevent.modules.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    // 1. Tìm bản ghi Webhook theo nhà cung cấp và mã sự kiện
    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

    // 2. Kiểm tra nhanh sự kiện Webhook đã tồn tại trong DB chưa (trả về true/false)
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
}