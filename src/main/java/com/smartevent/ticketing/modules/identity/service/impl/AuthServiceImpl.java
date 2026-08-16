package com.smartevent.ticketing.modules.identity.service.impl;

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
import com.smartevent.ticketing.modules.identity.exception.AuthException;
import com.smartevent.ticketing.modules.identity.repository.RefreshTokenRepository;
import com.smartevent.ticketing.modules.identity.repository.RoleRepository;
import com.smartevent.ticketing.modules.identity.repository.UserRepository;
import com.smartevent.ticketing.modules.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_ROLE = "CUSTOMER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        /*Kiểm tra xem email đã tồn tại chưa*/
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(
                    ErrorCode.DUPLICATE_EMAIL,
                    "Email already exists"
            );
        }

        Role customerRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Customer role not found"
                ));

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.phone()
        );

        user.addRole(customerRole);
        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request){
        User user = userRepository.findByEmailWithRoles(request.email())
                .orElseThrow(() -> new AuthException(
                        ErrorCode.INVALID_CREDENTIALS,
                        "Email hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác");
        }
        if (!user.isActive()) {
            throw new AuthException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "Tài khoản của bạn đã bị vô hiệu hóa hoặc đã bị xóa");
        }

        UserPrincipal principal = UserPrincipal.create(user);

        String accessToken = jwtTokenProvider.generateAccessToken(principal);

        // Dòng 1: Sinh ra chìa khóa thật ngẫu nhiên
        String rawRefreshToken = jwtTokenProvider.generateSecureRandomToken();

        // Dòng 2: Lấy dấu vân tay (Băm SHA-256) của chiếc chìa khóa đó
        String tokenHash = jwtTokenProvider.hashToken(rawRefreshToken);

        // Dòng 3: Tính thời điểm hết hạn (Hiện tại + 7 ngày)
        Instant expiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs());

        // Dòng 4 & 5: Lưu dấu vân tay vào bảng refresh_tokens trong Database
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.of(
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationMs(),
                UserResponse.from(user));
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {

        String tokenHash = jwtTokenProvider.hashToken(request.refreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.INVALID_CREDENTIALS,
                        "Phiên đăng nhập không hợp"
                ));

        User user = refreshToken.getUser();

        // 1. Phát hiện Tấn Công Tái Sử Dụng Token (Reuse Detection)
        if (refreshToken.isRevoked()) {
            log.warn("Phát hiện Token Reuse Attack từ User ID: {}", user.getId());
            refreshTokenRepository.revokeAllUserTokens(user.getId(), Instant.now());
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Phiên đăng nhập đã bị thu hồi hoặc được sử dụng trước đó"
            );
        }

        // 2. Kiểm tra hết hạn
        if (refreshToken.isExpired()) {
            throw new BusinessException(
                    ErrorCode.TOKEN_EXPIRED,
                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"
            );
        }
        if (!user.isActive()) {
            throw new BusinessException(
                    ErrorCode.ACCOUNT_DISABLED,
                    "Tài khoản của bạn đã bị vô hiệu hóa"
            );
        }


        // 3. Xoay vòng Token (Rotation): Thu hồi token cũ
        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        // 4. Cấp phát Access Token Mới & Refresh Token Mới
        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = jwtTokenProvider.generateAccessToken(principal);
        String newRawRefreshToken = jwtTokenProvider.generateSecureRandomToken();
        String newTokenHash = jwtTokenProvider.hashToken(newRawRefreshToken);
        Instant newExpiresAt = Instant.now().plusMillis(jwtTokenProvider.getRefreshTokenExpirationMs());
        RefreshToken newRefreshToken = new RefreshToken(user, newTokenHash, newExpiresAt);
        refreshTokenRepository.save(newRefreshToken);
        return TokenRefreshResponse.of(
                newAccessToken,
                newRawRefreshToken,
                jwtTokenProvider.getAccessTokenExpirationMs()
        );
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {

        String tokenHash = jwtTokenProvider.hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                });

    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Không tìm thấy thông tin người dùng"
                ));
        return UserProfileResponse.from(user);
    }
}
