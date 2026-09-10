package com.smartevent.modules.event.service;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.modules.event.entity.Category;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.entity.Venue;
import com.smartevent.modules.event.repository.CategoryRepository;
import com.smartevent.modules.event.repository.EventCategoryRepository;
import com.smartevent.modules.event.repository.EventFileRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.impl.EventQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventQueryServiceTest {
    @Mock EventRepository eventRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock VenueRepository venueRepository;
    @Mock EventFileRepository eventFileRepository;
    @Mock EventCategoryRepository eventCategoryRepository;
    @InjectMocks EventQueryService query;

    @Test
    void listingLoadsSharedMetadataInBatchesAndPreservesEventOrder() {
        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setName("Venue");
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Music");
        Event first = new Event();
        first.setId(UUID.randomUUID());
        first.setVenueId(venue.getId());
        Event second = new Event();
        second.setId(UUID.randomUUID());
        second.setVenueId(venue.getId());
        var page = PageRequest.of(0, 10);
        var ids = List.of(first.getId(), second.getId());
        when(eventRepository.findByStatus(EventStatus.PUBLISHED, page)).thenReturn(new PageImpl<>(List.of(first, second)));
        when(venueRepository.findAllById(List.of(venue.getId()))).thenReturn(List.of(venue));
        when(eventCategoryRepository.findByIdEventIdIn(ids)).thenReturn(List.of(
                new EventCategory(first.getId(), category.getId()), new EventCategory(second.getId(), category.getId())));
        when(categoryRepository.findAllById(List.of(category.getId()))).thenReturn(List.of(category));
        when(eventFileRepository.findByEventIdInOrderBySortOrderAsc(ids)).thenReturn(List.of());

        var response = query.getPublishedEvents(page);
        assertEquals(ids, response.content().stream().map(event -> event.id()).toList());
        assertEquals("Venue", response.content().get(1).venue().name());
        assertEquals("Music", response.content().get(0).categories().get(0).name());
        verify(venueRepository, times(1)).findAllById(any());
        verify(venueRepository, never()).findById(any());
        verify(eventCategoryRepository, never()).findByIdEventId(any());
        verify(eventFileRepository, never()).findByEventIdOrderBySortOrderAsc(any());
    }

    @Test
    void emptyResultDoesNotQueryMetadata() {
        assertEquals(List.of(), query.toResponses(List.of()));
        verifyNoInteractions(venueRepository, categoryRepository, eventCategoryRepository, eventFileRepository);
    }
}
