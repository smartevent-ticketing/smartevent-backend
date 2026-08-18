package com.smartevent.modules.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartevent.modules.identity.dto.request.LoginRequest;
import com.smartevent.modules.identity.dto.request.LogoutRequest;
import com.smartevent.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.modules.identity.dto.request.RegisterRequest;
import com.smartevent.modules.identity.dto.response.LoginResponse;
import com.smartevent.modules.identity.dto.response.TokenRefreshResponse;
import com.smartevent.modules.identity.dto.response.UserResponse;
import com.smartevent.modules.identity.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Đăng ký trả về 200 OK và ApiResponse chuẩn")
    void register_ReturnsSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest("user@test.com", "Password@123", "Nguyen Van A", "0901234567");
        UserResponse response = new UserResponse(UUID.randomUUID(), "user@test.com", "Nguyen Van A", "0901234567", "ACTIVE", Set.of("CUSTOMER"));

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@test.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Đăng nhập trả về 200 OK và LoginResponse")
    void login_ReturnsSuccess() throws Exception {
        LoginRequest request = new LoginRequest("user@test.com", "Password@123");
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "user@test.com", "Nguyen Van A", "0901234567", "ACTIVE", Set.of("CUSTOMER"));
        LoginResponse response = LoginResponse.of("mock.jwt.token", "mockRawRefreshToken", 900000L, userResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("mockRawRefreshToken"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh-token - Cấp mới token trả về 200 OK")
    void refreshToken_ReturnsSuccess() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("rawRefreshToken");
        TokenRefreshResponse response = TokenRefreshResponse.of("new.jwt.token", "newRawRefreshToken", 900000L);

        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new.jwt.token"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - Đăng xuất thành công trả về 200 OK")
    void logout_ReturnsSuccess() throws Exception {
        LogoutRequest request = new LogoutRequest("rawRefreshToken");
        doNothing().when(authService).logout(any(LogoutRequest.class));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

