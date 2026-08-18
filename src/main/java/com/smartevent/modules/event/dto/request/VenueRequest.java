package com.smartevent.modules.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record VenueRequest (

        @NotBlank(message = "Tên địa điểm không được phép để trống!")
        String name,

        @NotBlank(message = "Địa chỉ không được phép để trống!")
        String address,

        @NotBlank(message = "Vui lòng điền thông tin thành phố")
        String city,

        BigDecimal latitude,

        BigDecimal longitude,

        @Positive
        Integer capacity
){
}

