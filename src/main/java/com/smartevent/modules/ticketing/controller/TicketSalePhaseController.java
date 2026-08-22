package com.smartevent.modules.ticketing.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ticketing.dto.request.TicketPhaseRuleRequest;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.response.TicketPhaseRuleResponse;
import com.smartevent.modules.ticketing.dto.response.TicketSalePhaseResponse;
import com.smartevent.modules.ticketing.service.TicketSalePhaseService;
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
@Tag(name = "Ticket Sale Phase Management", description = "APIs quản lý đợt mở bán vé (Early Bird, Public Sale, VIP Presale) và giới hạn mua")
public class TicketSalePhaseController {

    private final TicketSalePhaseService ticketSalePhaseService;

    @PostMapping("/api/v1/ticket-types/{ticketTypeId}/sale-phases")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Tạo đợt mở bán mới cho loại vé (Yêu cầu ADMIN hoặc Ban tổ chức)")
    public ApiResponse<TicketSalePhaseResponse> createSalePhase(
            @PathVariable UUID ticketTypeId,
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody TicketSalePhaseRequest request
    ){
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketSalePhaseService.createSalePhase(ticketTypeId, currentUser.getId(), isAdmin, request));
    }

    @GetMapping("/api/v1/ticket-types/{ticketTypeId}/sale-phases")
    @Operation(summary = "Lấy danh sách các đợt mở bán của một loại vé")
    public ApiResponse<List<TicketSalePhaseResponse>> getSalePhasesByTicketTypeId(@PathVariable UUID ticketTypeId) {
        return ApiResponse.success(ticketSalePhaseService.getSalePhasesByTicketTypeId(ticketTypeId));
    }

    @GetMapping("/api/v1/events/{eventId}/sale-phases")
    @Operation(summary = "Lấy danh sách tất cả các đợt mở bán của một sự kiện")
    public ApiResponse<List<TicketSalePhaseResponse>> getSalePhasesByEventId(@PathVariable UUID eventId) {
        return ApiResponse.success(ticketSalePhaseService.getSalePhasesByEventId(eventId));
    }

    @GetMapping("/api/v1/sale-phases/{id}")
    @Operation(summary = "Lấy thông tin chi tiết một đợt mở bán theo ID")
    public ApiResponse<TicketSalePhaseResponse> getSalePhaseById(@PathVariable UUID id) {
        return ApiResponse.success(ticketSalePhaseService.getSalePhaseById(id));
    }

    @PutMapping("/api/v1/sale-phases/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Cập nhật thông tin đợt mở bán theo ID")
    public ApiResponse<TicketSalePhaseResponse> updateSalePhase(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody TicketSalePhaseRequest request
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketSalePhaseService.updateSalePhase(id, currentUser.getId(), isAdmin, request));
    }

    @PatchMapping("/api/v1/sale-phases/{id}/status")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Chuyển trạng thái đợt mở bán (ACTIVE, PAUSED, CLOSED)")
    public ApiResponse<TicketSalePhaseResponse> updateStatus(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser,
            @RequestBody SalePhaseStatus newStatus
            ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketSalePhaseService.updateStatus(id, currentUser.getId(), isAdmin, newStatus));
    }

    @DeleteMapping("/api/v1/sale-phases/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Xóa mềm một đợt mở bán theo ID")
    public ApiResponse<Void> deleteSalePhase(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        ticketSalePhaseService.deleteSalePhase(id, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Đã xóa đợt mở bán thành công");
    }

    @PostMapping("/api/v1/sale-phases/{salePhaseId}/rules")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Thêm quy tắc kiểm soát mua vé cho đợt mở bán (VD: giới hạn 2 vé/user)")
    public ApiResponse<TicketPhaseRuleResponse> addRule(
            @PathVariable UUID salePhaseId,
            @CurrentUser UserPrincipal currentUser,
            @Valid @RequestBody TicketPhaseRuleRequest request
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketSalePhaseService.addRule(salePhaseId, currentUser.getId(), isAdmin, request));
    }

    @DeleteMapping("/api/v1/sale-phases/rules/{ruleId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Xóa một quy tắc kiểm soát mua vé")
    public ApiResponse<Void> deleteRule(
            @PathVariable UUID ruleId,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        ticketSalePhaseService.deleteRule(ruleId, currentUser.getId(), isAdmin);
        return ApiResponse.ok("Đã xóa luật thành công");
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
