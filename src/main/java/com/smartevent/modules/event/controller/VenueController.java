package com.smartevent.modules.event.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.modules.event.dto.request.VenueRequest;
import com.smartevent.modules.event.dto.response.VenueResponse;
import com.smartevent.modules.event.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venue Management", description = "APIs quản lý địa điểm tổ chức sự kiện (Sân vận động, Trung tâm hội nghị...)")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Ban tổ chức tạo địa điểm để chọn cho sự kiện")
    public ApiResponse<VenueResponse> createVenue(@Valid @RequestBody VenueRequest request) {
        return ApiResponse.success(venueService.createVenue(request));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các địa điểm đang hoạt động")
    public ApiResponse<List<VenueResponse>> getAllActiveVenues() {
        return ApiResponse.success(venueService.getAllActiveVenues());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết địa điểm theo ID")
    public ApiResponse<VenueResponse> getVenueById(@PathVariable UUID id) {
        return ApiResponse.success(venueService.getVenueById(id));
    }

    @GetMapping("/city/{city}")
    @Operation(summary = "Lấy danh sách địa điểm theo thành phố (Hà Nội, TP.HCM...)")
    public ApiResponse<List<VenueResponse>> getVenuesByCity(@PathVariable String city) {
        return ApiResponse.success(venueService.getVenuesByCity(city));
    }

}

