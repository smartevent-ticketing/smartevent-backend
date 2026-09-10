package com.smartevent.modules.ticket.service;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.outbox.service.OutboxService;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.service.impl.TicketIssuanceService;
import com.smartevent.modules.ticket.service.impl.TicketQrService;
import com.smartevent.modules.ticket.service.impl.TicketQueryService;
import com.smartevent.modules.ticket.service.impl.TicketServiceImpl;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.math.BigDecimal;
import java.util.List;
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
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketQrTokenRepository qrTokenRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventAreaRepository eventAreaRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketSalePhaseRepository salePhaseRepository;
    @Mock private UserRepository userRepository;
    @Mock private OutboxService outboxService;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void composeServices() {
        var query = new TicketQueryService(qrTokenRepository, eventRepository, eventAreaRepository,
                eventSeatRepository, ticketTypeRepository, salePhaseRepository);
        var issuance = new TicketIssuanceService(query, ticketRepository, qrTokenRepository, orderRepository,
                orderItemRepository, eventRepository, eventSeatRepository, ticketTypeRepository, userRepository, outboxService);
        var qr = new TicketQrService(new com.smartevent.modules.ticket.service.impl.TicketMutationGuard(ticketRepository, eventRepository), new com.smartevent.modules.ticket.service.impl.TicketCredentialService(ticketRepository, qrTokenRepository), query);
        ticketService = new TicketServiceImpl(query, issuance, qr, ticketRepository, eventRepository);
    }

    private UUID userId;
    private UUID orderId;
    private UUID eventId;
    private UUID ticketTypeId;
    private Order order;
    private OrderItem item1;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();

        order = new Order();
        order.setId(orderId);
        order.setUserId(userId);

        item1 = new OrderItem(orderId, ticketTypeId, UUID.randomUUID(), null, 2, BigDecimal.valueOf(500000), BigDecimal.valueOf(1000000));
        item1.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Phát hành vé thành công khi đơn hàng đã thanh toán (Order có quantity = 2 -> Sinh 2 vé đơn lẻ)")
    void issueTickets_Success() {
        TicketType ticketType = new TicketType();
        ticketType.setId(ticketTypeId);
        ticketType.setEventId(eventId);
        ticketType.setName("VIP Diamond");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item1));
        when(ticketTypeRepository.findById(ticketTypeId)).thenReturn(Optional.of(ticketType));
        when(ticketRepository.existsByTicketCode(any())).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(i -> {
            Ticket t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        List<TicketResponse> responses = ticketService.issueTicketsForOrder(orderId);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        verify(ticketRepository, times(2)).save(any(Ticket.class));
        verify(qrTokenRepository, times(2)).save(any(TicketQrToken.class));
    }

    @Test
    @DisplayName("Lấy chi tiết vé thành công kèm chuỗi ảnh QR Code Base64 (data:image/png;base64,...)")
    void getTicketById_Success() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(UUID.randomUUID(), userId, userId, eventId, null, null, ticketTypeId, null, "TCK-20260822-12345678");
        ticket.setId(ticketId);

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));
        when(qrTokenRepository.findFirstByTicketIdAndStatusOrderByIssuedAtDesc(eq(ticketId), eq("ACTIVE")))
                .thenReturn(Optional.of(new TicketQrToken(ticketId, "TCK-QR.test-hash")));

        TicketResponse response = ticketService.getTicketById(ticketId, userId, false);

        assertNotNull(response);
        assertEquals("TCK-20260822-12345678", response.ticketCode());
        assertNotNull(response.qrCodeBase64());
        assertTrue(response.qrCodeBase64().startsWith("data:image/png;base64,"));
    }

    @Test
    @DisplayName("Chặn người dùng lạ xem vé của người khác (Ném lỗi ACCESS_DENIED)")
    void getTicketById_AccessDenied() {
        UUID ticketId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Ticket ticket = new Ticket(UUID.randomUUID(), userId, userId, eventId, null, null, ticketTypeId, null, "TCK-20260822-12345678");

        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketService.getTicketById(ticketId, otherUserId, false));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Làm mới mã QR Token bảo mật thành công (Thu hồi token cũ và sinh token mới)")
    void refreshTicketQr_Success() {
        UUID ticketId = UUID.randomUUID();
        Ticket ticket = new Ticket(UUID.randomUUID(), userId, userId, eventId, null, null, ticketTypeId, null, "TCK-20260822-12345678");
        ticket.setId(ticketId);

        TicketQrToken oldToken = new TicketQrToken(ticketId, "OLD-HASH");

        when(ticketRepository.findEventIdById(ticketId)).thenReturn(Optional.of(eventId));
        var event = new com.smartevent.modules.event.entity.Event();
        event.setStatus(com.smartevent.common.enums.EventStatus.PUBLISHED);
        when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(event));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));

        TicketResponse response = ticketService.refreshTicketQr(ticketId, userId);

        assertNotNull(response);
        assertNotEquals("TCK-20260822-12345678", ticket.getTicketCode());
        verify(qrTokenRepository).revokeActiveTokens(eq(ticketId), any());
        verify(qrTokenRepository, times(1)).save(argThat(TicketQrToken::isActive));
    }
}
