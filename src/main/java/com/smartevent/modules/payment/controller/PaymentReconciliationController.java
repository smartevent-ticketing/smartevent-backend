package com.smartevent.modules.payment.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.repository.PaymentRefundReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payment-refund-reviews")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PaymentReconciliationController {
    private final PaymentRefundReviewRepository reviewRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PaymentRefundReview>> pending(@PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(reviewRepository.findByStatusOrderByCreatedAtAsc("REQUIRED", pageable)));
    }
}
