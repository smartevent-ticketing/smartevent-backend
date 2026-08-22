package com.smartevent.modules.ticket.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ticket.dto.request.TransferTicketRequest;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.dto.response.TicketTransferResponse;
import com.smartevent.modules.ticket.service.TicketService;
import com.smartevent.modules.ticket.service.TicketTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Ticket Management", description = "APIs quản lý vé điện tử, ví vé cá nhân, mã QR và chuyển nhượng vé")
public class TicketController {

    private final TicketService ticketService;
    private final TicketTransferService ticketTransferService;

    @GetMapping("/my-tickets")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy danh sách tất cả các vé điện tử trong ví của người dùng hiện tại")
    public ApiResponse<List<TicketResponse>> getMyTickets(@CurrentUser UserPrincipal currentUser) {
        return ApiResponse.success(ticketService.getMyTickets(currentUser.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lấy thông tin chi tiết tấm vé theo ID kèm hình ảnh mã QR (Base64 Data URL)")
    public ApiResponse<TicketResponse> getTicketById(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketService.getTicketById(id, currentUser.getId(), isAdmin));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    @Operation(summary = "Ban tổ chức / Admin xem danh sách vé đã phát hành của một sự kiện")
    public ApiResponse<List<TicketResponse>> getTicketsByEvent(
            @PathVariable UUID eventId,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(ticketService.getTicketsByEvent(eventId, currentUser.getId(), isAdmin));
    }

    @PostMapping("/{id}/refresh-qr")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Làm mới mã QR bảo mật của vé (Thu hồi mã QR cũ và sinh mã mới)")
    public ApiResponse<TicketResponse> refreshTicketQr(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(ticketService.refreshTicketQr(id, currentUser.getId()));
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chuyển nhượng quyền sở hữu vé sang email người khác (Thu hồi mã QR của người cũ)")
    public ApiResponse<TicketTransferResponse> transferTicket(
            @PathVariable UUID id,
            @Valid @RequestBody TransferTicketRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(ticketTransferService.transferTicket(id, currentUser.getId(), request));
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}