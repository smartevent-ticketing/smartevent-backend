package com.smartevent.modules.notification.consumer;

import com.smartevent.config.RabbitMQConfig;
import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent;
import com.smartevent.modules.invoice.service.InvoiceDeliveryService;
import com.smartevent.modules.ordering.dto.event.OrderPaidEvent;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final InvoiceDeliveryService invoiceDeliveryService;

    @RabbitListener(queues = RabbitMQConfig.TICKET_ISSUED_QUEUE)
    public void handleTicketIssuedEvent(String messagePayload) {
        try {
            TicketIssuedEvent event = objectMapper.readValue(messagePayload, TicketIssuedEvent.class);
            log.info("Notification Consumer: Nhận sự kiện vé phát hành: {}", event.ticketCode());

            emailService.sendTicketEmail(
                    event.ownerEmail(),
                    event.ticketCode(),
                    event.eventName(),
                    event.seatCode(),
                    event.qrCodeBase64()
            );
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý sự kiện TicketIssuedEvent: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.INVOICE_CREATED_QUEUE)
    public void handleInvoiceCreatedEvent(String messagePayload) {
        InvoiceCreatedEvent event = objectMapper.readValue(messagePayload, InvoiceCreatedEvent.class);
        invoiceDeliveryService.deliver(event);
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_PAID_QUEUE)
    public void handleOrderPaidEvent(String messagePayload) {
        try {
            OrderPaidEvent event = objectMapper.readValue(messagePayload, OrderPaidEvent.class);
            log.info("Notification Consumer: Nhận sự kiện thanh toán thành công: {} ({} VNĐ)", event.orderCode(), event.totalAmount());
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý sự kiện OrderPaidEvent: {}", ex.getMessage(), ex);
        }
    }
}
