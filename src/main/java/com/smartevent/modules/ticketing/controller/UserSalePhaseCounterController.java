package com.smartevent.modules.ticketing.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ticketing.dto.response.UserSalePhaseCounterResponse;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserSalePhaseCounterController {

    private final UserSalePhaseCounterService userSalePhaseCounterService;

    @GetMapping("/api/v1/sale-phases/{salePhaseId}/my-counter")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserSalePhaseCounterResponse> getMyCounter(
            @PathVariable UUID salePhaseId,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(userSalePhaseCounterService.getUserCounter(currentUser.getId(), salePhaseId));
    }
}