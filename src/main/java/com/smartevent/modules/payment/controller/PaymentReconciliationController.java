package com.smartevent.modules.payment.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.RefundReviewStatus;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.payment.dto.request.UpdateRefundReviewRequest;
import com.smartevent.modules.payment.entity.PaymentRefundReview;
import com.smartevent.modules.payment.entity.PaymentRefundReviewAction;
import com.smartevent.modules.payment.repository.PaymentRefundReviewActionRepository;
import com.smartevent.modules.payment.repository.PaymentRefundReviewRepository;
import com.smartevent.modules.payment.service.PaymentReconciliationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
    private final PaymentRefundReviewActionRepository actionRepository;
    private final PaymentReconciliationService reconciliationService;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<PaymentRefundReview>> list(
            @RequestParam(defaultValue = "REQUIRED") RefundReviewStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(reviewRepository.findByStatusOrderByCreatedAtAsc(status, pageable)));
    }

    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public ApiResponse<List<PaymentRefundReviewAction>> history(@PathVariable UUID id) {
        if (!reviewRepository.existsById(id)) {
            throw new com.smartevent.common.error.BusinessException(
                    com.smartevent.common.error.ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ đối soát");
        }
        return ApiResponse.success(actionRepository.findByReviewIdOrderByCreatedAtAsc(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<PaymentRefundReview> update(@PathVariable UUID id,
            @CurrentUser UserPrincipal admin,
            @Valid @RequestBody UpdateRefundReviewRequest request) {
        return ApiResponse.success(reconciliationService.updateReview(id, admin.getId(), request));
    }
}
