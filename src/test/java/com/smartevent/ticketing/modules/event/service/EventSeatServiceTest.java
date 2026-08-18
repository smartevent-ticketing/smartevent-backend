package com.smartevent.ticketing.modules.event.service;

import com.smartevent.ticketing.common.api.PageResponse;
import com.smartevent.ticketing.common.enums.AreaType;
import com.smartevent.ticketing.common.enums.EventStatus;
import com.smartevent.ticketing.common.enums.SeatStatus;
import com.smartevent.ticketing.common.error.ErrorCode;
import com.smartevent.ticketing.modules.event.dto.request.EventSeatRequest;
import com.smartevent.ticketing.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.ticketing.modules.event.dto.response.EventSeatResponse;
import com.smartevent.ticketing.modules.event.entity.Event;
import com.smartevent.ticketing.modules.event.entity.EventArea;
import com.smartevent.ticketing.modules.event.entity.EventSeat;
import com.smartevent.ticketing.modules.event.exception.EventException;
import com.smartevent.ticketing.modules.event.repository.EventAreaRepository;
import com.smartevent.ticketing.modules.event.repository.EventRepository;
import com.smartevent.ticketing.modules.event.repository.EventSeatRepository;
import com.smartevent.ticketing.modules.event.service.impl.EventSeatServiceImpl;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventSeatServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAreaRepository eventAreaRepository;

    @Mock
    private EventSeatRepository eventSeatRepository;

    @InjectMocks
    private EventSeatServiceImpl eventSeatService;

    @Test
    @DisplayName("Sinh ghế tự động (Batch Generator) thành công cho khu vực SEATED")
    void generateSeats_Success() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "Khán Đài VIP", AreaType.SEATED, 100, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        // Sinh từ Hàng A đến Hàng C, mỗi hàng 5 ghế -> Tổng: 3 * 5 = 15 ghế
        GenerateSeatsRequest request = new GenerateSeatsRequest("A", "C", 5);

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSeatRepository.countByEventAreaId(areaId)).thenReturn(0L);

        when(eventSeatRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<EventSeatResponse> responses = eventSeatService.generateSeats(areaId, organizerId, false, request);

        assertNotNull(responses);
        assertEquals(15, responses.size());
        assertEquals("A", responses.get(0).rowName());
        assertEquals("01", responses.get(0).seatNumber());
        assertEquals("A-01", responses.get(0).label());
        assertEquals("C-05", responses.get(14).label());

        verify(eventSeatRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Sinh ghế cho khu vực vé đứng (STANDING) - Ném lỗi BUSINESS_RULE_VIOLATION")
    void generateSeats_AreaNotSeated_ThrowsBusinessRuleViolation() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea standingArea = new EventArea(eventId, "Fanzone", AreaType.STANDING, 1000, 0, null);
        standingArea.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        GenerateSeatsRequest request = new GenerateSeatsRequest("A", "C", 10);

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(standingArea));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventSeatService.generateSeats(areaId, organizerId, false, request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Chỉ có thể sinh ghế cho khu vực vé ngồi"));
    }

    @Test
    @DisplayName("Sinh ghế với hàng bắt đầu sau hàng kết thúc (VD: từ Z đến A) - Ném lỗi VALIDATION_ERROR")
    void generateSeats_InvalidRowOrder_ThrowsValidationError() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "Khán Đài VIP", AreaType.SEATED, 100, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        GenerateSeatsRequest request = new GenerateSeatsRequest("Z", "A", 10); // Sai thứ tự

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventSeatService.generateSeats(areaId, organizerId, false, request)
        );

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    @DisplayName("Sinh số lượng ghế vượt quá sức chứa khán đài - Ném lỗi BUSINESS_RULE_VIOLATION")
    void generateSeats_ExceedsAreaCapacity_ThrowsBusinessRuleViolation() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "VIP", AreaType.SEATED, 50, 1, null); // Sức chứa 50
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        // Sinh từ A đến F (6 hàng * 10 ghế = 60 ghế > 50)
        GenerateSeatsRequest request = new GenerateSeatsRequest("A", "F", 10);

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSeatRepository.countByEventAreaId(areaId)).thenReturn(0L);

        EventException exception = assertThrows(EventException.class, () ->
                eventSeatService.generateSeats(areaId, organizerId, false, request)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("vượt quá sức chứa tối đa"));
    }

    @Test
    @DisplayName("Tạo 1 ghế đơn lẻ thành công")
    void createSingleSeat_Success() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "Khán Đài VIP", AreaType.SEATED, 100, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        EventSeatRequest request = new EventSeatRequest("VIP", "01", "VIP-01");

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSeatRepository.existsByEventAreaIdAndRowNameAndSeatNumber(areaId, "VIP", "01")).thenReturn(false);
        when(eventSeatRepository.countByEventAreaId(areaId)).thenReturn(10L);

        when(eventSeatRepository.save(any(EventSeat.class))).thenAnswer(invocation -> {
            EventSeat s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        EventSeatResponse response = eventSeatService.createSingleSeat(areaId, organizerId, false, request);

        assertNotNull(response);
        assertEquals("VIP", response.rowName());
        assertEquals("01", response.seatNumber());
        assertEquals("VIP-01", response.label());
        assertEquals(SeatStatus.AVAILABLE, response.status());
    }

    @Test
    @DisplayName("Xóa ghế đang được giữ (HELD) hoặc đã bán (SOLD) - Ném lỗi BUSINESS_RULE_VIOLATION")
    void deleteSeat_SoldOrHeld_ThrowsBusinessRuleViolation() {
        UUID seatId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventSeat seat = new EventSeat(areaId, "A", "01", "A-01", SeatStatus.SOLD, null); // Đã bán
        seat.setId(seatId);

        EventArea area = new EventArea(eventId, "VIP", AreaType.SEATED, 100, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        EventException exception = assertThrows(EventException.class, () ->
                eventSeatService.deleteSeat(seatId, organizerId, false)
        );

        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Không thể xóa ghế đang được giữ hoặc đã bán"));
        verify(eventSeatRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa toàn bộ ghế trong khán đài thành công khi không có ghế nào bị giữ hoặc đã bán")
    void deleteAllSeatsInArea_Success() {
        UUID areaId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID organizerId = UUID.randomUUID();

        EventArea area = new EventArea(eventId, "VIP", AreaType.SEATED, 100, 1, null);
        area.setId(areaId);

        Event event = new Event();
        event.setId(eventId);
        event.setOrganizerId(organizerId);
        event.setStatus(EventStatus.DRAFT);

        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(area));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(eventSeatRepository.countByEventAreaIdAndStatus(areaId, SeatStatus.SOLD)).thenReturn(0L);
        when(eventSeatRepository.countByEventAreaIdAndStatus(areaId, SeatStatus.HELD)).thenReturn(0L);

        eventSeatService.deleteAllSeatsInArea(areaId, organizerId, false);

        verify(eventSeatRepository, times(1)).deleteByEventAreaId(areaId);
    }
}
