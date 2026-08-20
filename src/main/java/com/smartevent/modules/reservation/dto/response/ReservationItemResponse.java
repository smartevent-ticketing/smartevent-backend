package com.smartevent.modules.reservation.dto.response;

import com.smartevent.modules.reservation.entity.ReservationItem;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemResponse(
        UUID id,
        UUID ticketTypeId,
        String ticketTypeName,
        UUID salePhaseId,
        String salePhaseName,
        UUID eventSeatId,
        String seatCode,          // Mã số ghế dạng String (VD: "A-12")
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {
    public static ReservationItemResponse of(
            ReservationItem item,
            String ticketTypeName,
            String salePhaseName,
            String seatCode
    ) {
        return new ReservationItemResponse(
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