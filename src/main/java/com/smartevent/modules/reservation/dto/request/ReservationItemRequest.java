package com.smartevent.modules.reservation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReservationItemRequest(
        @NotNull(message = "Loại vé không được để trống")
        UUID ticketTypeId,
        @NotNull(message = "Đợt mở bán không được để trống")
        UUID salePhaseId,
        UUID eventSeatId, // null nếu là vé STANDING, có ID nếu là SEATED
        @NotNull(message = "Số lượng vé không được để trống")
        @Positive(message = "Số lượng vé phải lớn hơn 0")
        Integer quantity
) {}
