package com.smartevent.modules.reservation.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.common.enums.SeatStatus;
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
import com.smartevent.modules.reservation.service.impl.ReservationItemValidator;
import com.smartevent.modules.reservation.service.impl.ReservationQueryService;
import com.smartevent.modules.reservation.service.impl.ReservationResources;
import com.smartevent.modules.reservation.service.impl.ReservationServiceImpl;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import com.smartevent.modules.ticketing.service.InventoryService;
import com.smartevent.modules.ticketing.service.UserSalePhaseCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.*;

/**
 * Adversarial Challenger Test Suite for Milestone 1 R1 anti-scalping limits:
 * - Boundary conditions of (purchased + held + newQuantity > maxTicketsPerUser)
 * - Cross-tier and cross-phase rejection across the event
 * - Normal reservation success within limits
 * - Null/unconfigured maxTicketsPerUser semantics
 * - Idempotency and error message precision
 */
@ExtendWith(MockitoExtension.class)
public class AntiScalpingChallengerTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationItemRepository reservationItemRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventAreaRepository eventAreaRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketSalePhaseRepository ticketSalePhaseRepository;
    @Mock private InventoryService inventoryService;
    @Mock private UserSalePhaseCounterService userSalePhaseCounterService;

    private ReservationServiceImpl reservationService;

    private UUID userId;
    private UUID eventId;
    private Event sampleEvent;

    // Tier 1 (VIP)
    private UUID area1Id;
    private EventArea area1;
    private UUID type1Id;
    private TicketType type1;
    private UUID phase1Id;
    private TicketSalePhase phase1;

    // Tier 2 (Standard)
    private UUID area2Id;
    private EventArea area2;
    private UUID type2Id;
    private TicketType type2;
    private UUID phase2Id;
    private TicketSalePhase phase2;

    private Reservation sampleReservation;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationServiceImpl(
                new ReservationItemValidator(
                        eventAreaRepository,
                        eventSeatRepository,
                        ticketTypeRepository,
                        ticketSalePhaseRepository),
                new ReservationResources(
                        reservationItemRepository,
                        eventSeatRepository,
                        inventoryService,
                        userSalePhaseCounterService),
                new ReservationQueryService(
                        reservationItemRepository,
                        eventRepository,
                        eventSeatRepository,
                        ticketTypeRepository,
                        ticketSalePhaseRepository),
                reservationRepository,
                reservationItemRepository,
                eventRepository,
                userSalePhaseCounterService);

        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        sampleEvent = new Event();
        sampleEvent.setId(eventId);
        sampleEvent.setName("Mega Festival 2026");
        sampleEvent.setStatus(EventStatus.PUBLISHED);

        Instant now = Instant.now();

        // Tier 1: VIP Standing
        area1Id = UUID.randomUUID();
        area1 = new EventArea(eventId, "VIP Standing", AreaType.STANDING, 500, 0, null);
        area1.setId(area1Id);

        type1Id = UUID.randomUUID();
        type1 = new TicketType(eventId, area1Id, "VIP", "VIP Access", "ACTIVE");
        type1.setId(type1Id);

        phase1Id = UUID.randomUUID();
        phase1 = new TicketSalePhase(
                type1Id, "VIP Phase 1", BigDecimal.valueOf(1000000), 500,
                now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
                10, 10, SalePhaseStatus.ACTIVE
        );
        phase1.setId(phase1Id);

        // Tier 2: Standard Standing
        area2Id = UUID.randomUUID();
        area2 = new EventArea(eventId, "Standard Standing", AreaType.STANDING, 1000, 1, null);
        area2.setId(area2Id);

        type2Id = UUID.randomUUID();
        type2 = new TicketType(eventId, area2Id, "Standard", "Standard Access", "ACTIVE");
        type2.setId(type2Id);

        phase2Id = UUID.randomUUID();
        phase2 = new TicketSalePhase(
                type2Id, "Standard Phase 1", BigDecimal.valueOf(500000), 1000,
                now.minus(1, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS),
                10, 10, SalePhaseStatus.ACTIVE
        );
        phase2.setId(phase2Id);

        sampleReservation = new Reservation(userId, eventId, now.plus(10, ChronoUnit.MINUTES), "challenger-key");
        sampleReservation.setId(UUID.randomUUID());
    }

    private void mockEntitiesForSuccess(TicketType t, TicketSalePhase p, EventArea a) {
        when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
        when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);
        when(ticketTypeRepository.findById(t.getId())).thenReturn(Optional.of(t));
        when(ticketSalePhaseRepository.findById(p.getId())).thenReturn(Optional.of(p));
        when(eventAreaRepository.findById(a.getId())).thenReturn(Optional.of(a));

        ReservationItem item = new ReservationItem(sampleReservation.getId(), t.getId(), p.getId(), null, 1, p.getPrice());
        when(reservationItemRepository.save(any(ReservationItem.class))).thenReturn(item);
    }

    @Nested
    @DisplayName("1. Boundary Condition Verification (purchased + held + newQuantity > maxTicketsPerUser)")
    class BoundaryConditions {

        @Test
        @DisplayName("Boundary: purchased=0, held=0, new=maxTicketsPerUser (exact limit) -> SUCCEEDS")
        void boundary_ZeroPurchased_ExactLimit_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(4);
            mockEntitiesForSuccess(type1, phase1, area1);
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(0);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 4)), "k1");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            assertEquals(ReservationStatus.PENDING, resp.status());
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }

        @Test
        @DisplayName("Boundary: purchased=0, held=0, new=maxTicketsPerUser + 1 (1 over limit) -> REJECTED")
        void boundary_ZeroPurchased_OneOverLimit_Rejects() {
            sampleEvent.setMaxTicketsPerUser(4);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(0);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 5)), "k2");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
            assertEquals("Bạn đã mua giới hạn số vé cho phép", ex.getMessage());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Boundary: purchased=3, held=0, new=1 when limit=4 (3+0+1=4 == max) -> SUCCEEDS")
        void boundary_Purchased3_New1_Limit4_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(4);
            mockEntitiesForSuccess(type1, phase1, area1);
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(3);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 1)), "k3");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }

        @Test
        @DisplayName("Boundary: purchased=3, held=0, new=2 when limit=4 (3+0+2=5 > 4) -> REJECTED")
        void boundary_Purchased3_New2_Limit4_Rejects() {
            sampleEvent.setMaxTicketsPerUser(4);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(3);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 2)), "k4");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
            assertEquals("Bạn đã mua giới hạn số vé cho phép", ex.getMessage());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Boundary: purchased=2, held=1, new=1 when limit=4 (2+1+1=4 == max) -> SUCCEEDS")
        void boundary_Purchased2_Held1_New1_Limit4_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(4);
            mockEntitiesForSuccess(type1, phase1, area1);
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(3);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 1)), "k5");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }

        @Test
        @DisplayName("Boundary: purchased=2, held=1, new=2 when limit=4 (2+1+2=5 > 4) -> REJECTED")
        void boundary_Purchased2_Held1_New2_Limit4_Rejects() {
            sampleEvent.setMaxTicketsPerUser(4);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(3);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 2)), "k6");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Boundary: user already reached maxTicketsPerUser (purchased=4), any new request (new=1) -> REJECTED")
        void boundary_AlreadyAtLimit_AnyNew_Rejects() {
            sampleEvent.setMaxTicketsPerUser(4);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(4);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 1)), "k7");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Unconfigured limit: maxTicketsPerUser=null -> No event limit applied, reservation SUCCEEDS")
        void unconfiguredLimit_NullMaxTickets_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(null);
            mockEntitiesForSuccess(type1, phase1, area1);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 10)), "k8");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            verify(userSalePhaseCounterService, never()).getOccupiedTicketsForEvent(any(), any());
        }
    }

    @Nested
    @DisplayName("2. Multi-tier and Multi-phase Anti-scalping Verification")
    class MultiTierAndMultiPhase {

        @Test
        @DisplayName("Cross-tier single request: Tier 1 (qty 2) + Tier 2 (qty 2) with limit=3 (4 > 3) -> REJECTED")
        void multiTierSingleRequest_ExceedsLimit_Rejects() {
            sampleEvent.setMaxTicketsPerUser(3);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(0);

            var req = new CreateReservationRequest(eventId, List.of(
                    new ReservationItemRequest(type1Id, phase1Id, null, 2),
                    new ReservationItemRequest(type2Id, phase2Id, null, 2)
            ), "multi-tier-reject");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
            assertEquals("Bạn đã mua giới hạn số vé cho phép", ex.getMessage());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cross-tier single request: Tier 1 (qty 1) + Tier 2 (qty 2) with limit=3 (3 == 3) -> SUCCEEDS")
        void multiTierSingleRequest_ExactLimit_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(3);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(0);
            when(reservationRepository.save(any(Reservation.class))).thenReturn(sampleReservation);

            when(ticketTypeRepository.findById(type1Id)).thenReturn(Optional.of(type1));
            when(ticketSalePhaseRepository.findById(phase1Id)).thenReturn(Optional.of(phase1));
            when(eventAreaRepository.findById(area1Id)).thenReturn(Optional.of(area1));

            when(ticketTypeRepository.findById(type2Id)).thenReturn(Optional.of(type2));
            when(ticketSalePhaseRepository.findById(phase2Id)).thenReturn(Optional.of(phase2));
            when(eventAreaRepository.findById(area2Id)).thenReturn(Optional.of(area2));

            ReservationItem item1 = new ReservationItem(sampleReservation.getId(), type1Id, phase1Id, null, 1, phase1.getPrice());
            ReservationItem item2 = new ReservationItem(sampleReservation.getId(), type2Id, phase2Id, null, 2, phase2.getPrice());
            when(reservationItemRepository.save(any(ReservationItem.class))).thenReturn(item1).thenReturn(item2);

            var req = new CreateReservationRequest(eventId, List.of(
                    new ReservationItemRequest(type1Id, phase1Id, null, 1),
                    new ReservationItemRequest(type2Id, phase2Id, null, 2)
            ), "multi-tier-pass");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            verify(reservationRepository, times(1)).save(any(Reservation.class));
            verify(inventoryService, times(1)).holdInventory(phase1Id, 1);
            verify(inventoryService, times(1)).holdInventory(phase2Id, 2);
        }

        @Test
        @DisplayName("Cross-tier accumulated: User already purchased 2 in Tier 1, attempts 2 in Tier 2 with limit=3 -> REJECTED")
        void crossTierAccumulated_ExceedsLimit_Rejects() {
            sampleEvent.setMaxTicketsPerUser(3);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            // user has 2 purchased from Tier 1 (returned across event by query)
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(2);

            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type2Id, phase2Id, null, 2)), "cross-tier-accum");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
            assertEquals("Bạn đã mua giới hạn số vé cho phép", ex.getMessage());
            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Cross-phase accumulated: User already purchased 2 in Phase 1, attempts 2 in Phase 2 with limit=3 -> REJECTED")
        void crossPhaseAccumulated_ExceedsLimit_Rejects() {
            sampleEvent.setMaxTicketsPerUser(3);
            when(reservationRepository.existsByUserIdAndEventIdAndStatus(userId, eventId, ReservationStatus.PENDING)).thenReturn(false);
            when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(sampleEvent));
            // 2 tickets purchased in Early Bird phase
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(2);

            // Now attempting to reserve in General Sale phase
            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 2)), "cross-phase-accum");

            ReservationException ex = assertThrows(ReservationException.class, () ->
                    reservationService.createReservation(userId, req));
            assertEquals(ErrorCode.EXCEEDED_TICKET_LIMIT, ex.getErrorCode());
            assertEquals("Bạn đã mua giới hạn số vé cho phép", ex.getMessage());
        }

        @Test
        @DisplayName("Cross-tier + Cross-phase combined: 1 in Tier1 Phase1 + 1 in Tier2 Phase1 already purchased, limit=3, requests 1 -> SUCCEEDS")
        void multiTierAndPhase_ExactLimit_Succeeds() {
            sampleEvent.setMaxTicketsPerUser(3);
            mockEntitiesForSuccess(type1, phase1, area1);
            when(userSalePhaseCounterService.getOccupiedTicketsForEvent(userId, eventId)).thenReturn(2);

            // 2 + 0 + 1 = 3 == limit
            var req = new CreateReservationRequest(eventId,
                    List.of(new ReservationItemRequest(type1Id, phase1Id, null, 1)), "exact-limit-pass");

            ReservationResponse resp = reservationService.createReservation(userId, req);
            assertNotNull(resp);
            verify(reservationRepository, times(1)).save(any(Reservation.class));
        }
    }
}
