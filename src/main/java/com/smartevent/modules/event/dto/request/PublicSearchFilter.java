package com.smartevent.modules.event.dto.request;
import java.math.BigDecimal;
import java.time.Instant;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.exception.EventException;
public record PublicSearchFilter(Instant startFrom, Instant startBefore, BigDecimal maxPrice, String priceComparison) {
    public PublicSearchFilter {
        if (startFrom != null && startBefore != null && !startFrom.isBefore(startBefore))
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Khoảng ngày không hợp lệ");
        if (maxPrice != null && (maxPrice.signum() < 0 || maxPrice.compareTo(new BigDecimal("1000000000")) > 0))
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Giá nằm ngoài phạm vi hỗ trợ");
        if (priceComparison == null) priceComparison = "LTE";
        if (!priceComparison.equals("LT") && !priceComparison.equals("LTE"))
            throw new EventException(ErrorCode.VALIDATION_ERROR, "Phép so sánh giá không hợp lệ");
    }
}
