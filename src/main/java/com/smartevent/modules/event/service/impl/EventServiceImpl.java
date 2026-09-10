package com.smartevent.modules.event.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.service.EventService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventCommandService eventCommandService;
    private final EventLifecycleService eventLifecycleService;
    private final EventQueryService eventQueryService;

    @Override
    public EventResponse createEvent(UUID organizerId, CreateEventRequest request) {
        return eventCommandService.createEvent(organizerId, request);
    }

    @Override
    public EventResponse updateEvent(UUID eventId, UUID currentUserId, boolean isAdmin, UpdateEventRequest request){
        return eventCommandService.updateEvent(eventId, currentUserId, isAdmin, request);
    }

    @Override
    public EventResponse getEventBySlug(String slug) {
        return eventQueryService.getEventBySlug(slug);
    }

    @Override
    public EventResponse getEventById(UUID id) {
        return eventQueryService.getEventById(id);
    }

    @Override
    public PageResponse<EventResponse> getPublishedEvents(Pageable pageable) {
        return eventQueryService.getPublishedEvents(pageable);
    }

    @Override
    public PageResponse<EventResponse> getEventsByOrganizer(UUID organizerId, Pageable pageable) {
        return eventQueryService.getEventsByOrganizer(organizerId, pageable);
    }

    @Override
    public EventResponse submitForApproval(UUID eventId, UUID currentUserId, boolean isAdmin) {
        return eventLifecycleService.submitForApproval(eventId, currentUserId, isAdmin);
    }

    @Override
    public EventResponse approveEvent(UUID eventId) {
        return eventLifecycleService.approveEvent(eventId);
    }

    @Override
    public EventResponse rejectEvent(UUID eventId, String reason) {
        return eventLifecycleService.rejectEvent(eventId, reason);
    }

    @Override
    public EventResponse cancelEvent(UUID eventId, UUID currentUserId, boolean isAdmin, String reason) {
        return eventLifecycleService.cancelEvent(eventId, currentUserId, isAdmin, reason);
    }
}
