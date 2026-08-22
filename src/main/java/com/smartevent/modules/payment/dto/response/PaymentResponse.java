package com.smartevent.modules.payment.dto.response;

import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID paymentId,
        UUID orderId,
        String orderCode,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String paymentUrl,
        Instant createdAt
) {
}
