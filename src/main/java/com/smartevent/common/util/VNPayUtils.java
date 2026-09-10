package com.smartevent.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class VNPayUtils {

    // 1. Thuật toán băm HMAC-SHA512
    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key và Data không được để trống khi băm HMAC");
            }
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Lỗi khi băm HMAC-SHA512: {}", ex.getMessage(), ex);
            return "";
        }
    }

    // 2. Sắp xếp Alphabet toàn bộ tham số và tạo chữ ký băm
    public static String hashAllFields(Map<String, String> fields, String secretKey) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                try {
                    sb.append(fieldName);
                    sb.append('=');
                    sb.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (UnsupportedEncodingException e) {
                    log.error("Lỗi encode US_ASCII: {}", e.getMessage());
                }
                if (itr.hasNext()) {
                    sb.append('&');
                }
            }
        }
        return hmacSHA512(secretKey, sb.toString());
    }

    // 3. Xây dựng chuỗi Query URL hoàn chỉnh kèm chữ ký vnp_SecureHash
    public static String buildQueryUrl(Map<String, String> fields, String hashSecret) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        StringBuilder query = new StringBuilder();
        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                try {
                    hashData.append(fieldName);
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    if (itr.hasNext()) {
                        query.append('&');
                        hashData.append('&');
                    }
                } catch (UnsupportedEncodingException e) {
                    log.error("Lỗi encode URL: {}", e.getMessage());
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = hmacSHA512(hashSecret, hashData.toString());
        return queryUrl + "&vnp_SecureHash=" + vnpSecureHash;
    }

    // 4. Kiểm tra tính hợp lệ của chữ ký phản hồi
    public static boolean verifySignature(Map<String, String> fields, String secureHash, String secretKey) {
        if (secureHash == null || secureHash.isBlank() || secretKey == null || secretKey.isBlank()) return false;
        Map<String, String> cleanFields = new HashMap<>(fields);
        cleanFields.remove("vnp_SecureHash");
        cleanFields.remove("vnp_SecureHashType");

        String calculatedHash = hashAllFields(cleanFields, secretKey);
        return calculatedHash.equalsIgnoreCase(secureHash);
    }

    // 5. Lấy địa chỉ IP thực tế của khách hàng (hỗ trợ qua Proxy/Nginx/Ngrok)
    public static String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
            if ("0:0:0:0:0:0:0:1".equals(ipAddress) || "127.0.0.1".equals(ipAddress)) {
                ipAddress = "127.0.0.1";
            }
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress != null ? ipAddress : "127.0.0.1";
    }
}
