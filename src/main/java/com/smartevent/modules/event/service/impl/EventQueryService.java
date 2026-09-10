package com.smartevent.modules.event.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.response.CategoryResponse;
import com.smartevent.modules.event.dto.response.EventFileResponse;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.dto.response.VenueResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.entity.EventFile;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.CategoryRepository;
import com.smartevent.modules.event.repository.EventCategoryRepository;
import com.smartevent.modules.event.repository.EventFileRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventQueryService {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final VenueRepository venueRepository;
    private final EventFileRepository eventFileRepository;
    private final EventCategoryRepository eventCategoryRepository;

    @Transactional(readOnly = true)
    public EventResponse getEventBySlug(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventException(ErrorCode.EVENT_NOT_PUBLISHED, "Sự kiện chưa được công bố công khai");
        }
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy sự kiện"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventException(ErrorCode.EVENT_NOT_PUBLISHED, "Sự kiện chưa được công bố công khai");
        }
        return toResponse(event);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getPublishedEvents(Pageable pageable) {
        Page<Event> eventPage = eventRepository.findByStatus(EventStatus.PUBLISHED, pageable);

        return PageResponse.from(eventPage, toResponses(eventPage.getContent()));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventsByOrganizer(UUID organizerId, Pageable pageable) {
        Page<Event> eventPage = eventRepository.findByOrganizerId(organizerId, pageable);
        return PageResponse.from(eventPage, toResponses(eventPage.getContent()));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventsForAdministration(EventStatus status, Pageable pageable) {
        Page<Event> page = status == null ? eventRepository.findAll(pageable) : eventRepository.findByStatus(status, pageable);
        return PageResponse.from(page, toResponses(page.getContent()));
    }

    @Transactional(readOnly = true)
    public EventResponse getEventForAdministration(UUID id) {
        return toResponse(eventRepository.findById(id)
                .orElseThrow(() -> new EventException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện")));
    }

    public EventResponse toResponse(Event event) {
        return toResponses(List.of(event)).get(0);
    }

    public List<EventResponse> toResponses(List<Event> events) {
        if (events.isEmpty()) return List.of();
        List<UUID> eventIds = events.stream().map(Event::getId).toList();
        List<UUID> venueIds = events.stream().map(Event::getVenueId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, VenueResponse> venues = venueIds.isEmpty() ? Map.of() : venueRepository.findAllById(venueIds).stream()
                .collect(Collectors.toMap(v -> v.getId(), VenueResponse::from));
        List<EventCategory> links = eventCategoryRepository.findByIdEventIdIn(eventIds);
        List<UUID> categoryIds = links.stream().map(link -> link.getId().getCategoryId()).distinct().toList();
        Map<UUID, CategoryResponse> categories = categoryIds.isEmpty() ? Map.of() : categoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(c -> c.getId(), CategoryResponse::from));
        Map<UUID, List<CategoryResponse>> eventCategories = new HashMap<>();
        for (EventCategory link : links) {
            CategoryResponse category = categories.get(link.getId().getCategoryId());
            if (category != null) eventCategories.computeIfAbsent(link.getId().getEventId(), id -> new ArrayList<>()).add(category);
        }
        Map<UUID, List<EventFileResponse>> files = eventFileRepository.findByEventIdInOrderBySortOrderAsc(eventIds).stream()
                .collect(Collectors.groupingBy(EventFile::getEventId, Collectors.mapping(EventFileResponse::from, Collectors.toList())));
        return events.stream().map(event -> EventResponse.of(event,
                event.getVenueId() == null ? null : venues.get(event.getVenueId()),
                eventCategories.getOrDefault(event.getId(), List.of()),
                files.getOrDefault(event.getId(), List.of()))).toList();
    }
}
