package com.smartevent.modules.ticketing.dto.response;

import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TicketSalePhaseResponse(
        UUID id,
        UUID ticketTypeId,
        String ticketTypeName,
        String name,
        BigDecimal price,
        Integer quantity,
        Instant saleStartAt,
        Instant saleEndAt,
        Instant soldOutAt,
        Integer maxPerOrder,
        Integer maxPerUser,
        SalePhaseStatus status,
        List<TicketPhaseRuleResponse> rules,
        Instant createdAt
) {
    public static TicketSalePhaseResponse of(
            TicketSalePhase phase,
            String ticketTypeName,
            List<TicketPhaseRuleResponse> rules
    ) {
        return new TicketSalePhaseResponse(
                phase.getId(),
                phase.getTicketTypeId(),
                ticketTypeName,
                phase.getName(),
                phase.getPrice(),
                phase.getQuantity(),
                phase.getSaleStartAt(),
                phase.getSaleEndAt(),
                phase.getSoldOutAt(),
                phase.getMaxPerOrder(),
                phase.getMaxPerUser(),
                phase.getStatus(),
                rules != null ? rules : List.of(),
                phase.getCreatedAt()
        );
    }
}