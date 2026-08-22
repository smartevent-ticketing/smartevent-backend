package com.smartevent.modules.ticket.dto.response;

import com.smartevent.common.enums.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID orderItemId,
        UUID currentOwnerUserId,
        UUID originalBuyerUserId,
        UUID eventId,
        String eventName,
        UUID eventAreaId,
        String areaName,
        UUID eventSeatId,
        String seatCode,          // VD: "Hàng A - Ghế 12" hoặc "Vé đứng tự do"
        UUID ticketTypeId,
        String ticketTypeName,    // VD: "VIP Diamond", "Standard"
        UUID salePhaseId,
        String salePhaseName,
        String ticketCode,        // Mã hiển thị cố định: "TCK-20260822-ABC12345"
        TicketStatus status,      // ISSUED, USED, TRANSFERRED, CANCELLED, REFUNDED
        String qrCodeBase64,      // Chuỗi "data:image/png;base64,..." để hiển thị ảnh QR tức thì
        Instant issuedAt,
        Instant usedAt
) {}