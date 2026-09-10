package com.smartevent.modules.invoice.service;

import com.smartevent.common.enums.DeliveryStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import com.smartevent.modules.invoice.exception.InvoiceException;
import com.smartevent.modules.invoice.repository.InvoiceDeliveryRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.outbox.service.OutboxService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceDeliveryService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDeliveryRepository invoiceDeliveryRepository;
    private final InvoiceDocumentService invoiceDocumentService;
    private final EmailService emailService;
    private final OutboxService outboxService;

    @Transactional
    public InvoiceDelivery enqueue(Invoice invoice, String recipientEmail) {
        InvoiceDelivery delivery = invoiceDeliveryRepository.save(new InvoiceDelivery(invoice.getId(), recipientEmail));
        outboxService.publishEvent("INVOICE", invoice.getId(), new InvoiceCreatedEvent(
                invoice.getId(), delivery.getId(), invoice.getInvoiceCode(), invoice.getOrderId(),
                invoice.getUserId(), recipientEmail, invoice.getTotalAmount()));
        return delivery;
    }

    public void deliver(InvoiceCreatedEvent event) {
        if (event.deliveryId() != null && invoiceDeliveryRepository.findById(event.deliveryId())
                .map(delivery -> delivery.getStatus() == DeliveryStatus.SENT).orElse(false)) return;
        try {
            log.info("Notification Consumer: Nhận sự kiện hóa đơn xuất: {}", event.invoiceCode());

            // 1. Sinh file PDF hóa đơn thực tế
            var invoice = invoiceRepository.findById(event.invoiceId())
                    .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));
            byte[] pdfBytes = invoiceDocumentService.generate(invoice);

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
                    d.setStatus(DeliveryStatus.SENT);
                    d.setSentAt(java.time.Instant.now());
                    d.setProviderMessageId("SENT-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                    invoiceDeliveryRepository.save(d);
                });
            } else {
                var deliveries = invoiceDeliveryRepository.findByInvoiceId(event.invoiceId());
                for (var d : deliveries) {
                    if (d.getStatus() == DeliveryStatus.PENDING) {
                        d.setStatus(DeliveryStatus.SENT);
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
                        d.setStatus(DeliveryStatus.FAILED);
                        String errMsg = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                        d.setProviderMessageId("ERR: " + errMsg.substring(0, Math.min(errMsg.length(), 200)));
                        invoiceDeliveryRepository.save(d);
                    });
                } else {
                    var deliveries = invoiceDeliveryRepository.findByInvoiceId(event.invoiceId());
                    for (var d : deliveries) {
                        if (d.getStatus() == DeliveryStatus.PENDING) {
                            d.setStatus(DeliveryStatus.FAILED);
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
}
