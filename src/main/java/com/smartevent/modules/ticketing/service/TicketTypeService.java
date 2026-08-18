package com.smartevent.modules.ticketing.service;

import com.smartevent.modules.ticketing.dto.request.TicketTypeRequest;
import com.smartevent.modules.ticketing.dto.response.TicketTypeResponse;

import java.util.List;
import java.util.UUID;

public interface TicketTypeService {

    TicketTypeResponse createTicketType(UUID eventId, UUID currentUserId, boolean isAdmin, TicketTypeRequest request);

    List<TicketTypeResponse> getTicketTypesByEventId(UUID eventId);

    List<TicketTypeResponse> getTicketTypesByAreaId(UUID areaId);

    TicketTypeResponse getTicketTypeById(UUID id);

    TicketTypeResponse updateTicketType(UUID id, UUID currentUserId, boolean isAdmin, TicketTypeRequest request);

    void deleteTicketType(UUID id, UUID currentUserId, boolean isAdmin);
}
