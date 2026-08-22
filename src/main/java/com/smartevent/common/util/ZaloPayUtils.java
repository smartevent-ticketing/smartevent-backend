package com.smartevent.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
public class ZaloPayUtils {

    // 1. Thuật toán băm HMAC-SHA256 chuẩn ZaloPay
    public static String hmacSHA256(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key và Data không được để trống khi băm ZaloPay HMAC");
            }
            Mac hmac256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac256.init(secretKeySpec);
            byte[] result = hmac256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Lỗi khi băm ZaloPay HMAC-SHA256: {}", ex.getMessage(), ex);
            return "";
        }
    }

    // 2. Tạo chữ ký MAC khi tạo đơn hàng (sử dụng Key1)
    // Format: app_id|app_trans_id|app_user|amount|app_time|embed_data|item
    public static String buildCreateOrderMac(String appId, String appTransId, String appUser,
                                             String amount, String appTime, String embedData,
                                             String item, String key1) {
        String data = appId + "|" + appTransId + "|" + appUser + "|" + amount + "|" + appTime + "|" + embedData + "|" + item;
        return hmacSHA256(key1, data);
    }

    // 3. Xác thực chữ ký MAC khi nhận Callback Webhook từ ZaloPay (sử dụng Key2)
    public static boolean verifyCallbackMac(String dataStr, String reqMac, String key2) {
        String calculatedMac = hmacSHA256(key2, dataStr);
        return calculatedMac.equalsIgnoreCase(reqMac);
    }
}
