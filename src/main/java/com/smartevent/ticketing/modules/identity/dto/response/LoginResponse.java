package com.smartevent.ticketing.modules.identity.dto.response;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}