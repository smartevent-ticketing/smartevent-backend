package com.smartevent.modules.ordering.dto.request;

import com.smartevent.common.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Mã phiên giữ chỗ (reservationId) không được để trống")
        UUID reservationId,

        String customerNote,

        PaymentMethod paymentMethod
) {
}