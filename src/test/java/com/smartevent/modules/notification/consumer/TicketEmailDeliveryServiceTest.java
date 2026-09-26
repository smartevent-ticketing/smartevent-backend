package com.smartevent.modules.notification.consumer;

import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import java.util.UUID;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketEmailDeliveryServiceTest {
    @Test
    void duplicateEventDoesNotSendAgain() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmailService email = mock(EmailService.class);
        TicketIssuedEvent event = new TicketIssuedEvent(UUID.randomUUID(), "TCK-1", UUID.randomUUID(),
                "Concert", UUID.randomUUID(), "buyer@example.test", "A1", "VIP", "data:image/png;base64,test");
        when(jdbc.update(anyString(), any(UUID.class), any(Timestamp.class))).thenReturn(0);

        new TicketEmailDeliveryService(jdbc, email).deliver(event);

        verifyNoInteractions(email);
    }

    @Test
    void firstEventSendsTicketEmail() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EmailService email = mock(EmailService.class);
        TicketIssuedEvent event = new TicketIssuedEvent(UUID.randomUUID(), "TCK-1", UUID.randomUUID(),
                "Concert", UUID.randomUUID(), "buyer@example.test", "A1", "VIP", "data:image/png;base64,test");
        when(jdbc.update(anyString(), any(UUID.class), any(Timestamp.class))).thenReturn(1);

        new TicketEmailDeliveryService(jdbc, email).deliver(event);

        verify(email).sendTicketEmail("buyer@example.test", "TCK-1", "Concert", "A1", "data:image/png;base64,test");
    }
}
