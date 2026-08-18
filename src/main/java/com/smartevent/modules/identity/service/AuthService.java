package com.smartevent.modules.identity.service;

import com.smartevent.modules.identity.dto.request.LoginRequest;
import com.smartevent.modules.identity.dto.request.LogoutRequest;
import com.smartevent.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.modules.identity.dto.request.RegisterRequest;
import com.smartevent.modules.identity.dto.response.LoginResponse;
import com.smartevent.modules.identity.dto.response.TokenRefreshResponse;
import com.smartevent.modules.identity.dto.response.UserProfileResponse;
import com.smartevent.modules.identity.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    TokenRefreshResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    UserProfileResponse getProfile(UUID userId);
}
