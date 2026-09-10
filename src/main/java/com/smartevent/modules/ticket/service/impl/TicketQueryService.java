package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.util.QrCodeUtils;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketQueryService {

    private final TicketQrTokenRepository qrTokenRepository;
    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository salePhaseRepository;

    public TicketResponse toResponse(Ticket ticket) {
        String eventName = eventRepository.findById(ticket.getEventId()).map(Event::getName).orElse("Unknown Event");
        String areaName = ticket.getEventAreaId() != null
                ? eventAreaRepository.findById(ticket.getEventAreaId()).map(EventArea::getName).orElse(null) : null;
        String seatCode = ticket.getEventSeatId() != null
                ? eventSeatRepository.findById(ticket.getEventSeatId()).map(EventSeat::getSeatNumber).orElse(null) : "Vé đứng tự do";
        String ticketTypeName = ticket.getTicketTypeId() != null
                ? ticketTypeRepository.findById(ticket.getTicketTypeId()).map(TicketType::getName).orElse("General") : "General";
        String phaseName = ticket.getSalePhaseId() != null
                ? salePhaseRepository.findById(ticket.getSalePhaseId()).map(TicketSalePhase::getName).orElse("Standard") : "Standard";

        // Lấy token QR đang Active để render ảnh QR
        String qrContent = qrTokenRepository.findFirstByTicketIdAndStatusOrderByIssuedAtDesc(ticket.getId(), "ACTIVE")
                .map(TicketQrToken::getTokenHash)
                .orElse(ticket.getTicketCode());

        String qrCodeBase64 = QrCodeUtils.generateQrCodeBase64(qrContent);

        return new TicketResponse(
                ticket.getId(),
                ticket.getOrderItemId(),
                ticket.getCurrentOwnerUserId(),
                ticket.getOriginalBuyerUserId(),
                ticket.getEventId(),
                eventName,
                ticket.getEventAreaId(),
                areaName,
                ticket.getEventSeatId(),
                seatCode,
                ticket.getTicketTypeId(),
                ticketTypeName,
                ticket.getSalePhaseId(),
                phaseName,
                ticket.getTicketCode(),
                ticket.getStatus(),
                qrCodeBase64,
                ticket.getIssuedAt(),
                ticket.getUsedAt()
        );
    }
}
