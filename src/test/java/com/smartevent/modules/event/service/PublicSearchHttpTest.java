package com.smartevent.modules.event.service;

import com.smartevent.modules.event.controller.EventController;
import com.smartevent.modules.event.dto.request.PublicSearchFilter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicSearchHttpTest {
    @Test void utcAndEncodedOffsetBindToTheSameInstant() throws Exception {
        var service = mock(EventService.class);
        var api = MockMvcBuilders.standaloneSetup(new EventController(service))
            .setControllerAdvice(new com.smartevent.common.error.GlobalExceptionHandler())
            .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver()).build();
        api.perform(get("/api/v1/events").param("startFrom", "2026-10-31T17:00:00Z"))
            .andExpect(status().isOk());
        // param supplies the decoded value, as Spring sees an HTTP %2B offset.
        api.perform(get("/api/v1/events").param("startFrom", "2026-11-01T00:00:00+07:00"))
            .andExpect(status().isOk());
        api.perform(get(java.net.URI.create("/api/v1/events?startFrom=2026-11-01T00%3A00%3A00%2B07%3A00")))
            .andExpect(status().isOk());
        var filters = ArgumentCaptor.forClass(PublicSearchFilter.class);
        verify(service, times(3)).searchPublishedEvents(any(Pageable.class), isNull(), isNull(), isNull(), filters.capture());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), filters.getAllValues().get(0).startFrom());
        assertEquals(filters.getAllValues().get(0), filters.getAllValues().get(1));
        assertEquals(filters.getAllValues().get(0), filters.getAllValues().get(2));
    }

    @Test void missingTimezoneAndInvalidDateAreBadRequests() throws Exception {
        var service = mock(EventService.class);
        var api = MockMvcBuilders.standaloneSetup(new EventController(service))
            .setControllerAdvice(new com.smartevent.common.error.GlobalExceptionHandler())
            .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver()).build();
        for (var date : List.of("2026-11-01T00:00:00", "invalid")) {
            api.perform(get("/api/v1/events").param("startFrom", date)).andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }
}
