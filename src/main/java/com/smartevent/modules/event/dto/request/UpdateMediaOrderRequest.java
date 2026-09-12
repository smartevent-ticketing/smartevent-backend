package com.smartevent.modules.event.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateMediaOrderRequest(
        @NotEmpty(message = "Danh sách thứ tự ảnh không được để trống")
        List<MediaOrderItem> items
) {
    public record MediaOrderItem(
            @NotNull(message = "eventFileId không được để trống")
            UUID eventFileId,

            @NotNull(message = "sortOrder không được để trống")
            Integer sortOrder
    ) {}
}
