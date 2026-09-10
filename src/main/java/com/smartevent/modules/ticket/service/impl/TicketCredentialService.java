package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.util.TicketSecurityUtils;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketCredentialService {
    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository qrTokenRepository;

    /** Caller holds the ticket lock. Both accepted gate credentials change together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rotate(Ticket ticket) {
        qrTokenRepository.revokeActiveTokens(ticket.getId(), Instant.now());
        String code;
        do {
            code = "TCK-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (ticketRepository.existsByTicketCode(code));
        ticket.setTicketCode(code);
        ticketRepository.save(ticket);
        qrTokenRepository.save(new TicketQrToken(ticket.getId(),
                TicketSecurityUtils.generateSecureQrToken(ticket.getId(), ticket.getCurrentOwnerUserId())));
    }
}
