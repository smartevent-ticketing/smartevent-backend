package com.smartevent.modules.event.dto.response;
import java.math.BigDecimal;
import java.util.UUID;
public record PriceEvidence(UUID eventId, UUID ticketTypeId, UUID salePhaseId, BigDecimal price) {}
