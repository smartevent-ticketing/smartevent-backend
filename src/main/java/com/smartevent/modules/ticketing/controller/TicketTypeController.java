package com.smartevent.modules.ticketing.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.dto.response.TicketTypeResponse;
import com.smartevent.modules.ticketing.service.TicketTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Ticket Type Management", description = "APIs quản lý các hạng vé (VIP, Standard, Early Bird, VVIP...)")
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PostMapping("/api/v1/events/{eventId}/ticket-types")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Tạo hạng vé mới cho sự kiện (Yêu cầu ADMIN hoặc Ban tổ chức)")
    public ApiResponse<TicketTypeResponse> createTicketType(
            @PathVariable UUID eventId,
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody TicketTypeRequest request
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketTypeService.createTicketType(eventId, currentUser.getId(), isAdmin, request));
    }

    @GetMapping("/api/v1/events/{eventId}/ticket-types")
    @Operation(summary = "Lấy danh sách tất cả các hạng vé của một sự kiện")
    public ApiResponse<List<TicketTypeResponse>> getTicketTypesByEventId(@PathVariable UUID eventId) {
        return ApiResponse.success(ticketTypeService.getTicketTypesByEventId(eventId));
    }

    @GetMapping("/api/v1/areas/{areaId}/ticket-types")
    @Operation(summary = "Lấy danh sách các hạng vé thuộc một phân khu cụ thể")
    public ApiResponse<List<TicketTypeResponse>> getTicketTypesByAreaId(@PathVariable UUID areaId) {
        return ApiResponse.success(ticketTypeService.getTicketTypesByAreaId(areaId));
    }

    @GetMapping("/api/v1/ticket-types/{id}")
    @Operation(summary = "Lấy thông tin chi tiết một hạng vé theo ID")
    public ApiResponse<TicketTypeResponse> getTicketTypeById(@PathVariable UUID id) {
        return ApiResponse.success(ticketTypeService.getTicketTypeById(id));
    }

    @PutMapping("/api/v1/ticket-types/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Cập nhật thông tin hạng vé theo ID (Yêu cầu ADMIN hoặc Ban tổ chức)")
    public ApiResponse<TicketTypeResponse> updateTicketType(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody TicketTypeRequest request
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketTypeService.updateTicketType(id, currentUser.getId(), isAdmin, request));
    }

    @DeleteMapping("/api/v1/ticket-types/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Xóa mềm một hạng vé theo ID (Yêu cầu ADMIN hoặc Ban tổ chức)")
    public ApiResponse<Void> deleteTicketType(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser) {
        boolean isAdmin = checkIsAdmin(currentUser);
        ticketTypeService.deleteTicketType(id, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Đã xóa loại vé có id: " + id + "thành công");
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}

