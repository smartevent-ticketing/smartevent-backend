package com.smartevent.modules.payment.dto.response;

import java.math.BigDecimal;

public record VNPayReturnResponse(
        String orderCode,
        String transactionNo,
        String bankCode,
        BigDecimal amount,
        String payDate,
        String status,
        String message
) {
}