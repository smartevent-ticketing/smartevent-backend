package com.smartevent.modules.payment.dto.request;

import com.smartevent.common.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull(message = "Mã đơn hàng (orderId) không được để trống")
        UUID orderId,

        @NotNull(message = "Phương thức thanh toán (paymentMethod) không được để trống")
        @Schema(description = "Hiện chỉ hỗ trợ VNPay", allowableValues = {"VNPAY"})
        PaymentMethod paymentMethod,

        String bankCode
) {
}
