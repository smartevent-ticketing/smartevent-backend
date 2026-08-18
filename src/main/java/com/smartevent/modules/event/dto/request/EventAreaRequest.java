package com.smartevent.modules.event.dto.request;

import com.smartevent.common.enums.AreaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EventAreaRequest(
        @NotBlank(message = "Tên khu vực không được để trống")
        @Size(max = 100, message = "Tên khu vực tối đa 100 ký tự")
        String name,

        @NotNull(message = "Loại khu vực không được để trống (STANDING hoặc SEATED)")
        AreaType areaType,

        @NotNull(message = "Sức chứa không được để trống")
        @Positive(message = "Sức chứa phải lớn hơn 0")
        Integer capacity,

        Integer sortOrder,

        String description
) {}
