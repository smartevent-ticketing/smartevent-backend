package com.smartevent.application.eventsetup;

import com.smartevent.common.enums.AreaType;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CreateEventSetupRequest(
        @NotNull @Valid CreateEventRequest event,
        @NotEmpty List<@NotNull @Valid Tier> tiers
) {
    public record Tier(
            @NotBlank @Size(max = 100) String name,
            @NotNull AreaType areaType,
            @NotNull @PositiveOrZero BigDecimal price,
            @Positive int capacity
    ) {}
}
