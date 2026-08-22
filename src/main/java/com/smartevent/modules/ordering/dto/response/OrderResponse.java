package com.smartevent.modules.ordering.dto.response;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.modules.ordering.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID userId,
        UUID reservationId,
        String orderCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal feeAmount,
        BigDecimal totalAmount,
        String currency,
        Instant paymentDeadline,
        String customerNote,
        PaymentMethod selectedPaymentMethod,
        OrderStatus status,
        List<OrderItemResponse> items,
        Instant createdAt
) {
    public static OrderResponse of(Order order, List<OrderItemResponse> items) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getReservationId(),
                order.getOrderCode(),
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getFeeAmount(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPaymentDeadline(),
                order.getCustomerNote(),
                order.getSelectedPaymentMethod(),
                order.getStatus(),
                items,
                order.getCreatedAt()
        );
    }
}