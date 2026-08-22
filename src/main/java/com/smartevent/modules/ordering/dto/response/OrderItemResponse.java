package com.smartevent.modules.ordering.dto.response;

import com.smartevent.modules.ordering.entity.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID ticketTypeId,
        String ticketTypeName,
        UUID salePhaseId,
        String salePhaseName,
        UUID eventSeatId,
        String seatCode,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
    public static OrderItemResponse of(OrderItem item, String ticketTypeName, String salePhaseName, String seatCode) {
        return new OrderItemResponse(
                item.getId(),
                item.getTicketTypeId(),
                ticketTypeName,
                item.getSalePhaseId(),
                salePhaseName,
                item.getEventSeatId(),
                seatCode,
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice()
        );
    }
}