package com.smartevent.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Slf4j
public final class TicketSecurityUtils {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final String DEFAULT_SECRET = "SmartEventSecretKeyForTicketQrVerification2026";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TicketSecurityUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // 1. Sinh Token Hash bảo mật cho mã QR của vé
    // Format: TCK-TOKEN.<ticketId>.<ownerId>.<salt>.<hmacSignature>
    public static String generateSecureQrToken(UUID ticketId, UUID ownerId) {
        String salt = generateRandomSalt(8);
        long timestamp = System.currentTimeMillis();
        String rawData = ticketId.toString() + ":" + ownerId.toString() + ":" + salt + ":" + timestamp;
        String signature = hmacSha256(DEFAULT_SECRET, rawData);

        // Tạo token nhỏ gọn, an toàn có thể nhúng trực tiếp vào mã QR
        return "TCK-QR." + ticketId + "." + salt + "." + signature;
    }

    // 2. Thuật toán băm HMAC-SHA256
    public static String hmacSha256(String key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception ex) {
            log.error("Lỗi khi băm HMAC-SHA256 cho vé: {}", ex.getMessage());
            throw new RuntimeException("Lỗi mã hóa token vé", ex);
        }
    }

    // 3. Sinh chuỗi muối ngẫu nhiên (Salt) bảo mật cao
    private static String generateRandomSalt(int length) {
        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}