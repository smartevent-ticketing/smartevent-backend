package com.smartevent.ticketing.modules.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EventSeatRequest(
        @NotBlank(message = "Tên hàng ghế không được để trống (ví dụ: 'A', 'VIP-1')")
        @Size(max = 50, message = "Tên hàng ghế tối đa 50 ký tự")
        String rowName,

        @NotBlank(message = "Số ghế không được để trống (ví dụ: '01', '12A')")
        @Size(max = 50, message = "Số ghế tối đa 50 ký tự")
        String seatNumber,

        @Size(max = 100, message = "Nhãn ghế tối đa 100 ký tự (ví dụ: 'A-01')")
        String label
) {}