package com.smartevent.modules.payment.service;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.payment.dto.request.CreatePaymentRequest;
import com.smartevent.modules.payment.dto.response.PaymentResponse;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.payment.repository.PaymentWebhookEventRepository;
import com.smartevent.modules.payment.service.impl.PaymentServiceImpl;
import com.smartevent.modules.reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentWebhookEventRepository webhookEventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReservationService reservationService;
    @Mock private HttpServletRequest servletRequest;
    @Mock private PaymentGatewayProvider vnpayGatewayProvider;
    @Mock private com.smartevent.modules.ticket.service.TicketService ticketService;
    @Mock private com.smartevent.modules.invoice.service.InvoiceService invoiceService;

    private VNPayProperties vnPayProperties;
    private ObjectMapper objectMapper;
    private PaymentServiceImpl paymentService;

    private UUID userId;
    private UUID orderId;
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        vnPayProperties = new VNPayProperties();
        vnPayProperties.setTmnCode("TEST_TMN");
        vnPayProperties.setHashSecret("TEST_SECRET_KEY_123456");
        vnPayProperties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        vnPayProperties.setReturnUrl("http://localhost:8080/return");
        vnPayProperties.setIpnUrl("http://localhost:8080/ipn");

        objectMapper = new ObjectMapper();

        when(vnpayGatewayProvider.getPaymentMethod()).thenReturn(PaymentMethod.VNPAY);

        paymentService = new PaymentServiceImpl(
                paymentRepository,
                webhookEventRepository,
                orderRepository,
                reservationService,
                vnPayProperties,
                objectMapper,
                List.of(vnpayGatewayProvider),
                ticketService,
                invoiceService
        );

        pendingOrder = new Order(
                userId,
                UUID.randomUUID(),
                "ORD-20260822-ABC12345",
                BigDecimal.valueOf(500000),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.valueOf(500000),
                Instant.now().plus(10, ChronoUnit.MINUTES),
                "Note",
                PaymentMethod.VNPAY
        );
        pendingOrder.setId(orderId);
        pendingOrder.setStatus(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Khởi tạo Pay URL VNPay thành công (Status INITIATED)")
    void createPayment_Success() {
        CreatePaymentRequest request = new CreatePaymentRequest(orderId, PaymentMethod.VNPAY, null);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(pendingOrder));
        when(vnpayGatewayProvider.createPaymentUrl(any(), any(), any(), any()))
                .thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=50000000");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentResponse response = paymentService.createPayment(userId, request, servletRequest);

        assertNotNull(response);
        assertEquals(PaymentStatus.INITIATED, response.status());
        assertNotNull(response.paymentUrl());
    }

    @Test
    @DisplayName("Xử lý IPN VNPay thành công -> Đổi Order PAID & Gọi confirmReservation chốt vé SOLD")
    void handleVNPayIpn_Success() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", pendingOrder.getOrderCode());
        params.put("vnp_Amount", "50000000"); // 500.000 VNĐ * 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");

        String secureHash = VNPayUtils.hashAllFields(params, vnPayProperties.getHashSecret());
        params.put("vnp_SecureHash", secureHash);

        when(webhookEventRepository.existsByProviderAndProviderEventId(eq("VNPAY"), any())).thenReturn(false);
        when(orderRepository.findByOrderCode(pendingOrder.getOrderCode())).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(eq(orderId), eq(PaymentStatus.INITIATED)))
                .thenReturn(Optional.empty());
        when(reservationService.confirmReservation(pendingOrder.getReservationId())).thenReturn(true);

        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);

        assertEquals("00", response.rspCode());
        assertEquals("Confirm Success", response.message());
        assertEquals(OrderStatus.PAID, pendingOrder.getStatus());
        verify(reservationService, times(1)).confirmReservation(pendingOrder.getReservationId());
        verify(ticketService, times(1)).issueTicketsForOrder(pendingOrder.getId());
        verify(invoiceService, times(1)).issueInvoiceForOrder(pendingOrder.getId());
        verify(webhookEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Xử lý IPN khi khách thanh toán thành công nhưng Reservation đã hết hạn -> Order CANCELLED kèm note đối soát hoàn tiền")
    void handleVNPayIpn_LatePayment_SetsCancelledAndNotesWithoutRollback() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", pendingOrder.getOrderCode());
        params.put("vnp_Amount", "50000000"); // 500.000 VNĐ * 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");

        String secureHash = VNPayUtils.hashAllFields(params, vnPayProperties.getHashSecret());
        params.put("vnp_SecureHash", secureHash);

        when(webhookEventRepository.existsByProviderAndProviderEventId(eq("VNPAY"), any())).thenReturn(false);
        when(orderRepository.findByOrderCode(pendingOrder.getOrderCode())).thenReturn(Optional.of(pendingOrder));
        when(paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(eq(orderId), eq(PaymentStatus.INITIATED)))
                .thenReturn(Optional.empty());

        // Giả lập phiên giữ chỗ đã hết hạn 10 phút trước đó
        when(reservationService.confirmReservation(pendingOrder.getReservationId())).thenReturn(false);

        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);

        assertEquals("00", response.rspCode());
        assertEquals("Confirm Success", response.message());
        assertEquals(OrderStatus.CANCELLED, pendingOrder.getStatus());
        assertTrue(pendingOrder.getCustomerNote().contains("LATE_PAYMENT_EXPIRED"));
        verify(ticketService, never()).issueTicketsForOrder(any());
        verify(invoiceService, never()).issueInvoiceForOrder(any());
        verify(webhookEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Xử lý IPN từ chối khi sai chữ ký băm (RspCode 97 - Invalid Checksum)")
    void handleVNPayIpn_InvalidChecksum() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", pendingOrder.getOrderCode());
        params.put("vnp_Amount", "50000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "FAKE_INVALID_HASH_123");

        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);

        assertEquals("97", response.rspCode());
        assertEquals("Invalid Checksum", response.message());
        verify(reservationService, never()).confirmReservation(any());
    }

    @Test
    @DisplayName("Xử lý IPN chống trùng lặp (Idempotency - RspCode 02 Order already confirmed)")
    void handleVNPayIpn_Idempotency_AlreadyProcessed() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", pendingOrder.getOrderCode());
        params.put("vnp_Amount", "50000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");

        String secureHash = VNPayUtils.hashAllFields(params, vnPayProperties.getHashSecret());
        params.put("vnp_SecureHash", secureHash);

        // Giả lập sự kiện này đã được nhận và xử lý trước đó
        when(webhookEventRepository.existsByProviderAndProviderEventId(eq("VNPAY"), any())).thenReturn(true);

        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);

        assertEquals("02", response.rspCode());
        assertEquals("Order already confirmed", response.message());
        verify(reservationService, never()).confirmReservation(any());
    }

    @Test
    @DisplayName("Xử lý IPN phát hiện gian lận sai lệch số tiền (RspCode 04 - Invalid Amount)")
    void handleVNPayIpn_InvalidAmount() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", pendingOrder.getOrderCode());
        params.put("vnp_Amount", "100000"); // Hacker sửa tiền từ 500k xuống 1k (1000 * 100)
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");

        String secureHash = VNPayUtils.hashAllFields(params, vnPayProperties.getHashSecret());
        params.put("vnp_SecureHash", secureHash);

        when(webhookEventRepository.existsByProviderAndProviderEventId(eq("VNPAY"), any())).thenReturn(false);
        when(orderRepository.findByOrderCode(pendingOrder.getOrderCode())).thenReturn(Optional.of(pendingOrder));

        VNPayIpnResponse response = paymentService.handleVNPayIpn(params);

        assertEquals("04", response.rspCode());
        assertEquals("Invalid Amount", response.message());
        verify(reservationService, never()).confirmReservation(any());
    }
}