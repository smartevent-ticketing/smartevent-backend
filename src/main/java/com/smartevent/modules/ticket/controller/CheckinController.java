package com.smartevent.modules.ticket.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.ticket.dto.request.CheckinRequest;
import com.smartevent.modules.ticket.dto.response.CheckinResponse;
import com.smartevent.modules.ticket.entity.TicketCheckin;
import com.smartevent.modules.ticket.repository.TicketCheckinRepository;
import com.smartevent.modules.ticket.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
@Tag(name = "Gate Check-in Management", description = "APIs soát vé tại cổng sự kiện, quét mã QR thời gian thực và chống quét trùng lặp")
public class CheckinController {

    private final CheckinService checkinService;
    private final TicketCheckinRepository checkinRepository;

    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(summary = "Quét soát vé tại cổng sự kiện (Xác thực mã QR/Mã vé, chống quét trùng lặp DUPLICATE)")
    public ApiResponse<CheckinResponse> scanTicket(
            @Valid @RequestBody CheckinRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(checkinService.processCheckin(request, currentUser.getId()));
    }

    @GetMapping("/events/{eventId}/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(summary = "Xem toàn bộ lịch sử các lượt quét vé tại các cổng của sự kiện")
    public ApiResponse<List<TicketCheckin>> getCheckinHistory(@PathVariable UUID eventId) {
        return ApiResponse.success(checkinRepository.findByEventIdOrderByCheckedAtDesc(eventId));
    }
}