package com.smartevent.ticketing.modules.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
}