package com.smartevent.modules.notification.consumer;

import com.smartevent.common.enums.DeliveryStatus;
import com.smartevent.infrastructure.mail.EmailService;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import com.smartevent.modules.invoice.repository.InvoiceDeliveryRepository;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.invoice.service.InvoiceDeliveryService;
import com.smartevent.modules.invoice.service.InvoiceDocumentService;
import com.smartevent.modules.invoice.support.PdfInvoiceGenerator;
import com.smartevent.modules.outbox.service.OutboxService;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock private EmailService emailService;
    @Mock private ObjectMapper objectMapper;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private InvoiceDeliveryRepository invoiceDeliveryRepository;
    @Mock private UserRepository userRepository;
    @Mock private PdfInvoiceGenerator pdfInvoiceGenerator;

    @Mock private OutboxService outboxService;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void composeServices() {
        consumer = new NotificationEventConsumer(
                emailService,
                objectMapper,
                new InvoiceDeliveryService(
                        invoiceRepository,
                        invoiceDeliveryRepository,
                        new InvoiceDocumentService(invoiceItemRepository, userRepository, pdfInvoiceGenerator),
                        emailService,
                        outboxService));
    }

    @Test
    @DisplayName("Xử lý TicketIssuedEvent thành công: Gửi email vé và QR")
    void handleTicketIssuedEvent_Success() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        TicketIssuedEvent event = new TicketIssuedEvent(
                ticketId, "TCK-12345", eventId, "Concert Test",
                userId, "user@gmail.com", "Ghế A-1", "VIP", "data:image/png;base64,sample"
        );

        when(objectMapper.readValue(any(String.class), eq(TicketIssuedEvent.class))).thenReturn(event);

        consumer.handleTicketIssuedEvent("payload");

        verify(emailService, times(1)).sendTicketEmail(
                eq("user@gmail.com"), eq("TCK-12345"), eq("Concert Test"), eq("Ghế A-1"), eq("data:image/png;base64,sample")
        );
    }

    @Test
    @DisplayName("Xử lý InvoiceCreatedEvent thành công: Sinh PDF, gửi email đính kèm và cập nhật đúng deliveryId -> SENT")
    void handleInvoiceCreatedEvent_Success() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        InvoiceCreatedEvent event = new InvoiceCreatedEvent(
                invoiceId, deliveryId, "INV-9999", orderId, userId, "buyer@gmail.com", BigDecimal.valueOf(1000000)
        );

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setUserId(userId);
        invoice.setBillingEmail("buyer@gmail.com");

        User user = new User();
        user.setFullName("Nguyen Van A");

        InvoiceDelivery delivery = new InvoiceDelivery(invoiceId, "buyer@gmail.com");
        delivery.setStatus(DeliveryStatus.PENDING);

        byte[] fakePdf = "FAKE_PDF_CONTENT".getBytes();

        when(objectMapper.readValue(any(String.class), eq(InvoiceCreatedEvent.class))).thenReturn(event);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByInvoiceId(invoiceId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pdfInvoiceGenerator.generateInvoicePdf(eq(invoice), any(), eq("Nguyen Van A"), eq("buyer@gmail.com"))).thenReturn(fakePdf);
        when(invoiceDeliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));

        consumer.handleInvoiceCreatedEvent("payload");

        verify(emailService, times(1)).sendInvoiceEmailWithPdf(
                eq("buyer@gmail.com"), eq("INV-9999"), eq(BigDecimal.valueOf(1000000)), any(), eq(fakePdf)
        );
        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertNotNull(delivery.getSentAt());
        assertTrue(delivery.getProviderMessageId().startsWith("SENT-"));
        verify(invoiceDeliveryRepository, times(1)).save(delivery);
    }

    @Test
    @DisplayName("Xử lý InvoiceCreatedEvent khi SMTP lỗi: Đánh dấu deliveryId -> FAILED và ném RuntimeException để RabbitMQ retry/DLQ")
    void handleInvoiceCreatedEvent_SmtpFailure_MarksFailedAndThrows() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        InvoiceCreatedEvent event = new InvoiceCreatedEvent(
                invoiceId, deliveryId, "INV-9999", orderId, userId, "buyer@gmail.com", BigDecimal.valueOf(1000000)
        );

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        invoice.setUserId(userId);
        invoice.setBillingEmail("buyer@gmail.com");

        InvoiceDelivery delivery = new InvoiceDelivery(invoiceId, "buyer@gmail.com");
        delivery.setStatus(DeliveryStatus.PENDING);

        when(objectMapper.readValue(any(String.class), eq(InvoiceCreatedEvent.class))).thenReturn(event);
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDeliveryRepository.findById(deliveryId)).thenReturn(Optional.of(delivery));
        doThrow(new RuntimeException("SMTP Connection Timeout")).when(emailService)
                .sendInvoiceEmailWithPdf(any(), any(), any(), any(), any());

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> consumer.handleInvoiceCreatedEvent("payload"));
        assertTrue(thrown.getMessage().contains("SMTP Connection Timeout"));

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertTrue(delivery.getProviderMessageId().contains("SMTP Connection Timeout"));
        verify(invoiceDeliveryRepository, times(1)).save(delivery);
    }
}
