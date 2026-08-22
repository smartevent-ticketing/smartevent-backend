package com.smartevent.modules.ticketing.controller;

import com.smartevent.common.api.ApiResponse;
import com.smartevent.modules.ticketing.dto.response.InventoryCounterResponse;
import com.smartevent.modules.ticketing.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Inventory Counter Management", description = "APIs tra cứu tồn kho vé thời gian thực (Tổng vé, Vé đang giữ chỗ, Vé đã bán)")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/api/v1/sale-phases/{salePhaseId}/inventory")
    @Operation(summary = "Tra cứu số lượng tồn kho vé thời gian thực của một đợt mở bán")
    public ApiResponse<InventoryCounterResponse> getCounterBySalePhaseId(@PathVariable UUID salePhaseId) {
        return ApiResponse.success(inventoryService.getCounterBySalePhaseId(salePhaseId));
    }

    @GetMapping("/api/v1/events/{eventId}/inventory")
    @Operation(summary = "Tra cứu số lượng tồn kho vé thời gian thực của toàn bộ sự kiện")
    public ApiResponse<List<InventoryCounterResponse>> getCountersByEventId(@PathVariable UUID eventId) {
        return ApiResponse.success(inventoryService.getCountersByEventId(eventId));
    }
}