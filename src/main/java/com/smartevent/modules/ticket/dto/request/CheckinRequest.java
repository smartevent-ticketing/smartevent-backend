package com.smartevent.modules.ticket.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckinRequest(

        @NotBlank(message = "Mã vé hoặc chuỗi token QR không được để trống")
        String ticketCodeOrToken, // Chuỗi quét được từ Camera / Scanner máy quét tại cổng

        @NotNull(message = "ID sự kiện không được để trống")
        UUID eventId,             // Kiểm tra đối soát xem vé có đúng của sự kiện này không

        String gateName           // Tên cổng soát vé (VD: "Cổng A1 - Tầng Trệt", "Cổng VIP")
) {}