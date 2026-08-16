package com.smartevent.ticketing.modules.identity.service;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.infrastructure.security.JwtTokenProvider;
import com.smartevent.ticketing.infrastructure.security.UserPrincipal;
import com.smartevent.ticketing.modules.identity.dto.request.LoginRequest;
import com.smartevent.ticketing.modules.identity.dto.request.LogoutRequest;
import com.smartevent.ticketing.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.ticketing.modules.identity.dto.request.RegisterRequest;
import com.smartevent.ticketing.modules.identity.dto.response.LoginResponse;
import com.smartevent.ticketing.modules.identity.dto.response.TokenRefreshResponse;
import com.smartevent.ticketing.modules.identity.dto.response.UserProfileResponse;
import com.smartevent.ticketing.modules.identity.dto.response.UserResponse;
import com.smartevent.ticketing.modules.identity.entity.RefreshToken;
import com.smartevent.ticketing.modules.identity.entity.Role;
import com.smartevent.ticketing.modules.identity.entity.User;
import com.smartevent.ticketing.modules.identity.repository.RefreshTokenRepository;
import com.smartevent.ticketing.modules.identity.repository.RoleRepository;
import com.smartevent.ticketing.modules.identity.repository.UserRepository;
import com.smartevent.ticketing.modules.identity.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private User mockUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new Role("CUSTOMER");
        mockUser = new User("user@test.com", "encodedPassword", "Nguyen Van A", "0901234567");
        mockUser.addRole(customerRole);
    }

    @Test
    @DisplayName("Register: Đăng ký tài khoản thành công")
    void register_Success() {
        RegisterRequest request = new RegisterRequest("user@test.com", "Password@123", "Nguyen Van A", "0901234567");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);

        UserResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("user@test.com", response.email());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Register: Ném lỗi DUPLICATE_EMAIL khi email đã tồn tại")
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest("user@test.com", "Password@123", "Nguyen Van A", "0901234567");

        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(ErrorCode.DUPLICATE_EMAIL, ex.getErrorCode());
    }

    @Test
    @DisplayName("Login: Đăng nhập thành công trả về Access Token và Refresh Token")
    void login_Success() {
        LoginRequest request = new LoginRequest("user@test.com", "Password@123");

        when(userRepository.findByEmailWithRoles("user@test.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("Password@123", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("mock.jwt.token");
        when(jwtTokenProvider.generateSecureRandomToken()).thenReturn("mockRawRefreshToken");
        when(jwtTokenProvider.hashToken(anyString())).thenReturn("mockHashedRefreshToken");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.accessToken());
        assertEquals("mockRawRefreshToken", response.refreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Login: Ném lỗi INVALID_CREDENTIALS khi sai mật khẩu")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest("user@test.com", "WrongPassword");

        when(userRepository.findByEmailWithRoles("user@test.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("WrongPassword", "encodedPassword")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    @DisplayName("RefreshToken: Xoay vòng token thành công")
    void refreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("validRawRefreshToken");
        Instant futureExpiry = Instant.now().plusSeconds(3600);
        RefreshToken validToken = new RefreshToken(mockUser, "tokenHash", futureExpiry);

        when(jwtTokenProvider.hashToken("validRawRefreshToken")).thenReturn("tokenHash");
        when(refreshTokenRepository.findByTokenHashWithUser("tokenHash")).thenReturn(Optional.of(validToken));
        when(jwtTokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new.access.token");
        when(jwtTokenProvider.generateSecureRandomToken()).thenReturn("newRawRefreshToken");
        when(jwtTokenProvider.hashToken("newRawRefreshToken")).thenReturn("newTokenHash");
        when(jwtTokenProvider.getRefreshTokenExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessTokenExpirationMs()).thenReturn(900000L);

        TokenRefreshResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new.access.token", response.accessToken());
        assertEquals("newRawRefreshToken", response.refreshToken());
        assertTrue(validToken.isRevoked(), "Token cũ phải bị thu hồi (revoked)");
    }

    @Test
    @DisplayName("RefreshToken: Phát hiện Token Reuse Attack và hủy toàn bộ phiên của User")
    void refreshToken_TokenReuseAttack_RevokesAllTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest("revokedRawToken");
        RefreshToken revokedToken = new RefreshToken(mockUser, "tokenHash", Instant.now().plusSeconds(3600));
        revokedToken.revoke(); // Đã bị thu hồi trước đó

        when(jwtTokenProvider.hashToken("revokedRawToken")).thenReturn("tokenHash");
        when(refreshTokenRepository.findByTokenHashWithUser("tokenHash")).thenReturn(Optional.of(revokedToken));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.refreshToken(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        verify(refreshTokenRepository).revokeAllUserTokens(eq(mockUser.getId()), any(Instant.class));
    }

    @Test
    @DisplayName("Logout: Thu hồi token trong database")
    void logout_Success() {
        LogoutRequest request = new LogoutRequest("rawTokenToRevoke");
        RefreshToken token = new RefreshToken(mockUser, "tokenHash", Instant.now().plusSeconds(3600));

        when(jwtTokenProvider.hashToken("rawTokenToRevoke")).thenReturn("tokenHash");
        when(refreshTokenRepository.findByTokenHash("tokenHash")).thenReturn(Optional.of(token));

        authService.logout(request);

        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("GetProfile: Lấy thông tin người dùng thành công")
    void getProfile_Success() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdWithRoles(userId)).thenReturn(Optional.of(mockUser));

        UserProfileResponse response = authService.getProfile(userId);

        assertNotNull(response);
        assertEquals("user@test.com", response.email());
    }
}
