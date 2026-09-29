package com.smartevent.modules.ordering.dto.request;

import com.smartevent.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderRequest(
        @NotNull(message = "Mã phiên giữ chỗ (reservationId) không được để trống")
        UUID reservationId,

        String customerNote,

        @Schema(description = "Hiện chỉ hỗ trợ VNPay", allowableValues = {"VNPAY"})
        PaymentMethod paymentMethod
) {
}
