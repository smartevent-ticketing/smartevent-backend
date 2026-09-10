package com.smartevent.modules.payment.service.impl;

import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.modules.invoice.service.InvoiceService;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.service.OrderLifecycleService;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.ticket.service.TicketService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCompletionService {

    private final OrderLifecycleService orderLifecycleService;
    private final TicketService ticketService;
    private final InvoiceService invoiceService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void complete(Order order, Payment payment, String transactionNo) {
        payment.setPaidAt(Instant.now());
        payment.setStatus(PaymentStatus.SUCCESS);
        if (orderLifecycleService.completePayment(order, transactionNo)) {
            ticketService.issueTicketsForOrder(order.getId());
            invoiceService.issueInvoiceForOrder(order.getId());
        }
    }
}
