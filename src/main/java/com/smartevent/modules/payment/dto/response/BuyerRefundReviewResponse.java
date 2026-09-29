package com.smartevent.modules.payment.dto.response;

import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BuyerRefundReviewResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        String reason,
        RefundReviewStatus status,
        String resolutionNote,
        String evidenceReference,
        Instant createdAt,
        Instant resolvedAt
) {
    public static BuyerRefundReviewResponse from(PaymentRefundReview review) {
        return new BuyerRefundReviewResponse(review.getId(), review.getOrderId(), review.getAmount(),
                review.getReason(), review.getStatus(), review.getResolutionNote(),
                review.getEvidenceReference(), review.getCreatedAt(), review.getResolvedAt());
    }
}
