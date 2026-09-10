package com.smartevent.application.eventsetup;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.response.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
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
}
