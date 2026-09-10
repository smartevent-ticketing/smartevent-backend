package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.service.TicketService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketQueryService ticketQueryService;
    private final TicketIssuanceService ticketIssuanceService;
    private final TicketQrService ticketQrService;
    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    @Override
    public List<TicketResponse> issueTicketsForOrder(UUID orderId) {
        return ticketIssuanceService.issueTicketsForOrder(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId, UUID currentUserId, boolean isAdmin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé điện tử"));

        if (!isAdmin && !ticket.getCurrentOwnerUserId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem tấm vé này");
        }

        return ticketQueryService.toResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(UUID currentUserId) {
        List<Ticket> tickets = ticketRepository.findByCurrentOwnerUserIdOrderByCreatedAtDesc(currentUserId);
        return tickets.stream().map(ticketQueryService::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByEvent(UUID eventId, UUID currentUserId, boolean isAdmin) {
        if (!isAdmin) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new TicketException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
            if (!event.getOrganizerId().equals(currentUserId)) {
                throw new TicketException(ErrorCode.ACCESS_DENIED, "Bạn không phải ban tổ chức sự kiện này");
            }
        }

        List<Ticket> tickets = ticketRepository.findByEventIdOrderByCreatedAtDesc(eventId);
        return tickets.stream().map(ticketQueryService::toResponse).toList();
    }

    @Override
    public TicketResponse refreshTicketQr(UUID ticketId, UUID currentUserId) {
        return ticketQrService.refreshTicketQr(ticketId, currentUserId);
    }
}
