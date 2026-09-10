package com.smartevent.modules.ticket.service;

import com.smartevent.common.enums.CheckinResult;
import com.smartevent.common.enums.TicketStatus;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.ticket.dto.request.CheckinRequest;
import com.smartevent.modules.ticket.dto.response.CheckinResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketCheckin;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.repository.TicketCheckinRepository;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.service.impl.CheckinServiceImpl;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private TicketQrTokenRepository qrTokenRepository;
    @Mock private TicketCheckinRepository checkinRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private com.smartevent.modules.event.repository.EventRepository eventRepository;

    @InjectMocks
    private CheckinServiceImpl checkinService;

    private UUID eventId;
    private UUID staffId;
    private UUID ticketId;
    private Ticket validTicket;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        validTicket = new Ticket(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                eventId, null, null, UUID.randomUUID(), null, "TCK-20260822-ABC12345"
        );
        validTicket.setId(ticketId);
    }

    private com.smartevent.modules.event.entity.Event publishedEvent() {
        var event = new com.smartevent.modules.event.entity.Event();
        event.setStatus(com.smartevent.common.enums.EventStatus.PUBLISHED);
        return event;
    }

    @Test
    @DisplayName("Quét vé lần đầu thành công -> Kết quả SUCCESS và đổi trạng thái vé sang USED bằng Atomic Update")
    void checkin_Success() {
        CheckinRequest request = new CheckinRequest("TCK-20260822-ABC12345", eventId, "Cổng VIP 1");

        when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(ticketRepository.findIdByTicketCode("TCK-20260822-ABC12345")).thenReturn(Optional.of(ticketId));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(validTicket));
        when(ticketRepository.markTicketAsUsedAtomic(eq(ticketId), any())).thenReturn(1);
        when(checkinRepository.save(any(TicketCheckin.class))).thenAnswer(i -> {
            TicketCheckin tc = i.getArgument(0);
            tc.setId(UUID.randomUUID());
            return tc;
        });

        CheckinResponse response = checkinService.processCheckin(request, staffId);

        assertNotNull(response);
        assertEquals(CheckinResult.SUCCESS, response.result());
        verify(ticketRepository, times(1)).markTicketAsUsedAtomic(eq(ticketId), any());
        verify(checkinRepository, times(1)).save(any(TicketCheckin.class));
    }

    @Test
    @DisplayName("Phát hiện và báo động vé quét trùng (DUPLICATE) khi vé đã sử dụng trước đó")
    void checkin_DuplicateScan() {
        validTicket.setStatus(TicketStatus.USED);
        validTicket.setUsedAt(Instant.now().minusSeconds(1800)); // Đã quét cách đây 30 phút

        CheckinRequest request = new CheckinRequest("TCK-20260822-ABC12345", eventId, "Cổng A2");

        when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(ticketRepository.findIdByTicketCode("TCK-20260822-ABC12345")).thenReturn(Optional.of(ticketId));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(validTicket));
        when(checkinRepository.save(any(TicketCheckin.class))).thenAnswer(i -> i.getArgument(0));

        CheckinResponse response = checkinService.processCheckin(request, staffId);

        assertNotNull(response);
        assertEquals(CheckinResult.DUPLICATE, response.result());
        assertTrue(response.message().contains("CẢNH BÁO: Vé này đã được quét sử dụng trước đó"));
        verify(ticketRepository, never()).markTicketAsUsedAtomic(any(), any());
    }

    @Test
    @DisplayName("Từ chối check-in khi quét nhầm vé của sự kiện khác (EVENT_MISMATCH)")
    void checkin_EventMismatch() {
        UUID otherEventId = UUID.randomUUID();
        CheckinRequest request = new CheckinRequest("TCK-20260822-ABC12345", otherEventId, "Cổng A1");

        when(eventRepository.findByIdForShare(otherEventId)).thenReturn(Optional.of(publishedEvent()));
        when(ticketRepository.findIdByTicketCode("TCK-20260822-ABC12345")).thenReturn(Optional.of(ticketId));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(validTicket));
        when(checkinRepository.save(any(TicketCheckin.class))).thenAnswer(i -> i.getArgument(0));

        CheckinResponse response = checkinService.processCheckin(request, staffId);

        assertNotNull(response);
        assertEquals(CheckinResult.INVALID, response.result());
        assertEquals("VÉ NÀY KHÔNG THUỘC SỰ KIỆN NÀY!", response.message());
    }

    @Test
    @DisplayName("Từ chối check-in khi quét phải mã QR Token đã bị thu hồi (REVOKED)")
    void checkin_RevokedQrToken() {
        String tokenHash = "TCK-QR.revoked-token-123";
        TicketQrToken revokedToken = new TicketQrToken(ticketId, tokenHash);
        revokedToken.setStatus("REVOKED");

        CheckinRequest request = new CheckinRequest(tokenHash, eventId, "Cổng A1");

        when(eventRepository.findByIdForShare(eventId)).thenReturn(Optional.of(publishedEvent()));
        when(qrTokenRepository.findTicketIdByTokenHash(tokenHash)).thenReturn(Optional.of(ticketId));
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(validTicket));
        when(qrTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revokedToken));

        CheckinResponse response = checkinService.processCheckin(request, staffId);

        assertNotNull(response);
        assertEquals(CheckinResult.INVALID, response.result());
        assertEquals("MÃ QR NÀY ĐÃ BỊ THU HỒI HOẶC KHÔNG HỢP LỆ!", response.message());
    }
}