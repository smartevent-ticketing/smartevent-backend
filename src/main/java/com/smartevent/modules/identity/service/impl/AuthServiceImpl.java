package com.smartevent.modules.identity.service.impl;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.enums.FileVisibility;
import com.smartevent.infrastructure.security.JwtTokenProvider;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.identity.dto.request.LoginRequest;
import com.smartevent.modules.identity.dto.request.LogoutRequest;
import com.smartevent.modules.identity.dto.request.RefreshTokenRequest;
import com.smartevent.modules.identity.dto.request.RegisterRequest;
import com.smartevent.modules.identity.dto.response.LoginResponse;
import com.smartevent.modules.identity.dto.response.TokenRefreshResponse;
import com.smartevent.modules.identity.dto.response.UserProfileResponse;
import com.smartevent.modules.identity.dto.response.UserResponse;
import com.smartevent.modules.identity.entity.RefreshToken;
import com.smartevent.modules.identity.entity.Role;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.exception.AuthException;
import com.smartevent.modules.identity.exception.TokenReuseException;
import com.smartevent.modules.identity.repository.RefreshTokenRepository;
import com.smartevent.modules.identity.repository.RoleRepository;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.identity.service.AuthService;
import com.smartevent.modules.storage.dto.response.FileUploadResponse;
import com.smartevent.modules.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
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
    private final StorageService storageService;
    private final PlatformTransactionManager transactionManager;

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
    @Transactional(noRollbackFor = TokenReuseException.class)
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {

        String tokenHash = jwtTokenProvider.hashToken(request.refreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.INVALID_CREDENTIALS,
                        "Phiên đăng nhập không hợp"
                ));

        User user = refreshToken.getUser();

        // 1. Phát hiện Tấn Công Tái Sử Dụng Token (Reuse Detection)
        if (refreshToken.isRevoked()) {
            log.warn("Phát hiện Token Reuse Attack từ User ID: {}", user.getId());
            refreshTokenRepository.revokeAllUserTokens(user.getId(), Instant.now());
            throw new TokenReuseException();
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

    @Override
    @Transactional
    public UserProfileResponse updateAvatar(UUID userId, MultipartFile file) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản"));
        validateAvatar(file);

        FileUploadResponse uploaded = storageService.uploadFile(file, userId, "avatars", FileVisibility.PRIVATE);
        UUID previousAvatarFileId = user.getAvatarFileId();
        user.setAvatarFileId(uploaded.id());
        userRepository.save(user);
        if (previousAvatarFileId != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        TransactionTemplate cleanupTransaction = new TransactionTemplate(transactionManager);
                        cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        cleanupTransaction.executeWithoutResult(status -> storageService.deleteFile(previousAvatarFileId, userId));
                    } catch (RuntimeException e) {
                        log.warn("Không thể dọn ảnh đại diện cũ {} của người dùng {}", previousAvatarFileId, userId, e);
                    }
                }
            });
        }
        return UserProfileResponse.from(user);
    }

    private void validateAvatar(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh đại diện phải có dung lượng tối đa 2 MB");
        }

        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        String extension = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean jpeg = "image/jpeg".equals(contentType) && ("jpg".equals(extension) || "jpeg".equals(extension));
        boolean png = "image/png".equals(contentType) && "png".equals(extension);
        boolean webp = "image/webp".equals(contentType) && "webp".equals(extension);
        if (!jpeg && !png && !webp) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ hỗ trợ ảnh JPG, PNG hoặc WEBP");
        }

        try {
            byte[] signature = file.getInputStream().readNBytes(12);
            boolean validJpeg = jpeg && signature.length >= 3
                    && (signature[0] & 0xff) == 0xff && (signature[1] & 0xff) == 0xd8 && (signature[2] & 0xff) == 0xff;
            boolean validPng = png && signature.length >= 8 && Arrays.equals(
                    Arrays.copyOf(signature, 8), new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            boolean validWebp = webp && signature.length >= 12
                    && signature[0] == 'R' && signature[1] == 'I' && signature[2] == 'F' && signature[3] == 'F'
                    && signature[8] == 'W' && signature[9] == 'E' && signature[10] == 'B' && signature[11] == 'P';
            if (!validJpeg && !validPng && !validWebp) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nội dung ảnh đại diện không hợp lệ");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không thể đọc ảnh đại diện");
        }
    }
}

