package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.modules.event.dto.request.EventAreaRequest;
import com.smartevent.ticketing.modules.event.dto.response.EventAreaResponse;

import java.util.List;
import java.util.UUID;

public interface EventAreaService {

    EventAreaResponse createArea(UUID eventId, UUID currentUserId, boolean isAdmin, EventAreaRequest request);

    List<EventAreaResponse> getAreasByEventId(UUID eventId);

    EventAreaResponse getAreaById(UUID areaId);

    EventAreaResponse updateArea(UUID areaId, UUID currentUserId, boolean isAdmin, EventAreaRequest request);

    void deleteArea(UUID areaId, UUID currentUserId, boolean isAdmin);
}