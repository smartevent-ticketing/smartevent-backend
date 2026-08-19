package com.smartevent.modules.ticketing.service;

import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.modules.ticketing.dto.request.TicketPhaseRuleRequest;
import com.smartevent.modules.ticketing.dto.request.TicketSalePhaseRequest;
import com.smartevent.modules.ticketing.dto.response.TicketPhaseRuleResponse;
import com.smartevent.modules.ticketing.dto.response.TicketSalePhaseResponse;

import java.util.List;
import java.util.UUID;

public interface TicketSalePhaseService {

    TicketSalePhaseResponse createSalePhase(UUID ticketTypeId, UUID currentUserId, boolean isAdmin, TicketSalePhaseRequest request);

    List<TicketSalePhaseResponse> getSalePhasesByTicketTypeId(UUID ticketTypeId);

    List<TicketSalePhaseResponse> getSalePhasesByEventId(UUID eventId);

    TicketSalePhaseResponse getSalePhaseById(UUID id);

    TicketSalePhaseResponse updateSalePhase(UUID id, UUID currentUserId, boolean isAdmin, TicketSalePhaseRequest request);

    TicketSalePhaseResponse updateStatus(UUID id, UUID currentUserId, boolean isAdmin, SalePhaseStatus newStatus);

    void deleteSalePhase(UUID id, UUID currentUserId, boolean isAdmin);

    TicketPhaseRuleResponse addRule(UUID salePhaseId, UUID currentUserId, boolean isAdmin, TicketPhaseRuleRequest request);

    void deleteRule(UUID ruleId, UUID currentUserId, boolean isAdmin);
}