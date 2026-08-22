package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Event Management", description = "APIs quản lý vòng đời sự kiện (Tạo mới, Cập nhật, Duyệt, Hủy, Xem danh sách)")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    @Operation(summary = "Tạo sự kiện mới ở trạng thái DRAFT (Yêu cầu ADMIN hoặc ORGANIZER)")
    public ApiResponse<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            @CurrentUser UserPrincipal userPrincipal) {
        return ApiResponse.success(eventService.createEvent(userPrincipal.getId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    @Operation(summary = "Cập nhật thông tin sự kiện theo ID (Yêu cầu ADMIN hoặc Ban tổ chức chính chủ)")
    public ApiResponse<EventResponse> updateEvent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEventRequest request,
            @CurrentUser UserPrincipal userPrincipal
    ) {
        boolean isAdmin = checkIsAdmin(userPrincipal);
        return ApiResponse.success(eventService.updateEvent(id, userPrincipal.getId(), isAdmin, request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các sự kiện đã được duyệt và đang mở bán (PUBLISHED)")
    public ApiResponse<PageResponse<EventResponse>> getPublishedEvents(
            @PageableDefault(size = 10, sort = "startTime", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(eventService.getPublishedEvents(pageable));
    }

    @GetMapping("/my-events")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Lấy danh sách sự kiện do chính Ban tổ chức hiện tại tạo ra")
    public ApiResponse<PageResponse<EventResponse>> getMyEvents(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(eventService.getEventsByOrganizer(currentUser.getId(), pageable));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Lấy thông tin chi tiết sự kiện theo slug thân thiện")
    public ApiResponse<EventResponse> getEventBySlug(@PathVariable String slug) {
        return ApiResponse.success(eventService.getEventBySlug(slug));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết sự kiện theo ID")
    public ApiResponse<EventResponse> getEventById(@PathVariable UUID id) {
        return ApiResponse.success(eventService.getEventById(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Gửi sự kiện lên Admin để yêu cầu phê duyệt (DRAFT -> SUBMITTED)")
    public ApiResponse<EventResponse> submitEvent(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventService.submitForApproval(id, currentUser.getId(), isAdmin));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Phê duyệt sự kiện và đưa vào hoạt động (SUBMITTED -> PUBLISHED) (Yêu cầu ADMIN)")
    public ApiResponse<EventResponse> approveEvent(@PathVariable UUID id) {
        return ApiResponse.success(eventService.approveEvent(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Từ chối duyệt sự kiện kèm lý do (SUBMITTED -> REJECTED) (Yêu cầu ADMIN)")
    public ApiResponse<EventResponse> rejectEvent(
            @PathVariable UUID id,
            @RequestParam(value = "reason", defaultValue = "Không đạt yêu cầu kiểm duyệt") String reason
    ) {
        return ApiResponse.success(eventService.rejectEvent(id, reason));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Hủy bỏ sự kiện đã lên lịch kèm lý do thông báo")
    public ApiResponse<EventResponse> cancelEvent(
            @PathVariable UUID id,
            @RequestParam(value = "reason", defaultValue = "Đã bị hủy vì không đủ điều kiện tổ chức") String reason,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventService.cancelEvent(id, currentUser.getId(), isAdmin, reason));
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

