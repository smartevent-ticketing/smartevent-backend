package com.smartevent.modules.ticketing.support;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.enums.SalePhaseStatus;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticketing.entity.InventoryCounter;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.InventoryCounterRepository;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalePhaseLifecycleWorkerTest {

    @Mock
    private TicketSalePhaseRepository ticketSalePhaseRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private InventoryCounterRepository inventoryCounterRepository;

    @InjectMocks
    private SalePhaseLifecycleWorker worker;

    private UUID eventId;
    private UUID ticketTypeId;
    private UUID phaseId;
    private Event sampleEvent;
    private TicketType sampleTicketType;
    private TicketSalePhase samplePhase;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();
        phaseId = UUID.randomUUID();

        sampleEvent = new Event();
        sampleEvent.setId(eventId);
        sampleEvent.setStatus(EventStatus.PUBLISHED);

        sampleTicketType = new TicketType(eventId, UUID.randomUUID(), "Vé VIP", "Mô tả", "ACTIVE");
        sampleTicketType.setId(ticketTypeId);

        Instant now = Instant.now();
        samplePhase = new TicketSalePhase(
                ticketTypeId, "Early Bird", BigDecimal.valueOf(500000), 100,
                now.minus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.DAYS),
                4, 2, SalePhaseStatus.SCHEDULED
        );
        samplePhase.setId(phaseId);
    }

    @Test
    @DisplayName("Kích hoạt SCHEDULED -> ACTIVE khi đến giờ và sự kiện đã PUBLISHED")
    void processSalePhaseLifecycle_ActivatesScheduledPhases_WhenEventIsPublished() {
        when(ticketSalePhaseRepository.findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(
                eq(SalePhaseStatus.SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(samplePhase));

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketSalePhaseRepository.findByStatusAndSaleEndAtBefore(eq(SalePhaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());
        when(ticketSalePhaseRepository.findByStatus(SalePhaseStatus.ACTIVE)).thenReturn(List.of());

        worker.processSalePhaseLifecycle();

        assertEquals(SalePhaseStatus.ACTIVE, samplePhase.getStatus());
        verify(ticketSalePhaseRepository, times(1)).save(samplePhase);
    }

    @Test
    @DisplayName("Không kích hoạt SCHEDULED -> ACTIVE nếu sự kiện chưa PUBLISHED (ví dụ DRAFT)")
    void processSalePhaseLifecycle_DoesNotActivate_WhenEventNotPublished() {
        sampleEvent.setStatus(EventStatus.DRAFT);

        when(ticketSalePhaseRepository.findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(
                eq(SalePhaseStatus.SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(samplePhase));

        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(sampleTicketType));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(sampleEvent));
        when(ticketSalePhaseRepository.findByStatusAndSaleEndAtBefore(eq(SalePhaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());
        when(ticketSalePhaseRepository.findByStatus(SalePhaseStatus.ACTIVE)).thenReturn(List.of());

        worker.processSalePhaseLifecycle();

        assertEquals(SalePhaseStatus.SCHEDULED, samplePhase.getStatus());
        verify(ticketSalePhaseRepository, never()).save(samplePhase);
    }

    @Test
    @DisplayName("Chuyển ACTIVE -> CLOSED khi quá hạn saleEndAt")
    void processSalePhaseLifecycle_ClosesExpiredPhases() {
        samplePhase.setStatus(SalePhaseStatus.ACTIVE);

        when(ticketSalePhaseRepository.findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(
                eq(SalePhaseStatus.SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        when(ticketSalePhaseRepository.findByStatusAndSaleEndAtBefore(eq(SalePhaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(samplePhase));
        when(ticketSalePhaseRepository.findByStatus(SalePhaseStatus.ACTIVE)).thenReturn(List.of());

        worker.processSalePhaseLifecycle();

        assertEquals(SalePhaseStatus.CLOSED, samplePhase.getStatus());
        verify(ticketSalePhaseRepository, times(1)).save(samplePhase);
    }

    @Test
    @DisplayName("Chuyển ACTIVE -> SOLD_OUT khi toàn bộ vé đã bán hết")
    void processSalePhaseLifecycle_MarksSoldOut_WhenAllTicketsSold() {
        samplePhase.setStatus(SalePhaseStatus.ACTIVE);

        when(ticketSalePhaseRepository.findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(
                eq(SalePhaseStatus.SCHEDULED), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        when(ticketSalePhaseRepository.findByStatusAndSaleEndAtBefore(eq(SalePhaseStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        when(ticketSalePhaseRepository.findByStatus(SalePhaseStatus.ACTIVE)).thenReturn(List.of(samplePhase));

        InventoryCounter counter = new InventoryCounter(
                eventId, UUID.randomUUID(), ticketTypeId, phaseId, 100
        );
        counter.setSoldQuantity(100);
        when(inventoryCounterRepository.findBySalePhaseId(phaseId)).thenReturn(Optional.of(counter));

        worker.processSalePhaseLifecycle();

        assertEquals(SalePhaseStatus.SOLD_OUT, samplePhase.getStatus());
        assertNotNull(samplePhase.getSoldOutAt());
        verify(ticketSalePhaseRepository, times(1)).save(samplePhase);
    }
}
