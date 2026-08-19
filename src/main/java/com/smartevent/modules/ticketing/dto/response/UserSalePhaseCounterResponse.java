package com.smartevent.modules.ticketing.dto.response;

import com.smartevent.modules.ticketing.entity.UserSalePhaseCounter;

import java.time.Instant;
import java.util.UUID;

public record UserSalePhaseCounterResponse(
        UUID id,
        UUID userId,
        UUID salePhaseId,
        Integer heldQuantity,
        Integer purchasedQuantity,
        Integer refundedQuantity,
        Integer effectiveOccupiedQuantity, // ✅ Thêm trường này
        Instant updatedAt
) {
    public static UserSalePhaseCounterResponse from(UserSalePhaseCounter counter) {
        return new UserSalePhaseCounterResponse(
                counter.getId(),
                counter.getUserId(),
                counter.getSalePhaseId(),
                counter.getHeldQuantity(),
                counter.getPurchasedQuantity(),
                counter.getRefundedQuantity(),
                counter.getEffectiveOccupiedQuantity(), // ✅ Và truyền giá trị ở đây
                counter.getUpdatedAt()
        );
    }
}