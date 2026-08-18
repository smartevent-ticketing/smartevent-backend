package com.smartevent.modules.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record GenerateSeatsRequest(
        @NotBlank(message = "Hàng bắt đầu không được để trống (ví dụ: 'A')")
        String fromRow,

        @NotBlank(message = "Hàng kết thúc không được để trống (ví dụ: 'F')")
        String toRow,

        @NotNull(message = "Số ghế trên mỗi hàng không được để trống")
        @Positive(message = "Số ghế trên mỗi hàng phải lớn hơn 0")
        Integer seatsPerRow
) {}
