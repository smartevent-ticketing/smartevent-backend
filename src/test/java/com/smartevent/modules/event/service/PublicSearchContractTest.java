package com.smartevent.modules.event.service;
import java.time.*;
import java.math.BigDecimal;
import com.smartevent.modules.event.dto.request.PublicSearchFilter;
import com.smartevent.modules.event.repository.*;
import com.smartevent.modules.event.service.impl.EventQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicSearchContractTest {
    @Test void unfilteredSearchAlsoBatchesPriceEvidenceWithTheSameClock() {
        var events = mock(EventRepository.class);
        var now = Instant.parse("2026-10-30T00:00:00Z");
        var stable = PageRequest.of(0, 10, Sort.by("startTime", "id"));
        var event = new com.smartevent.modules.event.entity.Event();
        event.setId(java.util.UUID.randomUUID());
        var typeId = java.util.UUID.randomUUID();
        var phaseId = java.util.UUID.randomUUID();
        when(events.searchPublished(now, null, null, null, stable)).thenReturn(new PageImpl<>(java.util.List.of(event), stable, 1));
        when(events.findPriceEvidence(java.util.List.of(event.getId()), now, null, "LTE"))
            .thenReturn(java.util.List.of(new com.smartevent.modules.event.dto.response.PriceEvidence(event.getId(), typeId, phaseId, new BigDecimal("500000"))));
        var service = new EventQueryService(Clock.fixed(now, ZoneOffset.UTC), events,
            mock(CategoryRepository.class), mock(VenueRepository.class), mock(EventFileRepository.class), mock(EventCategoryRepository.class));
        var response = service.searchPublishedEvents(PageRequest.of(0, 10), null, null, null);
        assertEquals(new BigDecimal("500000"), response.content().get(0).price());
        assertEquals(now, response.content().get(0).priceObservedAt());
        assertEquals(phaseId, response.content().get(0).salePhaseId());
        verify(events, times(1)).findPriceEvidence(java.util.List.of(event.getId()), now, null, "LTE");
    }
    @Test void fixedClockReachesQueryAndStablePagination() {
        var events = mock(EventRepository.class);
        var now = Instant.parse("2026-10-30T00:00:00Z");
        var stable = PageRequest.of(0, 10, Sort.by("startTime", "id"));
        when(events.searchPublishedFiltered(now, null, null, null, null, null, null, "LTE", stable)).thenReturn(Page.empty(stable));
        var service = new EventQueryService(Clock.fixed(now, ZoneOffset.UTC), events,
            mock(CategoryRepository.class), mock(VenueRepository.class), mock(EventFileRepository.class), mock(EventCategoryRepository.class));
        var result = service.searchPublishedEvents(PageRequest.of(0, 10), null, null, null,
            new PublicSearchFilter(null, null, null, null));
        assertEquals(0, result.totalElements());
        verify(events).searchPublishedFiltered(now, null, null, null, null, null, null, "LTE", stable);
        verify(events, never()).findPriceEvidence(any(), any(), any(), any());
    }
    @Test void rejectsInvalidRangeComparisonAndPrice() {
        var now = Instant.parse("2026-10-30T00:00:00Z");
        assertThrows(RuntimeException.class, () -> new PublicSearchFilter(now, now, null, null));
        assertThrows(RuntimeException.class, () -> new PublicSearchFilter(null, null, BigDecimal.ONE, "EQ"));
        assertThrows(RuntimeException.class, () -> new PublicSearchFilter(null, null, new BigDecimal("-1"), "LT"));
    }
    @Test void sanitizesNonPositivePageSizeToAtLeastOne() {
        var events = mock(EventRepository.class);
        var now = Instant.parse("2026-10-30T00:00:00Z");
        var stable = PageRequest.of(0, 1, Sort.by("startTime", "id"));
        when(events.searchPublished(now, null, null, null, stable)).thenReturn(Page.empty(stable));
        var service = new EventQueryService(Clock.fixed(now, ZoneOffset.UTC), events,
            mock(CategoryRepository.class), mock(VenueRepository.class), mock(EventFileRepository.class), mock(EventCategoryRepository.class));
        Pageable invalidPageable = mock(Pageable.class);
        when(invalidPageable.getPageNumber()).thenReturn(-1);
        when(invalidPageable.getPageSize()).thenReturn(0);
        var response = service.searchPublishedEvents(invalidPageable, null, null, null);
        assertNotNull(response);
        verify(events).searchPublished(now, null, null, null, stable);
    }
}
