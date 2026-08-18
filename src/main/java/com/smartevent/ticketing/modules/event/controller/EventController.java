package com.smartevent.ticketing.modules.event.controller;

import com.smartevent.ticketing.common.api.ApiResponse;
import com.smartevent.ticketing.common.api.PageResponse;
import com.smartevent.ticketing.common.security.CurrentUser;
import com.smartevent.ticketing.infrastructure.security.UserPrincipal;
import com.smartevent.ticketing.modules.event.dto.request.CreateEventRequest;
import com.smartevent.ticketing.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.ticketing.modules.event.dto.response.EventResponse;
import com.smartevent.ticketing.modules.event.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    public ApiResponse<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @CurrentUser UserPrincipal userPrincipal) {
        return ApiResponse.success(eventService.createEvent(userPrincipal.getId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    public ApiResponse<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request,
            @CurrentUser UserPrincipal userPrincipal
    ) {
        boolean isAdmin = checkIsAdmin(userPrincipal);
        return ApiResponse.success(eventService.updateEvent(id, userPrincipal.getId(), isAdmin, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getPublishedEvents(
            @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(eventService.getPublishedEvents(pageable));
    }

    @GetMapping("/my-events")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<PageResponse<EventResponse>> getMyEvents(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(eventService.getEventsByOrganizer(currentUser.getId(), pageable));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<EventResponse> getEventBySlug(@PathVariable String slug) {
        return ApiResponse.success(eventService.getEventBySlug(slug));
    }

    @GetMapping("/{id}")
    public ApiResponse<EventResponse> getEventById(@PathVariable UUID id) {
        return ApiResponse.success(eventService.getEventById(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<EventResponse> submitEvent(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventService.submitForApproval(id, currentUser.getId(), isAdmin));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EventResponse> approveEvent(@PathVariable UUID id) {
        return ApiResponse.success(eventService.approveEvent(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EventResponse> rejectEvent(
            @PathVariable UUID id,
            @RequestParam(value = "reason", defaultValue = "Không đạt yêu cầu kiểm duyệt") String reason
    ) {
        return ApiResponse.success(eventService.rejectEvent(id, reason));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public ApiResponse<EventResponse> cancelEvent(
            @PathVariable UUID id,
            @RequestParam(value = "reason", defaultValue = "Đã bị hủy vì không đủ điều kiện tổ chức") String reason,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventService.cancelEvent(id, currentUser.getId(), isAdmin, reason));
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
