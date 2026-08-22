package com.smartevent.modules.invoice.service;

import com.smartevent.common.enums.DeliveryStatus;
import com.smartevent.common.enums.InvoiceStatus;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.invoice.dto.request.SendInvoiceEmailRequest;
import com.smartevent.modules.invoice.dto.response.InvoiceDeliveryResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import com.smartevent.modules.invoice.exception.InvoiceException;
import com.smartevent.modules.invoice.repository.InvoiceDeliveryRepository;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.invoice.service.impl.InvoiceServiceImpl;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private InvoiceDeliveryRepository invoiceDeliveryRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventSeatRepository eventSeatRepository;
    @Mock private com.smartevent.modules.invoice.support.PdfInvoiceGenerator pdfInvoiceGenerator;
    @Mock private com.smartevent.modules.outbox.service.OutboxService outboxService;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private UUID userId;
    private UUID orderId;
    private Order paidOrder;
    private OrderItem item1;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        paidOrder = new Order(
                userId,
                UUID.randomUUID(),
                "ORD-20260822-12345678",
                BigDecimal.valueOf(1000000),
                BigDecimal.valueOf(100000),
                BigDecimal.valueOf(20000),
                BigDecimal.valueOf(920000),
                Instant.now().plusSeconds(600),
                "Note",
                PaymentMethod.VNPAY
        );
        paidOrder.setId(orderId);
        paidOrder.setStatus(OrderStatus.PAID);

        item1 = new OrderItem(orderId, UUID.randomUUID(), UUID.randomUUID(), null, 2, BigDecimal.valueOf(500000), BigDecimal.valueOf(1000000));
        item1.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Xuất hóa đơn thành công cho đơn hàng PAID (Tính đúng Subtotal, Discount, Fee, Total)")
    void issueInvoice_Success() {
        User user = new User();
        user.setId(userId);
        user.setEmail("customer@smartevent.com");

        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(invoiceRepository.existsByInvoiceCode(any())).thenReturn(false);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice inv = i.getArgument(0);
            inv.setId(UUID.randomUUID());
            return inv;
        });
        when(invoiceDeliveryRepository.save(any(InvoiceDelivery.class))).thenAnswer(i -> {
            InvoiceDelivery d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item1));

        InvoiceResponse response = invoiceService.issueInvoiceForOrder(orderId);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(920000), response.totalAmount());
        assertEquals("customer@smartevent.com", response.billingEmail());
        assertEquals(InvoiceStatus.ISSUED, response.status());

        verify(invoiceRepository, times(1)).save(any(Invoice.class));
        verify(invoiceItemRepository, times(1)).saveAll(anyList());
        verify(invoiceDeliveryRepository, times(1)).save(any(InvoiceDelivery.class));
    }

    @Test
    @DisplayName("Không cho phép xuất hóa đơn khi đơn hàng chưa thanh toán (PENDING_PAYMENT)")
    void issueInvoice_OrderNotPaid() {
        paidOrder.setStatus(OrderStatus.PENDING_PAYMENT);

        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(paidOrder));

        InvoiceException ex = assertThrows(InvoiceException.class,
                () -> invoiceService.issueInvoiceForOrder(orderId));
        assertEquals(ErrorCode.ORDER_INVALID_STATUS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Cơ chế Idempotency: Trả về hóa đơn hiện tại nếu đơn hàng đã từng được xuất hóa đơn")
    void issueInvoice_AlreadyIssued_ReturnsExisting() {
        Invoice existingInvoice = new Invoice(
                orderId, userId, "INV-20260822-EXISTING", "customer@smartevent.com",
                BigDecimal.valueOf(1000000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(1000000)
        );
        existingInvoice.setId(UUID.randomUUID());

        when(invoiceRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingInvoice));

        InvoiceResponse response = invoiceService.issueInvoiceForOrder(orderId);

        assertNotNull(response);
        assertEquals("INV-20260822-EXISTING", response.invoiceCode());
        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("Lấy chi tiết hóa đơn theo ID thành công khi là chính chủ sở hữu")
    void getInvoiceById_Success_Owner() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(
                orderId, userId, "INV-20260822-ABC12345", "customer@smartevent.com",
                BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500000)
        );
        invoice.setId(invoiceId);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        InvoiceResponse response = invoiceService.getInvoiceById(invoiceId, userId, false);

        assertNotNull(response);
        assertEquals("INV-20260822-ABC12345", response.invoiceCode());
    }

    @Test
    @DisplayName("Chặn người dùng lạ xem hóa đơn của người khác (Ném lỗi ACCESS_DENIED)")
    void getInvoiceById_AccessDenied() {
        UUID invoiceId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Invoice invoice = new Invoice(
                orderId, userId, "INV-20260822-ABC12345", "customer@smartevent.com",
                BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500000)
        );

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        InvoiceException ex = assertThrows(InvoiceException.class,
                () -> invoiceService.getInvoiceById(invoiceId, otherUserId, false));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Gửi lại hóa đơn qua email thành công và ghi nhận lịch sử SENT")
    void sendInvoiceEmail_Success() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice(
                orderId, userId, "INV-20260822-ABC12345", "customer@smartevent.com",
                BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500000)
        );
        invoice.setId(invoiceId);

        SendInvoiceEmailRequest request = new SendInvoiceEmailRequest("custom_email@gmail.com", "Gửi lại hóa đơn");

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(invoiceDeliveryRepository.save(any(InvoiceDelivery.class))).thenAnswer(i -> {
            InvoiceDelivery id = i.getArgument(0);
            return id;
        });

        InvoiceDeliveryResponse response = invoiceService.sendInvoiceEmail(invoiceId, userId, request);

        assertNotNull(response);
        assertEquals("custom_email@gmail.com", response.recipientEmail());
        assertEquals(DeliveryStatus.PENDING, response.status());
        verify(invoiceDeliveryRepository, times(1)).save(any(InvoiceDelivery.class));
        verify(outboxService, times(1)).publishEvent(eq("INVOICE"), eq(invoiceId), any());
    }
}