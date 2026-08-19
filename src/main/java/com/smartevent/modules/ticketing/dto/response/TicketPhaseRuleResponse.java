package com.smartevent.modules.ticketing.dto.response;

import com.smartevent.modules.ticketing.entity.TicketPhaseRule;

import java.time.Instant;
import java.util.UUID;

public record TicketPhaseRuleResponse(
        UUID id,
        UUID salePhaseId,
        String ruleType,
        String ruleValue,
        Instant createdAt
) {
    public static TicketPhaseRuleResponse from(TicketPhaseRule rule) {
        return new TicketPhaseRuleResponse(
                rule.getId(),
                rule.getSalePhaseId(),
                rule.getRuleType(),
                rule.getRuleValue(),
                rule.getCreatedAt()
        );
    }
}