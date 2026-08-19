package com.smartevent.modules.ticketing.dto.request;

import com.smartevent.common.enums.SalePhaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record TicketSalePhaseRequest(
        @NotBlank(message = "Tên đợt mở bán không được để trống")
        @Size(max = 100, message = "Tên đợt mở bán tối đa 100 ký tự")
        String name,

        @NotNull(message = "Giá vé không được để trống")
        @PositiveOrZero(message = "Giá vé phải lớn hơn hoặc bằng 0")
        BigDecimal price,

        @NotNull(message = "Số lượng vé không được để trống")
        @Positive(message = "Số lượng vé phải lớn hơn 0")
        Integer quantity,

        @NotNull(message = "Thời gian bắt đầu mở bán không được để trống")
        Instant saleStartAt,

        @NotNull(message = "Thời gian kết thúc mở bán không được để trống")
        Instant saleEndAt,

        @Positive(message = "Giới hạn mua trên 1 đơn hàng phải lớn hơn 0")
        Integer maxPerOrder,

        @Positive(message = "Giới hạn mua trên 1 người dùng phải lớn hơn 0")
        Integer maxPerUser,

        SalePhaseStatus status
) {}