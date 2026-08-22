package com.smartevent.modules.ticket.dto.response;

import com.smartevent.common.enums.CheckinResult;

import java.time.Instant;
import java.util.UUID;

public record CheckinResponse(
        UUID checkinId,
        UUID ticketId,
        String ticketCode,
        CheckinResult result,     // SUCCESS, INVALID, DUPLICATE
        String message,           // VD: "HỢP LỆ! Mời quý khách vào cửa" hoặc "CẢNH BÁO: Vé đã quét lúc 18:30!"
        String gateName,
        String attendeeName,      // Tên khán giả chủ sở hữu vé
        String seatCode,          // Vị trí ghế để nhân viên hướng dẫn chỗ ngồi
        String ticketTypeName,    // Hạng vé
        Instant checkedAt
) {
    public static CheckinResponse success(UUID checkinId, UUID ticketId, String ticketCode, String gateName,
                                          String attendeeName, String seatCode, String ticketTypeName) {
        return new CheckinResponse(
                checkinId, ticketId, ticketCode, CheckinResult.SUCCESS,
                "HỢP LỆ! Mời quý khách qua cổng.", gateName, attendeeName, seatCode, ticketTypeName, Instant.now()
        );
    }

    public static CheckinResponse duplicate(UUID checkinId, UUID ticketId, String ticketCode, String gateName,
                                            String attendeeName, String previousUsedTime) {
        return new CheckinResponse(
                checkinId, ticketId, ticketCode, CheckinResult.DUPLICATE,
                "CẢNH BÁO: Vé này đã được quét sử dụng trước đó (Lúc " + previousUsedTime + ")!",
                gateName, attendeeName, null, null, Instant.now()
        );
    }

    public static CheckinResponse invalid(String message, String gateName) {
        return new CheckinResponse(
                null, null, null, CheckinResult.INVALID,
                message, gateName, null, null, null, Instant.now()
        );
    }
}