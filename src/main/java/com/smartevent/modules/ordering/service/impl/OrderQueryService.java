package com.smartevent.modules.ordering.service.impl;

import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.ordering.dto.response.OrderItemResponse;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderItemRepository orderItemRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;
    private final EventSeatRepository eventSeatRepository;

    public OrderResponse toResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : items) {
            String ticketTypeName = ticketTypeRepository.findById(item.getTicketTypeId())
                    .map(TicketType::getName).orElse("Unknown Ticket Type");
            String phaseName = ticketSalePhaseRepository.findById(item.getSalePhaseId())
                    .map(TicketSalePhase::getName).orElse("Unknown Phase");
            String seatCode = null;
            if (item.getEventSeatId() != null) {
                seatCode = eventSeatRepository.findById(item.getEventSeatId())
                        .map(EventSeat::getSeatNumber).orElse(null);
            }
            itemResponses.add(OrderItemResponse.of(item, ticketTypeName, phaseName, seatCode));
        }
        return OrderResponse.of(order, itemResponses);
    }
}
