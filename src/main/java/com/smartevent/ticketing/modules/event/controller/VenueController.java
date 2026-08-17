package com.smartevent.ticketing.modules.event.controller;

import com.smartevent.ticketing.common.api.ApiResponse;
import com.smartevent.ticketing.modules.event.dto.request.VenueRequest;
import com.smartevent.ticketing.modules.event.dto.response.VenueResponse;
import com.smartevent.ticketing.modules.event.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    public ApiResponse<VenueResponse> createVenue(@Valid @RequestBody VenueRequest request) {
        return ApiResponse.success(venueService.createVenue(request));
    }

    @GetMapping
    public ApiResponse<List<VenueResponse>> getAllActiveVenues() {
        return ApiResponse.success(venueService.getAllActiveVenues());
    }

    @GetMapping("/{id}")
    public ApiResponse<VenueResponse> getVenueById(@PathVariable UUID id) {
        return ApiResponse.success(venueService.getVenueById(id));
    }

    @GetMapping("/city/{city}")
    public ApiResponse<List<VenueResponse>> getVenuesByCity(@PathVariable String city) {
        return ApiResponse.success(venueService.getVenuesByCity(city));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ORGANIZER')")
    public ApiResponse<VenueResponse> updateVenue(
            @PathVariable UUID id,
            @Valid @RequestBody VenueRequest request)
    {
        return ApiResponse.success(venueService.updateVenue(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteVenue(@PathVariable UUID id) {

        venueService.deleteVenue(id);
        return ApiResponse.ok("Xóa địa điểm thành công");
    }
}
