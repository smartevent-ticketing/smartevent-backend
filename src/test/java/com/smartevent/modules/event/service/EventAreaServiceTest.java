package com.smartevent.modules.event.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.request.EventAreaRequest;
import com.smartevent.modules.event.dto.response.EventAreaResponse;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.Venue;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.event.repository.VenueRepository;
import com.smartevent.modules.event.service.impl.EventAreaServiceImpl;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventAreaServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private VenueRepository venueRepository;

    @Mock
    private EventAreaRepository eventAreaRepository;

    @Mock
    private EventSeatRepository eventSeatRepository;

    private EventAreaServiceImpl eventAreaService;

    @BeforeEach
    void composeServices() {
        eventAreaService = new EventAreaServiceImpl(
                new EventAccessPolicy(),
                eventRepository,
                venueRepository,
                eventAreaRepository,
                eventSeatRepository, mock(com.smartevent.modules.event.service.EventConfigurationPolicy.class));
    }

    @Test
    @DisplayName("Tạo khu vực vé thành công khi sức chứa hợp lệ")
    void createArea_Success() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setVenueId(venueId);
        event.setStatus(EventStatus.DRAFT);

        Venue venue = new Venue("Mỹ Đình", "Hà Nội", "Hà Nội", null, null, 40000);

        EventAreaRequest request = new EventAreaRequest(
                "Khán Đài VIP", AreaType.SEATED, 500, 1, "Mô tả khán đài VIP"
        );

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventAreaRepository.existsByEventIdAndName(eventId, "Khán Đài VIP")).thenReturn(false);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(eventAreaRepository.sumCapacityByEventIdExcluding(eventId, null)).thenReturn(1000);

        when(eventAreaRepository.save(any(EventArea.class))).thenAnswer(invocation -> {
            EventArea a = invocation.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        EventAreaResponse response = eventAreaService.createArea(eventId, organizerId, false, request);

        assertNotNull(response);
        assertEquals("Khán Đài VIP", response.name());
        assertEquals(AreaType.SEATED, response.areaType());
        assertEquals(500, response.capacity());
        assertEquals(0, response.totalSeatsConfigured());

        verify(eventAreaRepository, times(1)).save(any(EventArea.class));
    }

    @Test
    @DisplayName("Tạo khu vực khi người dùng không phải chủ sở hữu - Ném lỗi ACCESS_DENIED")
    void createArea_NotOwner_ThrowsAccessDenied() {
        UUID eventId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(ownerId);
        event.setStatus(EventStatus.DRAFT);

        EventAreaRequest request = new EventAreaRequest("Fanzone", AreaType.STANDING, 1000, 0, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventAreaService.createArea(eventId, hackerId, false, request)
        );

        assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
        verify(eventAreaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo khu vực khi sự kiện đã xuất bản (PUBLISHED) - Ném lỗi BUSINESS_RULE_VIOLATION")
    void createArea_EventAlreadyPublished_ThrowsBusinessRuleViolation() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.PUBLISHED);

        EventAreaRequest request = new EventAreaRequest("Fanzone", AreaType.STANDING, 1000, 0, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventAreaService.createArea(eventId, organizerId, false, request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
    }

    @Test
    @DisplayName("Tạo khu vực vượt quá sức chứa tối đa của địa điểm - Ném lỗi BUSINESS_RULE_VIOLATION")
    void createArea_ExceedsVenueCapacity_ThrowsBusinessRuleViolation() {
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setVenueId(venueId);
        event.setStatus(EventStatus.DRAFT);

        Venue venue = new Venue("Nhà hát Nhỏ", "Hà Nội", "Hà Nội", null, null, 1000);

        EventAreaRequest request = new EventAreaRequest("Khán Đài Lớn", AreaType.SEATED, 600, 1, null);

        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventAreaRepository.existsByEventIdAndName(eventId, "Khán Đài Lớn")).thenReturn(false);
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(eventAreaRepository.sumCapacityByEventIdExcluding(eventId, null)).thenReturn(500); // 500 + 600 = 1100 > 1000

        EventException exception = assertThrows(EventException.class, () ->
                eventAreaService.createArea(eventId, organizerId, false, request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("vượt quá sức chứa tối đa"));
    }

    @Test
    @DisplayName("Cập nhật khu vực với sức chứa nhỏ hơn số ghế đã tạo - Ném lỗi BUSINESS_RULE_VIOLATION")
    void updateArea_CapacityLessThanConfiguredSeats_ThrowsBusinessRuleViolation() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "Khán Đài A", AreaType.SEATED, 500, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        EventAreaRequest request = new EventAreaRequest("Khán Đài A", AreaType.SEATED, 200, 1, null); // Giảm xuống 200

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(eventSeatRepository.countByEventAreaId(areaId)).thenReturn(300L); // Nhưng đang có sẵn 300 ghế!

        EventException exception = assertThrows(EventException.class, () ->
                eventAreaService.updateArea(areaId, organizerId, false, request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("không được nhỏ hơn số ghế đã tạo"));
    }

    @Test
    @DisplayName("Xóa khu vực vé thành công")
    void deleteArea_Success() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "Fanzone", AreaType.STANDING, 1000, 0, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));

        eventAreaService.deleteArea(areaId, organizerId, false);

        verify(eventAreaRepository, times(1)).delete(area);
    }
}
