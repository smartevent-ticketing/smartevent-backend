package com.smartevent.modules.ticketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TicketTypeRequest(
        @NotNull(message = "Khu vực vé (eventAreaId) không được để trống")
        UUID eventAreaId,

        @NotBlank(message = "Tên loại vé không được để trống")
        @Size(max = 100, message = "Tên loại vé không được vượt quá 100 ký tự")
        String name,

        String description,

        String status
) {
}

