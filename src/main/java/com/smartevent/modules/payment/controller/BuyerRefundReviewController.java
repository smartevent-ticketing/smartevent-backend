package com.smartevent.modules.payment.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.payment.dto.request.BuyerRefundReviewRequest;
import com.smartevent.modules.payment.dto.response.BuyerRefundReviewResponse;
import com.smartevent.modules.payment.service.PaymentReconciliationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/refund-review")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class BuyerRefundReviewController {
    private final PaymentReconciliationService reconciliationService;

    @GetMapping
    public ApiResponse<BuyerRefundReviewResponse> get(@PathVariable UUID orderId,
            @CurrentUser UserPrincipal buyer) {
        return ApiResponse.success(reconciliationService.findBuyerReview(orderId, buyer.getId())
                .map(BuyerRefundReviewResponse::from).orElse(null));
    }

    @PostMapping
    public ApiResponse<BuyerRefundReviewResponse> create(@PathVariable UUID orderId,
            @CurrentUser UserPrincipal buyer, @Valid @RequestBody BuyerRefundReviewRequest request) {
        return ApiResponse.success(BuyerRefundReviewResponse.from(
                reconciliationService.requestReviewByBuyer(orderId, buyer.getId(), request.reason())));
    }
}
