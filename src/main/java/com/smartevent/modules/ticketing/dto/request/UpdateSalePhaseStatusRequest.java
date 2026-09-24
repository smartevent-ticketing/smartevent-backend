package com.smartevent.modules.ticketing.dto.request;

import com.smartevent.common.enums.SalePhaseStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSalePhaseStatusRequest(
        @NotNull(message = "Trạng thái không được để trống")
        SalePhaseStatus status
) {}
