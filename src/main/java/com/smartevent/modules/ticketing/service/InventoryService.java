package com.smartevent.modules.ticketing.service;

import com.smartevent.modules.ticketing.dto.response.InventoryCounterResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    void initCounter(UUID eventId, UUID eventAreaId, UUID ticketTypeId, UUID salePhaseId, int totalQuantity);

    InventoryCounterResponse getCounterBySalePhaseId(UUID salePhaseId);

    List<InventoryCounterResponse> getCountersByEventId(UUID eventId);

    void holdInventory(UUID salePhaseId, int quantity);

    void releaseHeldInventory(UUID salePhaseId, int quantity);

    void confirmPurchase(UUID salePhaseId, int quantity);

    void processRefund(UUID salePhaseId, int quantity);

    void updateTotalQuantity(UUID salePhaseId, int newTotalQuantity);
}