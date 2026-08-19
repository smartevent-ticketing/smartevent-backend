package com.smartevent.modules.ticketing.dto.response;

import com.smartevent.modules.ticketing.entity.InventoryCounter;

import java.time.Instant;
import java.util.UUID;

public record InventoryCounterResponse(
        UUID id,
        UUID eventId,
        UUID eventAreaId,
        UUID ticketTypeId,
        UUID salePhaseId,
        Integer totalQuantity,
        Integer heldQuantity,
        Integer soldQuantity,
        Integer availableQuantity,
        Instant updatedAt
) {
    public static InventoryCounterResponse from(InventoryCounter counter) {
        return new InventoryCounterResponse(
                counter.getId(),
                counter.getEventId(),
                counter.getEventAreaId(),
                counter.getTicketTypeId(),
                counter.getSalePhaseId(),
                counter.getTotalQuantity(),
                counter.getHeldQuantity(),
                counter.getSoldQuantity(),
                counter.getAvailableQuantity(),
                counter.getUpdatedAt()
        );
    }
}