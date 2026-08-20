package com.smartevent.modules.reservation.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.common.security.CurrentUser;
import com.smartevent.infrastructure.security.UserPrincipal;
import com.smartevent.modules.reservation.dto.request.CreateReservationRequest;
import com.smartevent.modules.reservation.dto.response.ReservationResponse;
import com.smartevent.modules.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/api/v1/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(reservationService.createReservation(currentUser.getId(), request));
    }

    @GetMapping("/api/v1/reservations/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReservationResponse> getReservationById(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        return ApiResponse.success(reservationService.getReservationById(id, currentUser.getId(), isAdmin));
    }

    @GetMapping("/api/v1/reservations/active")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ReservationResponse> getMyActiveReservation(
            @RequestParam UUID eventId,
            @CurrentUser UserPrincipal currentUser
    ) {
        return ApiResponse.success(reservationService.getMyActiveReservation(currentUser.getId(), eventId));
    }

    @PostMapping("/api/v1/reservations/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> cancelReservation(
            @PathVariable UUID id,
            @CurrentUser UserPrincipal currentUser
    ) {
        boolean isAdmin = checkIsAdmin(currentUser);
        reservationService.cancelReservation(id, currentUser.getId(), isAdmin);
        return ApiResponse.success(null);
    }

    private boolean checkIsAdmin(UserPrincipal currentUser) {
        return currentUser != null && currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}