package com.smartevent.modules.event.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest (

        @NotBlank(message = "Danh mục không được để trống")
        String name,

        String description
){
}

