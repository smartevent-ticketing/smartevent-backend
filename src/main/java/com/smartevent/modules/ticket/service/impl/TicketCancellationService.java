package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.enums.TicketStatus;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketCancellationService {
    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository tokenRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelForEvent(UUID eventId) {
        for (UUID ticketId : ticketRepository.findIdsByEventId(eventId)) {
            var ticket = ticketRepository.findByIdForUpdate(ticketId).orElseThrow();
            if (ticket.getStatus() == TicketStatus.ISSUED) {
                ticket.setStatus(TicketStatus.CANCELLED);
                ticketRepository.save(ticket);
            }
            tokenRepository.revokeActiveTokens(ticketId, Instant.now());
        }
    }
}
