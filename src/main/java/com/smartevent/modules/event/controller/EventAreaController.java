package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.dto.response.EventAreaResponse;
import com.smartevent.modules.event.service.EventAreaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EventAreaController {

    private final EventAreaService eventAreaService;

    @PostMapping("/api/v1/events/{eventId}/areas")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<EventAreaResponse> createArea(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventAreaRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventAreaService.createArea(eventId, currentUser.getId(), isAdmin, request));
    }

    @GetMapping("/api/v1/events/{eventId}/areas")
    public ApiResponse<List<EventAreaResponse>> getAreasByEventId(@PathVariable UUID eventId) {
        return ApiResponse.success(eventAreaService.getAreasByEventId(eventId));
    }

    @GetMapping("/api/v1/areas/{id}")
    public ApiResponse<EventAreaResponse> getAreaById(@PathVariable UUID id) {
        return ApiResponse.success(eventAreaService.getAreaById(id));
    }

    @PutMapping("/api/v1/areas/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<EventAreaResponse> updateArea(
            @PathVariable UUID id,
            @Valid @RequestBody EventAreaRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventAreaService.updateArea(id, currentUser.getId(), isAdmin, request));
    }

    @DeleteMapping("/api/v1/areas/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<Void> deleteArea(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        eventAreaService.deleteArea(id, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Xóa khu vực vé thành công");
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
