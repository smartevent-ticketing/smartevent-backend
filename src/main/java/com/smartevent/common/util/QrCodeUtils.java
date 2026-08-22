package com.smartevent.common.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class QrCodeUtils {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;

    private QrCodeUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // 1. Sinh mảng byte PNG từ nội dung văn bản / Token
    public static byte[] generateQrCodeBytes(String text, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // Mức độ phục hồi lỗi cao nhất (30%)
            hints.put(EncodeHintType.MARGIN, 1); // Viền trắng mỏng đẹp mắt

            BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception ex) {
            log.error("Lỗi khi sinh mã QR Code cho text {}: {}", text, ex.getMessage(), ex);
            throw new RuntimeException("Không thể tạo mã QR Code", ex);
        }
    }

    // 2. Sinh chuỗi Base64 Data URL (data:image/png;base64,...) để hiển thị trực tiếp lên thẻ <img> của Web / App
    public static String generateQrCodeBase64(String text, int width, int height) {
        byte[] qrBytes = generateQrCodeBytes(text, width, height);
        String base64Image = Base64.getEncoder().encodeToString(qrBytes);
        return "data:image/png;base64," + base64Image;
    }

    // 3. Hàm tiện ích mặc định kích thước 300x300 chuẩn mobile
    public static String generateQrCodeBase64(String text) {
        return generateQrCodeBase64(text, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
}