package com.smartevent.ticketing.modules.identity.dto.response;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenRefreshResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenRefreshResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}