package com.smartevent.modules.ticketing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketPhaseRuleRequest(
        @NotBlank(message = "Loại quy tắc không được để trống")
        @Size(max = 50, message = "Loại quy tắc tối đa 50 ký tự")
        String ruleType,

        String ruleValue
) {}