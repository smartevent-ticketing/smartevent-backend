package com.smartevent.modules.event.service;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.EventFileType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.UpdateEventRequest;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.entity.Category;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventCategory;
import com.smartevent.modules.event.entity.EventFile;
import com.smartevent.modules.event.entity.Venue;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.*;
import com.smartevent.modules.event.service.impl.EventServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private EventFileRepository eventFileRepository;

    @Mock
    private EventCategoryRepository eventCategoryRepository;

    @InjectMocks
    private EventServiceImpl eventService;

    @Test
    @DisplayName("Tạo sự kiện thành công - Lưu Event trạng thái DRAFT, lưu Categories và Files")
    void createEvent_Success() {
        UUID organizerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID bannerFileId = UUID.randomUUID();

        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        CreateEventRequest request = new CreateEventRequest(
                "Born Pink Hanoi", "Mô tả concert", venueId,
                start, end, "Hà Nội", List.of(categoryId), bannerFileId,
                List.of(), false, null, null, false, 50
        );

        Venue mockVenue = new Venue("Mỹ Đình", "Lê Đức Thọ", "Hà Nội", null, null, 40000);
        mockVenue.setId(venueId);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(mockVenue));
        when(eventRepository.hasVenueTimeConflict(venueId, start, end, null)).thenReturn(false);
        when(eventRepository.existsBySlug("born-pink-hanoi")).thenReturn(false);

        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event e = invocation.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        EventResponse response = eventService.createEvent(organizerId, request);

        assertNotNull(response);
        assertEquals("Born Pink Hanoi", response.name());
        assertEquals("born-pink-hanoi", response.slug());
        assertEquals(EventStatus.DRAFT, response.status());

        verify(eventRepository, times(1)).save(any(Event.class));
        verify(eventCategoryRepository, times(1)).saveAll(anyList());
        verify(eventFileRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Tạo sự kiện với thời gian kết thúc trước thời gian bắt đầu - Ném VALIDATION_ERROR")
    void createEvent_EndTimeBeforeStartTime_ThrowsValidationError() {
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant end = start.minus(1, ChronoUnit.HOURS); // Sai thời gian

        CreateEventRequest request = new CreateEventRequest(
                "Test Event", null, null, start, end, "Hà Nội",
                null, null, null, false, null, null, false, 50
        );

        EventException exception = assertThrows(EventException.class, () ->
                eventService.createEvent(UUID.randomUUID(), request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo sự kiện trùng lịch địa điểm - Ném BUSINESS_RULE_VIOLATION")
    void createEvent_VenueConflict_ThrowsBusinessRuleViolation() {
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        CreateEventRequest request = new CreateEventRequest(
                "Concert A", null, venueId, start, end, "Hà Nội",
                null, null, null, false, null, null, false, 50
        );

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(new Venue()));
        when(eventRepository.hasVenueTimeConflict(venueId, start, end, null)).thenReturn(true);

        EventException exception = assertThrows(EventException.class, () ->
                eventService.createEvent(UUID.randomUUID(), request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Cập nhật sự kiện bởi người lạ (không phải chủ sở hữu) - Ném ACCESS_DENIED")
    void updateEvent_NotOwner_ThrowsAccessDenied() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();

        Event existingEvent = new Event();
        existingEvent.setId(eventId);
        existingEvent.setOrganizerId(ownerId);
        existingEvent.setStatus(EventStatus.DRAFT);

        UpdateEventRequest request = new UpdateEventRequest(
                "Tên mới", null, null,
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS),
                "Hà Nội", null, null, null, false, null, null, false, 50
        );

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(existingEvent));

        EventException exception = assertThrows(EventException.class, () ->
                eventService.updateEvent(eventId, hackerId, false, request)
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Nộp duyệt sự kiện (submitForApproval) - Chuyển từ DRAFT sang PENDING_APPROVAL")
    void submitForApproval_Success() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setVenueId(venueId);
        event.setStatus(EventStatus.DRAFT);

        EventCategory ec = new EventCategory(eventId, UUID.randomUUID());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventCategoryRepository.findByIdEventId(eventId)).thenReturn(List.of(ec));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.submitForApproval(eventId, organizerId, false);

        assertNotNull(response);
        assertEquals(EventStatus.PENDING_APPROVAL, response.status());
    }

    @Test
    @DisplayName("Admin phê duyệt sự kiện (approveEvent) - Chuyển sang PUBLISHED và gán publishedAt")
    void approveEvent_Success() {
        UUID eventId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.HOURS);

        Event event = new Event();
        event.setId(eventId);
        event.setVenueId(venueId);
        event.setStartTime(start);
        event.setEndTime(end);
        event.setStatus(EventStatus.PENDING_APPROVAL);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.hasVenueTimeConflict(venueId, start, end, eventId)).thenReturn(false);
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.approveEvent(eventId);

        assertNotNull(response);
        assertEquals(EventStatus.PUBLISHED, response.status());
        assertNotNull(response.publishedAt());
    }

    @Test
    @DisplayName("Admin từ chối sự kiện (rejectEvent) - Chuyển về DRAFT")
    void rejectEvent_Success() {
        UUID eventId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.PENDING_APPROVAL);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.rejectEvent(eventId, "Ảnh banner không hợp lệ");

        assertNotNull(response);
        assertEquals(EventStatus.DRAFT, response.status());
    }

    @Test
    @DisplayName("Hủy sự kiện (cancelEvent) - Chuyển sang CANCELLED")
    void cancelEvent_Success() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.PUBLISHED);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.cancelEvent(eventId, organizerId, false, "Thời tiết xấu");

        assertNotNull(response);
        assertEquals(EventStatus.CANCELLED, response.status());
    }

    @Test
    @DisplayName("Xem sự kiện theo ID khi còn DRAFT -> Ném EVENT_NOT_PUBLISHED")
    void getEventById_Draft_ThrowsEventNotPublished() {
        UUID eventId = UUID.randomUUID();
        Event event = new Event();
        event.setId(eventId);
        event.setStatus(EventStatus.DRAFT);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventService.getEventById(eventId)
        );

        assertEquals(ErrorCode.EVENT_NOT_PUBLISHED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Xem sự kiện theo Slug khi còn DRAFT -> Ném EVENT_NOT_PUBLISHED")
    void getEventBySlug_Draft_ThrowsEventNotPublished() {
        String slug = "concert-draft-slug";
        Event event = new Event();
        event.setSlug(slug);
        event.setStatus(EventStatus.DRAFT);

        when(eventRepository.findBySlug(slug)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventService.getEventBySlug(slug)
        );

        assertEquals(ErrorCode.EVENT_NOT_PUBLISHED, exception.getErrorCode());
    }
}

