package com.smartevent.modules.notification.consumer;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.config.RabbitMQConfig;
import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent;
import com.smartevent.modules.ordering.dto.event.OrderPaidEvent;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final com.smartevent.modules.invoice.repository.InvoiceRepository invoiceRepository;
    private final com.smartevent.modules.invoice.repository.InvoiceItemRepository invoiceItemRepository;
    private final com.smartevent.modules.invoice.repository.InvoiceDeliveryRepository invoiceDeliveryRepository;
    private final com.smartevent.modules.identity.repository.UserRepository userRepository;
    private final com.smartevent.modules.invoice.support.PdfInvoiceGenerator pdfInvoiceGenerator;

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
        InvoiceCreatedEvent event = null;
        try {
            event = objectMapper.readValue(messagePayload, InvoiceCreatedEvent.class);
            log.info("Notification Consumer: Nhận sự kiện hóa đơn xuất: {}", event.invoiceCode());

            // 1. Sinh file PDF hóa đơn thực tế
            var invoice = invoiceRepository.findById(event.invoiceId()).orElse(null);
            byte[] pdfBytes = null;
            if (invoice != null) {
                var items = invoiceItemRepository.findByInvoiceId(invoice.getId());
                var buyer = userRepository.findById(invoice.getUserId()).orElse(null);
                String buyerName = buyer != null ? buyer.getFullName() : "Khách hàng";
                pdfBytes = pdfInvoiceGenerator.generateInvoicePdf(invoice, items, buyerName, event.billingEmail());
            }

            // 2. Gửi email đính kèm file PDF hóa đơn
            emailService.sendInvoiceEmailWithPdf(
                    event.billingEmail(),
                    event.invoiceCode(),
                    event.totalAmount(),
                    event.occurredAt().toString(),
                    pdfBytes
            );

            // 3. Cập nhật chính xác bản ghi InvoiceDelivery tương ứng -> SENT
            if (event.deliveryId() != null) {
                invoiceDeliveryRepository.findById(event.deliveryId()).ifPresent(d -> {
                    d.setStatus(com.smartevent.common.enums.DeliveryStatus.SENT);
                    d.setSentAt(java.time.Instant.now());
                    d.setProviderMessageId("SENT-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                    invoiceDeliveryRepository.save(d);
                });
            } else {
                var deliveries = invoiceDeliveryRepository.findByInvoiceId(event.invoiceId());
                for (var d : deliveries) {
                    if (d.getStatus() == com.smartevent.common.enums.DeliveryStatus.PENDING) {
                        d.setStatus(com.smartevent.common.enums.DeliveryStatus.SENT);
                        d.setSentAt(java.time.Instant.now());
                        d.setProviderMessageId("SENT-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        invoiceDeliveryRepository.save(d);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Lỗi khi xử lý gửi email InvoiceCreatedEvent: {}", ex.getMessage(), ex);
            if (event != null) {
                if (event.deliveryId() != null) {
                    invoiceDeliveryRepository.findById(event.deliveryId()).ifPresent(d -> {
                        d.setStatus(com.smartevent.common.enums.DeliveryStatus.FAILED);
                        String errMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                        d.setProviderMessageId("ERR: " + errMsg.substring(0, Math.min(errMsg.length(), 200)));
                        invoiceDeliveryRepository.save(d);
                    });
                } else {
                    var deliveries = invoiceDeliveryRepository.findByInvoiceId(event.invoiceId());
                    for (var d : deliveries) {
                        if (d.getStatus() == com.smartevent.common.enums.DeliveryStatus.PENDING) {
                            d.setStatus(com.smartevent.common.enums.DeliveryStatus.FAILED);
                            String errMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                            d.setProviderMessageId("ERR: " + errMsg.substring(0, Math.min(errMsg.length(), 200)));
                            invoiceDeliveryRepository.save(d);
                        }
                    }
                }
            }
            throw new RuntimeException(ex);
        }
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
