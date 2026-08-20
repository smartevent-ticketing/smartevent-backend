package com.smartevent.modules.reservation.service;

import com.smartevent.common.enums.*;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.reservation.dto.request.CreateReservationRequest;
import com.smartevent.modules.reservation.dto.request.ReservationItemRequest;
import com.smartevent.modules.reservation.dto.response.ReservationResponse;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.exception.ReservationException;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import com.smartevent.modules.reservation.service.impl.ReservationServiceImpl;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.InventoryService;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationItemRepository reservationItemRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAreaRepository eventAreaRepository;
    @Mock
    private EventSeatRepository eventSeatRepository;
    @Mock
    private TicketTypeRepository ticketTypeRepository;
    @Mock
    private TicketSalePhaseRepository ticketSalePhaseRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private UserSalePhaseCounterService userSalePhaseCounterService;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private UUID userId;
    private UUID eventId;
    private UUID areaId;
    private UUID ticketTypeId;
    private UUID salePhaseId;
    private UUID seatId;

    private Event sampleEvent;
    private EventArea standingArea;
    private EventArea seatedArea;
    private TicketType sampleTicketType;
    private TicketSalePhase samplePhase;
    private EventSeat sampleSeat;
    private Reservation sampleReservation;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        salePhaseId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        // Sample Event PUBLISHED
        sampleEvent = new Event();
        sampleEvent.setId(eventId);
        sampleEvent.setTitle("Concert Âm Nhạc");
        sampleEvent.setStatus(EventStatus.PUBLISHED);

        // Standing Area
        standingArea = new EventArea();
        standingArea.setId(areaId);
        standingArea.setEventId(eventId);
        standingArea.setName("Khu Đứng Fanzone");
        standingArea.setAreaType(AreaType.STANDING);
        standingArea.setCapacity(500);

        // Seated Area
        seatedArea = new EventArea();
        seatedArea.setId(areaId);
        seatedArea.setEventId(eventId);
        seatedArea.setName("Khán Đài VIP");
        seatedArea.setAreaType(AreaType.SEATED);
        seatedArea.setCapacity(200);

        // Ticket Type
        sampleTicketType = new TicketType(eventId, areaId, "Vé VIP", "Mô tả", true, null, null);
        sampleTicketType.setId(ticketTypeId);

        // Ticket Sale Phase
        Instant now = Instant.now();
        samplePhase = new TicketSalePhase(
                ticketTypeId, "Early Bird", BigDecimal.valueOf(500000), 200,
                now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
                4, 2, SalePhaseStatus.ACTIVE
        );
        samplePhase.setId(salePhaseId);

        // Event Seat AVAILABLE
        sampleSeat = new EventSeat(areaId, "A-12", 1, 12, SeatType.VIP, SeatStatus.AVAILABLE);
        sampleSeat.setId(seatId);

        // Reservation Sample
        sampleReservation = new Reservation(userId, eventId, now.plus(10, ChronoUnit.MINUTES), "idemp-key-1");
        sampleReservation.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Đặt vé đứng (STANDING) thành công: Trừ kho tổng, trừ quota user và tạo phiên giữ 10 phút")
    void createReservation_Standing_Success() {
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, null, 2);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), "key-1");

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(ticketSalePhaseRepository.findById(salePhaseId)).thenReturn(Optional.of(samplePhase));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(standingArea));

        ReservationItem savedItem = new ReservationItem(sampleReservation.getId(), ticketTypeId, salePhaseId, null, 2, BigDecimal.valueOf(500000));
        when(reservationItemRepository.save(any(ReservationItem.class))).thenReturn(savedItem);

        ReservationResponse response = reservationService.createReservation(userId, request);

        assertNotNull(response);
        assertEquals(eventId, response.eventId());
        assertEquals("Concert Âm Nhạc", response.eventName());
        assertEquals(ReservationStatus.PENDING, response.status());

        verify(inventoryService, times(1)).holdInventory(salePhaseId, 2);
        verify(userSalePhaseCounterService, times(1)).holdUserTickets(userId, salePhaseId, 2, 2);
    }

    @Test
    @DisplayName("Đặt vé ngồi (SEATED) thành công: Khóa ghế AVAILABLE -> HELD và tạo phiên giữ chỗ")
    void createReservation_Seated_Success() {
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, seatId, 1);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), "key-2");

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(ticketSalePhaseRepository.findById(salePhaseId)).thenReturn(Optional.of(samplePhase));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(seatedArea));
        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(sampleSeat));

        ReservationItem savedItem = new ReservationItem(sampleReservation.getId(), ticketTypeId, salePhaseId, seatId, 1, BigDecimal.valueOf(500000));
        when(reservationItemRepository.save(any(ReservationItem.class))).thenReturn(savedItem);

        ReservationResponse response = reservationService.createReservation(userId, request);

        assertNotNull(response);
        assertEquals(SeatStatus.HELD, sampleSeat.getStatus());
        verify(eventSeatRepository, times(1)).save(sampleSeat);
        verify(inventoryService, times(1)).holdInventory(salePhaseId, 1);
    }

    @Test
    @DisplayName("Chống bấm đúp: Trả về kết quả cũ khi trùng idempotencyKey")
    void createReservation_IdempotencyKey_ReturnsExisting() {
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(), "duplicate-key");

        when(reservationRepository.findByIdempotencyKey("duplicate-key")).thenReturn(Optional.of(sampleReservation));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationItemRepository.findByReservationId(sampleReservation.getId())).thenReturn(List.of());

        ReservationResponse response = reservationService.createReservation(userId, request);

        assertNotNull(response);
        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    @DisplayName("Chống găm vé: Ném RESERVATION_ALREADY_EXISTS nếu đã có phiên PENDING trên sự kiện")
    void createReservation_AlreadyHasPending_ThrowsException() {
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, null, 2);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), null);

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING))
                .thenReturn(true);

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.createReservation(userId, request)
        );
        assertEquals(ErrorCode.RESERVATION_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném EVENT_NOT_PUBLISHED khi sự kiện chưa mở bán (DRAFT)")
    void createReservation_EventNotPublished_ThrowsException() {
        sampleEvent.setStatus(EventStatus.DRAFT);
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, null, 2);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), null);

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.createReservation(userId, request)
        );
        assertEquals(ErrorCode.EVENT_NOT_PUBLISHED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném SALE_PHASE_NOT_ACTIVE khi đợt mở bán chưa kích hoạt")
    void createReservation_SalePhaseNotActive_ThrowsException() {
        samplePhase.setStatus(SalePhaseStatus.PAUSED);
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, null, 2);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), null);

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(ticketSalePhaseRepository.findById(salePhaseId)).thenReturn(Optional.of(samplePhase));

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.createReservation(userId, request)
        );
        assertEquals(ErrorCode.SALE_PHASE_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném MAX_PER_ORDER_EXCEEDED khi mua vượt quá số vé tối đa của một đơn hàng")
    void createReservation_MaxPerOrderExceeded_ThrowsException() {
        samplePhase.setMaxPerOrder(2);
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, null, 5); // 5 > 2
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), null);

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(ticketSalePhaseRepository.findById(salePhaseId)).thenReturn(Optional.of(samplePhase));

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.createReservation(userId, request)
        );
        assertEquals(ErrorCode.MAX_PER_ORDER_EXCEEDED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Ném SEAT_ALREADY_HELD khi ghế ngồi đã có người giữ (HELD)")
    void createReservation_Seated_SeatAlreadyHeld_ThrowsException() {
        sampleSeat.setStatus(SeatStatus.HELD); // Ghế đã bị giữ
        ReservationItemRequest itemReq = new ReservationItemRequest(ticketTypeId, salePhaseId, seatId, 1);
        CreateReservationRequest request = new CreateReservationRequest(eventId, List.of(itemReq), null);

        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(ticketSalePhaseRepository.findById(salePhaseId)).thenReturn(Optional.of(samplePhase));
        when(eventAreaRepository.findById(areaId)).thenReturn(Optional.of(seatedArea));
        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(sampleSeat));

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.createReservation(userId, request)
        );
        assertEquals(ErrorCode.SEAT_ALREADY_HELD, ex.getErrorCode());
    }

    @Test
    @DisplayName("Lấy chi tiết phiên giữ chỗ thành công")
    void getReservationById_Success() {
        UUID resId = sampleReservation.getId();
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(sampleReservation));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationItemRepository.findByReservationId(resId)).thenReturn(List.of());

        ReservationResponse response = reservationService.getReservationById(resId, userId, false);

        assertNotNull(response);
        assertEquals(resId, response.id());
    }

    @Test
    @DisplayName("Ném ACCESS_DENIED khi xem trộm phiên giữ chỗ của người khác")
    void getReservationById_AccessDenied_ThrowsException() {
        UUID resId = sampleReservation.getId();
        UUID strangerId = UUID.randomUUID();
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(sampleReservation));

        ReservationException ex = assertThrows(ReservationException.class, () ->
                reservationService.getReservationById(resId, strangerId, false)
        );
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khách hàng chủ động hủy phiên giữ chỗ: Mở lại ghế AVAILABLE và nhả kho tổng + quota")
    void cancelReservation_Success() {
        UUID resId = sampleReservation.getId();
        ReservationItem item = new ReservationItem(resId, ticketTypeId, salePhaseId, seatId, 1, BigDecimal.valueOf(500000));

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(sampleReservation));
        when(reservationItemRepository.findByReservationId(resId)).thenReturn(List.of(item));
        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(sampleSeat));

        sampleSeat.setStatus(SeatStatus.HELD);

        reservationService.cancelReservation(resId, userId, false);

        assertEquals(ReservationStatus.CANCELLED, sampleReservation.getStatus());
        assertEquals(SeatStatus.AVAILABLE, sampleSeat.getStatus());
        verify(inventoryService, times(1)).releaseHeldInventory(salePhaseId, 1);
        verify(userSalePhaseCounterService, times(1)).releaseUserHeldTickets(userId, salePhaseId, 1);
    }

    @Test
    @DisplayName("Xác nhận giữ chỗ thành công khi thanh toán: Chuyển ghế sang SOLD và xác nhận bán vé")
    void confirmReservation_Success() {
        UUID resId = sampleReservation.getId();
        ReservationItem item = new ReservationItem(resId, ticketTypeId, salePhaseId, seatId, 1, BigDecimal.valueOf(500000));

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(sampleReservation));
        when(reservationItemRepository.findByReservationId(resId)).thenReturn(List.of(item));
        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(sampleSeat));

        reservationService.confirmReservation(resId);

        assertEquals(ReservationStatus.CONFIRMED, sampleReservation.getStatus());
        assertEquals(SeatStatus.SOLD, sampleSeat.getStatus());
        verify(inventoryService, times(1)).confirmPurchase(salePhaseId, 1);
        verify(userSalePhaseCounterService, times(1)).confirmUserPurchase(userId, salePhaseId, 1);
    }

    @Test
    @DisplayName("Quét hết hạn 10 phút: Tự động chuyển EXPIRED, mở lại ghế và nhả kho vé")
    void expireReservation_Success() {
        UUID resId = sampleReservation.getId();
        ReservationItem item = new ReservationItem(resId, ticketTypeId, salePhaseId, seatId, 1, BigDecimal.valueOf(500000));

        when(reservationRepository.findById(resId)).thenReturn(Optional.of(sampleReservation));
        when(reservationItemRepository.findByReservationId(resId)).thenReturn(List.of(item));
        when(eventSeatRepository.findById(seatId)).thenReturn(Optional.of(sampleSeat));

        reservationService.expireReservation(resId);

        assertEquals(ReservationStatus.EXPIRED, sampleReservation.getStatus());
        assertEquals(SeatStatus.AVAILABLE, sampleSeat.getStatus());
        verify(inventoryService, times(1)).releaseHeldInventory(salePhaseId, 1);
        verify(userSalePhaseCounterService, times(1)).releaseUserHeldTickets(userId, salePhaseId, 1);
    }
}