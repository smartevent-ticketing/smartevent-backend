package com.smartevent.ticketing.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long accessTokenExpirationMs = 60000; // 1 phút
    private final long refreshTokenExpirationMs = 604800000; // 7 ngày

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(secretKey, accessTokenExpirationMs, refreshTokenExpirationMs);
    }

    @Test
    @DisplayName("Nên sinh Access Token và parse đúng Claims")
    void shouldGenerateAccessTokenAndExtractClaims() {
        UUID userId = UUID.randomUUID();
        String email = "test@example.com";
        String fullName = "Nguyen Van A";
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        UserPrincipal principal = new UserPrincipal(userId, email, fullName, "password", authorities, true);

        String token = jwtTokenProvider.generateAccessToken(principal);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
        assertTrue(jwtTokenProvider.getRolesFromToken(token).contains("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("Nên phát hiện Token giả mạo hoặc sai chữ ký")
    void shouldFailValidationOnTamperedToken() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                userId, "user@test.com", "User", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), true
        );

        String token = jwtTokenProvider.generateAccessToken(principal);
        String tamperedToken = token + "corrupted";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }

    @Test
    @DisplayName("Nên sinh chuỗi Refresh Token ngẫu nhiên và băm SHA-256 chính xác")
    void shouldGenerateAndHashRefreshToken() {
        String rawToken = jwtTokenProvider.generateSecureRandomToken();
        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());

        String hash1 = jwtTokenProvider.hashToken(rawToken);
        String hash2 = jwtTokenProvider.hashToken(rawToken);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hash phải có độ dài 64 ký tự hex");
        assertEquals(hash1, hash2, "Cùng một raw token phải cho ra cùng một chuỗi hash");
    }
}
