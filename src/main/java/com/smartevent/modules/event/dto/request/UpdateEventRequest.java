package com.smartevent.modules.event.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpdateEventRequest(
        @NotBlank(message = "Tên sự kiện không được để trống")
        @Size(max = 255, message = "Tên sự kiện tối đa 255 ký tự")
        String name,

        String description,

        UUID venueId,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        Instant startTime,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        Instant endTime,

        String city,

        List<UUID> categoryIds,

        UUID bannerFileId,

        List<UUID> galleryFileIds,

        Boolean resaleEnabled,

        BigDecimal maxResalePriceMultiplier,

        Integer resaleDeadlineHoursBefore,

        Boolean virtualQueueEnabled,

        Integer queueBatchSize
) {}
