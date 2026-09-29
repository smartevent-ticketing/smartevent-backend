package com.smartevent.modules.event.service;

import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.CompleteDraftSetupRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import java.util.UUID;

public interface EventSetupService {
    EventResponse createAndSubmit(UUID organizerId, CreateEventSetupRequest request);

    EventResponse completeDraftAndSubmit(UUID eventId, UUID organizerId, CompleteDraftSetupRequest request);
}
