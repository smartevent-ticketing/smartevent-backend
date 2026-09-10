package com.smartevent.modules.reservation.service.impl;

import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.reservation.dto.response.ReservationItemResponse;
import com.smartevent.modules.reservation.dto.response.ReservationResponse;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationQueryService {

    private final ReservationItemRepository reservationItemRepository;
    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;

    public ReservationResponse toResponse(Reservation reservation) {
        Event event = eventRepository.findById(reservation.getEventId()).orElse(null);
        String eventName = event != null ? event.getName() : "Unknown Event";

        List<ReservationItem> items = reservationItemRepository.findByReservationId(reservation.getId());
        List<ReservationItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (ReservationItem item : items) {
            TicketType ticketType = ticketTypeRepository.findById(item.getTicketTypeId()).orElse(null);
            String ticketTypeName = ticketType != null ? ticketType.getName() : "Unknown Ticket Type";

            TicketSalePhase phase = ticketSalePhaseRepository.findById(item.getSalePhaseId()).orElse(null);
            String phaseName = phase != null ? phase.getName() : "Unknown Phase";

            String seatCode = null;
            if (item.getEventSeatId() != null) {
                seatCode = eventSeatRepository.findById(item.getEventSeatId())
                        .map(EventSeat::getSeatNumber).orElse(null);
            }

            totalAmount = totalAmount.add(item.getTotalPrice());
            itemResponses.add(ReservationItemResponse.of(item, ticketTypeName, phaseName, seatCode));
        }

        return ReservationResponse.of(reservation, eventName, totalAmount, itemResponses);
    }
}
