package com.smartevent.modules.event.service;

import com.smartevent.common.enums.AreaType;
import com.smartevent.modules.event.dto.request.CreateEventRequest;
import com.smartevent.modules.event.dto.request.CreateEventSetupRequest;
import com.smartevent.modules.event.dto.request.GenerateSeatsRequest;
import com.smartevent.modules.event.dto.response.EventAreaResponse;
import com.smartevent.modules.event.dto.response.EventResponse;
import com.smartevent.modules.event.service.impl.EventSetupServiceImpl;
import com.smartevent.modules.ticketing.dto.response.TicketTypeResponse;
import com.smartevent.modules.ticketing.service.TicketSalePhaseService;
import com.smartevent.modules.ticketing.service.TicketTypeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventSetupServiceTest {
    @Mock EventService events;
    @Mock EventAreaService areas;
    @Mock EventSeatService seats;
    @Mock TicketTypeService types;
    @Mock TicketSalePhaseService phases;
    EventSetupService service;
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID areaId = UUID.randomUUID();
    UUID typeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EventSetupServiceImpl(events, areas, seats, types, phases);
    }

    private CreateEventSetupRequest request(AreaType areaType, int capacity) {
        return new CreateEventSetupRequest(new CreateEventRequest("Concert", null, UUID.randomUUID(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), "Hà Nội",
                List.of(UUID.randomUUID()), null, null, false, null, null, false, 50),
                List.of(new CreateEventSetupRequest.Tier("VIP", areaType, BigDecimal.TEN, capacity)));
    }

    private void stubCreation() {
        EventResponse event = mock(EventResponse.class);
        EventAreaResponse area = mock(EventAreaResponse.class);
        TicketTypeResponse type = mock(TicketTypeResponse.class);
        when(event.id()).thenReturn(eventId);
        when(area.id()).thenReturn(areaId);
        when(type.id()).thenReturn(typeId);
        when(events.createEvent(eq(userId), any())).thenReturn(event);
        when(areas.createArea(eq(eventId), eq(userId), eq(false), any())).thenReturn(area);
        when(types.createTicketType(eq(eventId), eq(userId), eq(false), any())).thenReturn(type);
    }

    @Test
    void generatesExactlyTheConfiguredSeatCapacityBeforeSubmitting() {
        stubCreation();
        service.createAndSubmit(userId, request(AreaType.SEATED, 2000));
        var generated = ArgumentCaptor.forClass(GenerateSeatsRequest.class);
        verify(seats, atLeastOnce()).generateSeats(eq(areaId), eq(userId), eq(false), generated.capture());
        int total = generated.getAllValues().stream().mapToInt(batch ->
                (batch.toRow().charAt(0) - batch.fromRow().charAt(0) + 1) * batch.seatsPerRow()).sum();
        assertEquals(2000, total);
        var order = inOrder(phases, events);
        order.verify(phases).createSalePhase(eq(typeId), eq(userId), eq(false), any());
        order.verify(events).submitForApproval(eventId, userId, false);
    }

    @Test
    void failedTicketSetupRollsBackOuterTransactionAndNeverSubmits() {
        stubCreation();
        when(phases.createSalePhase(any(), any(), eq(false), any())).thenThrow(new IllegalStateException("phase failed"));
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        var status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any())).thenReturn(status);
        var interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        EventSetupService transactional = (EventSetupService) factory.getProxy();

        assertThrows(IllegalStateException.class, () -> transactional.createAndSubmit(userId, request(AreaType.STANDING, 10)));
        verify(transactions).rollback(status);
        verify(transactions, never()).commit(any());
        verify(events, never()).submitForApproval(any(), any(), anyBoolean());
    }
}
