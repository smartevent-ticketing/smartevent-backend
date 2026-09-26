package com.smartevent.modules.notification.consumer;

import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketEmailDeliveryService {
    private final JdbcTemplate jdbc;
    private final EmailService emailService;

    /** A committed event ID is skipped on redelivery. An ambiguous SMTP result can still duplicate mail. */
    @Transactional
    public void deliver(TicketIssuedEvent event) {
        int claimed = jdbc.update("INSERT INTO notification_inbox(event_id, event_type, processed_at) "
                        + "VALUES (?, 'TICKET_ISSUED', ?) ON CONFLICT (event_id) DO NOTHING",
                event.eventId(), Timestamp.from(Instant.now()));
        if (claimed == 0) return;
        emailService.sendTicketEmail(event.ownerEmail(), event.ticketCode(), event.eventName(),
                event.seatCode(), event.qrCodeBase64());
    }
}
