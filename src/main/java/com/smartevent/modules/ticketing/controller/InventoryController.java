package com.smartevent.modules.ticketing.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.modules.ticketing.dto.response.InventoryCounterResponse;
import com.smartevent.modules.ticketing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/api/v1/sale-phases/{salePhaseId}/inventory")
    public ApiResponse<InventoryCounterResponse> getCounterBySalePhaseId(@PathVariable UUID salePhaseId) {
        return ApiResponse.success(inventoryService.getCounterBySalePhaseId(salePhaseId));
    }

    @GetMapping("/api/v1/events/{eventId}/inventory")
    public ApiResponse<List<InventoryCounterResponse>> getCountersByEventId(@PathVariable UUID eventId) {
        return ApiResponse.success(inventoryService.getCountersByEventId(eventId));
    }
}