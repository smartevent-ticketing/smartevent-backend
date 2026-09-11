package com.smartevent.modules.event.service.impl;

import com.smartevent.modules.event.service.EventCancelled;
import com.smartevent.modules.ordering.service.OrderLifecycleService;
import com.smartevent.modules.payment.service.PaymentReconciliationService;
import com.smartevent.modules.reservation.service.ReservationService;
import com.smartevent.modules.ticket.service.impl.TicketCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EventCancellationHandler {
    private final OrderLifecycleService orderLifecycleService;
    private final ReservationService reservationService;
    private final TicketCancellationService ticketCancellationService;
    private final PaymentReconciliationService reconciliationService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelRelatedBookings(EventCancelled event) {
        var paidOrders = orderLifecycleService.cancelForEvent(event.eventId());
        reservationService.cancelPendingForEvent(event.eventId());
        ticketCancellationService.cancelForEvent(event.eventId());
        paidOrders.forEach(orderId -> reconciliationService.requireReviewForOrder(orderId, "EVENT_CANCELLED"));
    }
}
