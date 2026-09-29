package com.smartevent.modules.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuyerRefundReviewRequest(@NotBlank @Size(min = 10, max = 200) String reason) {}
