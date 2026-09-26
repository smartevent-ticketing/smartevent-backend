package com.smartevent.modules.payment.dto.request;

import com.smartevent.common.enums.RefundReviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRefundReviewRequest(
        @NotNull RefundReviewStatus status,
        @NotBlank @Size(max = 2000) String note,
        @Size(max = 200) String evidenceReference
) {}
