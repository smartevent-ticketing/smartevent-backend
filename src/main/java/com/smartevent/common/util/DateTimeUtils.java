package com.smartevent.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public final class DateTimeUtils {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // Khởi tạo sẵn giá trị mặc định để chạy an toàn cả khi không có Spring Context (Unit Test)
    private static ZoneId systemZoneId = VIETNAM_ZONE;
    private static DateTimeFormatter defaultFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT);

    @Value("${app.date.timezone:Asia/Ho_Chi_Minh}")
    public void setTimezone(String timezone) {
        DateTimeUtils.systemZoneId = ZoneId.of(timezone);
    }

    @Value("${app.date.default-format:yyyy-MM-dd HH:mm:ss}")
    public void setDefaultFormat(String format) {
        DateTimeUtils.defaultFormatter = DateTimeFormatter.ofPattern(format);
    }

    public static ZoneId getSystemZoneId() {
        return systemZoneId;
    }

    // 1. Chuyển đổi Instant sang LocalDateTime theo múi giờ hệ thống
    public static LocalDateTime fromInstant(Instant instant) {
        if (instant == null) return null;
        return LocalDateTime.ofInstant(instant, systemZoneId);
    }

    // 2. Chuyển đổi LocalDateTime sang Instant theo múi giờ hệ thống
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return localDateTime.atZone(systemZoneId).toInstant();
    }

    // 3. Tính thời gian hết hạn giữ chỗ (Reservation TTL) chuẩn UTC Instant (Dùng lưu DB & so sánh)
    public static Instant calculateExpiresAt(int minutesToAdd) {
        return Instant.now().plus(Duration.ofMinutes(minutesToAdd));
    }

    // 4. Định dạng Instant sang chuỗi ngày giờ hiển thị
    public static String format(Instant instant) {
        if (instant == null) return "";
        return defaultFormatter.format(fromInstant(instant));
    }

    // 5. Định dạng Instant theo mẫu tùy chọn
    public static String format(Instant instant, String pattern) {
        if (instant == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(fromInstant(instant));
    }
}

