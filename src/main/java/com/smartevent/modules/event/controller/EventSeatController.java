package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.api.PageResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.event.dto.request.EventSeatRequest;
import com.smartevent.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.modules.event.dto.response.EventSeatResponse;
import com.smartevent.modules.event.service.EventSeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Event Seat Management", description = "APIs quản lý sơ đồ chỗ ngồi, sinh ghế tự động theo hàng, kiểm tra ghế trống")
public class EventSeatController {

    private final EventSeatService eventSeatService;

    @PostMapping("/api/v1/areas/{areaId}/seats/generate")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Sinh sơ đồ ghế ngồi tự động hàng loạt theo hàng và cột (A1..A20, B1..B20...)")
    public ApiResponse<List<EventSeatResponse>> generateSeats(
            @PathVariable UUID areaId,
            @Valid @RequestBody GenerateSeatsRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventSeatService.generateSeats(areaId, currentUser.getId(), isAdmin, request));
    }

    @GetMapping("/api/v1/areas/{areaId}/seats")
    @Operation(summary = "Lấy danh sách tất cả các ghế trong một phân khu (có phân trang)")
    public ApiResponse<PageResponse<EventSeatResponse>> getSeatsByArea(
            @PathVariable UUID areaId,
            @PageableDefault(size = 50, sort = "rowName", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ApiResponse.success(eventSeatService.getSeatsByArea(areaId, pageable));
    }

    @GetMapping("/api/v1/areas/{areaId}/seats/available")
    @Operation(summary = "Lấy danh sách các ghế đang còn trống (AVAILABLE) để người dùng chọn mua")
    public ApiResponse<List<EventSeatResponse>> getAvailableSeatsByArea(@PathVariable UUID areaId) {
        return ApiResponse.success(eventSeatService.getAvailableSeatsByArea(areaId));
    }

    @PostMapping("/api/v1/areas/{areaId}/seats")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Tạo một chiếc ghế đơn lẻ trong phân khu")
    public ApiResponse<EventSeatResponse> createSingleSeat(
            @PathVariable UUID areaId,
            @Valid @RequestBody EventSeatRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(eventSeatService.createSingleSeat(areaId, currentUser.getId(), isAdmin, request));
    }

    @DeleteMapping("/api/v1/seats/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Xóa một chiếc ghế theo ID")
    public ApiResponse<Void> deleteSeat(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        eventSeatService.deleteSeat(id, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Xóa ghế thành công");
    }

    @DeleteMapping("/api/v1/areas/{areaId}/seats")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Xóa toàn bộ sơ đồ ghế trong một phân khu")
    public ApiResponse<Void> deleteAllSeatsInArea(
            @PathVariable UUID areaId,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        eventSeatService.deleteAllSeatsInArea(areaId, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Đã xóa toàn bộ sơ đồ ghế của khu vực");
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
