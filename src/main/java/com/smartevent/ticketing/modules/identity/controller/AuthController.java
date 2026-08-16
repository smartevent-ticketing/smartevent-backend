package com.smartevent.ticketing.modules.identity.controller;

import com.smartevent.ticketing.common.api.ApiResponse;
import com.smartevent.ticketing.common.security.CurrentUser;
import com.smartevent.ticketing.infrastructure.security.UserPrincipal;
import com.smartevent.ticketing.modules.identity.dto.request.LoginRequest;
import com.smartevent.ticketing.modules.identity.dto.request.LogoutRequest;
import com.smartevent.ticketing.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.ticketing.modules.identity.dto.request.RegisterRequest;
import com.smartevent.ticketing.modules.identity.dto.response.LoginResponse;
import com.smartevent.ticketing.modules.identity.dto.response.TokenRefreshResponse;
import com.smartevent.ticketing.modules.identity.dto.response.UserProfileResponse;
import com.smartevent.ticketing.modules.identity.dto.response.UserResponse;
import com.smartevent.ticketing.modules.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh-token")
    public ApiResponse<TokenRefreshResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.ok("Đăng xuất thành công");
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getCurrentUser(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(authService.getProfile(currentUser.getId()));
    }
}