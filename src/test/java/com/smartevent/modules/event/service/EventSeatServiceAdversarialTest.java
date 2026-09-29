package com.smartevent.modules.event.service;

import com.smartevent.common.enums.SeatStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.dto.response.EventSeatResponse;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.exception.EventException;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.event.service.impl.EventSeatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Adversarial test harness for Milestone 1 R2 Seat Map API verification.
 * Target: EventSeatServiceImpl.getAvailableSeatsByArea
 */
@ExtendWith(MockitoExtension.class)
class EventSeatServiceAdversarialTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventAreaRepository eventAreaRepository;

    @Mock
    private EventSeatRepository eventSeatRepository;

    private EventSeatServiceImpl eventSeatService;

    @BeforeEach
    void setUp() {
        eventSeatService = new EventSeatServiceImpl(
                new EventAccessPolicy(),
                eventRepository,
                eventAreaRepository,
                eventSeatRepository
        );
    }

    @Test
    @DisplayName("Adversarial: getAvailableSeatsByArea trả về đầy đủ 4 trạng thái (AVAILABLE, HELD, SOLD, BLOCKED)")
    void getAvailableSeatsByArea_ReturnsAllFourStatuses_NoneDropped() {
        UUID areaId = UUID.randomUUID();
        when(eventAreaRepository.existsById(areaId)).thenReturn(true);

        Instant holdExpiry = Instant.now().plusSeconds(600);

        EventSeat seat1 = new EventSeat(areaId, "A", "01", "A-01", SeatStatus.AVAILABLE, null);
        seat1.setId(UUID.randomUUID());
        EventSeat seat2 = new EventSeat(areaId, "A", "02", "A-02", SeatStatus.HELD, holdExpiry);
        seat2.setId(UUID.randomUUID());
        EventSeat seat3 = new EventSeat(areaId, "B", "01", "B-01", SeatStatus.SOLD, null);
        seat3.setId(UUID.randomUUID());
        EventSeat seat4 = new EventSeat(areaId, "B", "02", "B-02", SeatStatus.BLOCKED, null);
        seat4.setId(UUID.randomUUID());

        when(eventSeatRepository.findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId))
                .thenReturn(List.of(seat1, seat2, seat3, seat4));

        List<EventSeatResponse> result = eventSeatService.getAvailableSeatsByArea(areaId);

        assertNotNull(result, "Response list must not be null");
        assertEquals(4, result.size(), "All 4 seats must be returned");

        // Verify none of the non-available seats are dropped
        assertEquals(SeatStatus.AVAILABLE, result.get(0).status());
        assertEquals(SeatStatus.HELD, result.get(1).status());
        assertEquals(SeatStatus.SOLD, result.get(2).status());
        assertEquals(SeatStatus.BLOCKED, result.get(3).status());

        // Verify holdExpiresAt is preserved
        assertNull(result.get(0).holdExpiresAt());
        assertEquals(holdExpiry, result.get(1).holdExpiresAt());
        assertNull(result.get(2).holdExpiresAt());
        assertNull(result.get(3).holdExpiresAt());
    }

    @Test
    @DisplayName("Adversarial: Kiểm tra toàn vẹn các trường thông tin trả về (Response Fields Integrity)")
    void getAvailableSeatsByArea_ResponseFieldsIntegrity() {
        UUID areaId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Instant holdExpiry = Instant.parse("2026-09-26T12:00:00Z");

        when(eventAreaRepository.existsById(areaId)).thenReturn(true);

        EventSeat seat = new EventSeat(areaId, "VIP-ROW", "99", "VIP-99", SeatStatus.HELD, holdExpiry);
        seat.setId(seatId);

        when(eventSeatRepository.findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId))
                .thenReturn(List.of(seat));

        List<EventSeatResponse> result = eventSeatService.getAvailableSeatsByArea(areaId);

        assertEquals(1, result.size());
        EventSeatResponse response = result.get(0);

        assertEquals(seatId, response.id());
        assertEquals(areaId, response.eventAreaId());
        assertEquals("VIP-ROW", response.rowName());
        assertEquals("99", response.seatNumber());
        assertEquals("VIP-99", response.label());
        assertEquals(SeatStatus.HELD, response.status());
        assertEquals(holdExpiry, response.holdExpiresAt());
    }

    @Test
    @DisplayName("Adversarial: Đảm bảo thứ tự sắp xếp RowName ASC, SeatNumber ASC được bảo toàn")
    void getAvailableSeatsByArea_PreservesAscendingOrder() {
        UUID areaId = UUID.randomUUID();
        when(eventAreaRepository.existsById(areaId)).thenReturn(true);

        EventSeat s1 = new EventSeat(areaId, "A", "01", "A-01", SeatStatus.AVAILABLE, null);
        s1.setId(UUID.randomUUID());
        EventSeat s2 = new EventSeat(areaId, "A", "02", "A-02", SeatStatus.HELD, null);
        s2.setId(UUID.randomUUID());
        EventSeat s3 = new EventSeat(areaId, "B", "01", "B-01", SeatStatus.SOLD, null);
        s3.setId(UUID.randomUUID());
        EventSeat s4 = new EventSeat(areaId, "B", "02", "B-02", SeatStatus.BLOCKED, null);
        s4.setId(UUID.randomUUID());

        when(eventSeatRepository.findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId))
                .thenReturn(List.of(s1, s2, s3, s4));

        List<EventSeatResponse> result = eventSeatService.getAvailableSeatsByArea(areaId);

        assertEquals(4, result.size());
        assertEquals("A-01", result.get(0).label());
        assertEquals("A-02", result.get(1).label());
        assertEquals("B-01", result.get(2).label());
        assertEquals("B-02", result.get(3).label());

        verify(eventSeatRepository, times(1)).findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId);
    }

    @Test
    @DisplayName("Adversarial: Phân khu không còn ghế trống nào (0 AVAILABLE) vẫn trả về toàn bộ ghế HELD/SOLD/BLOCKED")
    void getAvailableSeatsByArea_ZeroAvailableSeats_StillReturnsAllSeats() {
        UUID areaId = UUID.randomUUID();
        when(eventAreaRepository.existsById(areaId)).thenReturn(true);

        EventSeat s1 = new EventSeat(areaId, "A", "01", "A-01", SeatStatus.SOLD, null);
        s1.setId(UUID.randomUUID());
        EventSeat s2 = new EventSeat(areaId, "A", "02", "A-02", SeatStatus.HELD, null);
        s2.setId(UUID.randomUUID());
        EventSeat s3 = new EventSeat(areaId, "A", "03", "A-03", SeatStatus.BLOCKED, null);
        s3.setId(UUID.randomUUID());

        when(eventSeatRepository.findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId))
                .thenReturn(List.of(s1, s2, s3));

        List<EventSeatResponse> result = eventSeatService.getAvailableSeatsByArea(areaId);

        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(s -> s.status() == SeatStatus.AVAILABLE),
                "No seats should have AVAILABLE status");
        assertTrue(result.stream().anyMatch(s -> s.status() == SeatStatus.SOLD));
        assertTrue(result.stream().anyMatch(s -> s.status() == SeatStatus.HELD));
        assertTrue(result.stream().anyMatch(s -> s.status() == SeatStatus.BLOCKED));
    }

    @Test
    @DisplayName("Adversarial: Phân khu tồn tại nhưng không có ghế nào trả về danh sách rỗng (không null, không lỗi)")
    void getAvailableSeatsByArea_EmptyArea_ReturnsEmptyList() {
        UUID areaId = UUID.randomUUID();
        when(eventAreaRepository.existsById(areaId)).thenReturn(true);
        when(eventSeatRepository.findByEventAreaIdOrderByRowNameAscSeatNumberAsc(areaId))
                .thenReturn(List.of());

        List<EventSeatResponse> result = eventSeatService.getAvailableSeatsByArea(areaId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Adversarial: Phân khu không tồn tại ném RESOURCE_NOT_FOUND")
    void getAvailableSeatsByArea_AreaNotFound_ThrowsResourceNotFound() {
        UUID nonExistentAreaId = UUID.randomUUID();
        when(eventAreaRepository.existsById(nonExistentAreaId)).thenReturn(false);

        EventException ex = assertThrows(EventException.class, () ->
                eventSeatService.getAvailableSeatsByArea(nonExistentAreaId)
        );

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
        verify(eventSeatRepository, never()).findByEventAreaIdOrderByRowNameAscSeatNumberAsc(any());
    }
}
