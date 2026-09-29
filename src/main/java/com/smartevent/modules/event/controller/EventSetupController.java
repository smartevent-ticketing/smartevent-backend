package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.CompleteDraftSetupRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.service.EventSetupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EventSetupController {
    private final EventSetupService eventSetupService;

    @PostMapping("/api/v1/events/setup")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(summary = "Tạo cấu hình sự kiện và gửi duyệt trong một giao dịch")
    public ApiResponse<EventResponse> createEventSetup(@Valid @RequestBody CreateEventSetupRequest request,
                                                      @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(eventSetupService.createAndSubmit(currentUser.getId(), request));
    }

    @PostMapping("/api/v1/events/{eventId}/complete-setup")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(summary = "Hoàn tất cấu hình vé của bản nháp và gửi duyệt trong một giao dịch")
    public ApiResponse<EventResponse> completeDraftSetup(@PathVariable UUID eventId,
            @Valid @RequestBody CompleteDraftSetupRequest request,
            @CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(eventSetupService.completeDraftAndSubmit(eventId, currentUser.getId(), request));
    }
}
