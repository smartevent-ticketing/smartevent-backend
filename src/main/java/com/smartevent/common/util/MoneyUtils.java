package com.smartevent.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyUtils {

    public static final String CURRENCY_VND = "VND";
    public static final Locale LOCALE_VN = new Locale("vi", "VN");

    // Chế độ làm tròn mặc định cho tài chính (Half-Even / Banker's rounding)
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_EVEN;

    // Số chữ số thập phân mặc định nếu không tìm thấy tiền tệ (USD là 2, VND là 0)
    private static final int DEFAULT_SCALE = 2;

    // Ẩn hàm khởi tạo để ngăn tạo object
    private MoneyUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Tạo một BigDecimal chuẩn hóa dựa theo mã tiền tệ
     */
    public static BigDecimal create(String amount, String currencyCode) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        int scale = getScaleForCurrency(currencyCode);
        return new BigDecimal(amount.trim()).setScale(scale, DEFAULT_ROUNDING);
    }

    /**
     * Tạo BigDecimal từ số nguyên (phù hợp với VNĐ)
     */
    public static BigDecimal of(long amount) {
        return BigDecimal.valueOf(amount).setScale(0, DEFAULT_ROUNDING);
    }

    /**
     * Cộng hai số tiền cùng loại tiền tệ mang tính an toàn (null-safe)
     */
    public static BigDecimal add(BigDecimal amount1, BigDecimal amount2, String currencyCode) {
        BigDecimal a1 = (amount1 == null) ? BigDecimal.ZERO : amount1;
        BigDecimal a2 = (amount2 == null) ? BigDecimal.ZERO : amount2;
        int scale = getScaleForCurrency(currencyCode);
        return a1.add(a2).setScale(scale, DEFAULT_ROUNDING);
    }

    /**
     * Trừ hai số tiền cùng loại tiền tệ mang tính an toàn (null-safe)
     */
    public static BigDecimal subtract(BigDecimal amount1, BigDecimal amount2, String currencyCode) {
        BigDecimal a1 = (amount1 == null) ? BigDecimal.ZERO : amount1;
        BigDecimal a2 = (amount2 == null) ? BigDecimal.ZERO : amount2;
        int scale = getScaleForCurrency(currencyCode);
        return a1.subtract(a2).setScale(scale, DEFAULT_ROUNDING);
    }

    /**
     * Tính thành tiền: đơn giá * số lượng
     */
    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null || quantity <= 0) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /**
     * Định dạng nhanh tiền tệ Việt Nam (Ví dụ: "1.500.000 ₫")
     */
    public static String formatVnd(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        NumberFormat format = NumberFormat.getCurrencyInstance(LOCALE_VN);
        format.setCurrency(Currency.getInstance(CURRENCY_VND));
        return format.format(amount);
    }

    /**
     * Định dạng hiển thị tiền tệ theo Locale (Ví dụ: "100.000 ₫" hoặc "$100.00")
     */
    public static String format(BigDecimal amount, String currencyCode, Locale locale) {
        if (amount == null) return "";
        Currency currency = Currency.getInstance(currencyCode);
        NumberFormat format = NumberFormat.getCurrencyInstance(locale != null ? locale : LOCALE_VN);
        format.setCurrency(currency);
        return format.format(amount);
    }

    /**
     * Kiểm tra số tiền có lớn hơn 0 hay không
     */
    public static boolean isGreaterThanZero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Lấy số chữ số phần thập phân theo tiêu chuẩn của từng loại tiền tệ
     */
    private static int getScaleForCurrency(String currencyCode) {
        if (CURRENCY_VND.equalsIgnoreCase(currencyCode)) {
            return 0; // VNĐ không có số lẻ thập phân
        }
        try {
            return Currency.getInstance(currencyCode).getDefaultFractionDigits();
        } catch (IllegalArgumentException | NullPointerException e) {
            return DEFAULT_SCALE;
        }
    }
}

