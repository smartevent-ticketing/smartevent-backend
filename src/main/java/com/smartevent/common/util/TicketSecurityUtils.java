package com.smartevent.common.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class TicketSecurityUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TicketSecurityUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // Token chỉ được xác thực bằng bản ghi ACTIVE trong database, nên dùng giá trị ngẫu nhiên
    // thay vì một khóa HMAC cố định trong mã nguồn. Token cũ vẫn tra cứu được sau nâng cấp.
    public static String generateSecureQrToken(UUID ticketId) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return "TCK-QR." + ticketId + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
