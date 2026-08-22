package com.smartevent.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Slf4j
public class MoMoUtils {

    // 1. Thuật toán băm HMAC-SHA256 theo chuẩn bảo mật MoMo
    public static String hmacSHA256(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key và Data không được để trống khi băm HMAC-SHA256");
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
            log.error("Lỗi khi băm MoMo HMAC-SHA256: {}", ex.getMessage(), ex);
            return "";
        }
    }

    // 2. Ghép chuỗi rawData tạo chữ ký gửi sang MoMo API
    public static String buildCreateOrderRawData(String accessKey, String amount, String extraData,
                                                 String ipnUrl, String orderId, String orderInfo,
                                                 String partnerCode, String redirectUrl,
                                                 String requestId, String requestType) {
        return "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&ipnUrl=" + ipnUrl +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&partnerCode=" + partnerCode +
                "&redirectUrl=" + redirectUrl +
                "&requestId=" + requestId +
                "&requestType=" + requestType;
    }

    // 3. Xác thực chữ ký IPN Webhook do MoMo gửi về
    public static boolean verifyIpnSignature(String accessKey, String amount, String extraData,
                                             String message, String orderId, String orderInfo,
                                             String orderType, String partnerCode, String payType,
                                             String requestId, String responseTime, String resultCode,
                                             String transId, String secretKey, String receivedSignature) {
        String rawData = "accessKey=" + accessKey +
                "&amount=" + amount +
                "&extraData=" + extraData +
                "&message=" + message +
                "&orderId=" + orderId +
                "&orderInfo=" + orderInfo +
                "&orderType=" + orderType +
                "&partnerCode=" + partnerCode +
                "&payType=" + payType +
                "&requestId=" + requestId +
                "&responseTime=" + responseTime +
                "&resultCode=" + resultCode +
                "&transId=" + transId;

        String calculatedSignature = hmacSHA256(secretKey, rawData);
        return calculatedSignature.equalsIgnoreCase(receivedSignature);
    }
}
