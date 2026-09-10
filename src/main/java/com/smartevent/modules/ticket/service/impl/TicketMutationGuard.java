package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketMutationGuard {
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Ticket lockUsableEventTicket(UUID ticketId) {
        UUID eventId = ticketRepository.findEventIdById(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé"));
        var event = eventRepository.findByIdForShare(eventId)
                .orElseThrow(() -> new TicketException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new TicketException(ErrorCode.TICKET_INVALID_STATUS, "Sự kiện không còn cho phép sử dụng hoặc chuyển nhượng vé");
        }
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé"));
    }
}
