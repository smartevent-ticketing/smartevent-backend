package com.smartevent.modules.payment.service.impl;

import tools.jackson.databind.ObjectMapper;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.PaymentStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.util.VNPayUtils;
import com.smartevent.config.VNPayProperties;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.payment.dto.request.CreatePaymentRequest;
import com.smartevent.modules.payment.dto.response.PaymentResponse;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.dto.response.VNPayReturnResponse;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.entity.PaymentWebhookEvent;
import com.smartevent.modules.payment.exception.PaymentException;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.payment.repository.PaymentWebhookEventRepository;
import com.smartevent.modules.payment.service.PaymentGatewayProvider;
import com.smartevent.modules.payment.service.PaymentService;
import com.smartevent.modules.reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final ReservationService reservationService;
    private final VNPayProperties vnPayProperties;
    private final ObjectMapper objectMapper;
    private final Map<PaymentMethod, PaymentGatewayProvider> gatewayProviders;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentWebhookEventRepository webhookEventRepository,
                              OrderRepository orderRepository,
                              ReservationService reservationService,
                              VNPayProperties vnPayProperties,
                              ObjectMapper objectMapper,
                              List<PaymentGatewayProvider> providers) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderRepository = orderRepository;
        this.reservationService = reservationService;
        this.vnPayProperties = vnPayProperties;
        this.objectMapper = objectMapper;
        // Tự động gom tất cả Provider theo PaymentMethod
        this.gatewayProviders = providers.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getPaymentMethod, Function.identity()));
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(UUID currentUserId, CreatePaymentRequest request, HttpServletRequest servletRequest) {
        // 1. Kiểm tra đơn hàng
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new OrderingException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (!order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thanh toán đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderingException(ErrorCode.ORDER_INVALID_STATUS, "Đơn hàng không ở trạng thái chờ thanh toán");
        }

        if (order.isExpired()) {
            throw new OrderingException(ErrorCode.ORDER_EXPIRED, "Đơn hàng đã hết hạn thanh toán 10 phút");
        }

        // 2. Tìm Provider tương ứng với PaymentMethod
        PaymentGatewayProvider provider = gatewayProviders.get(request.paymentMethod());
        if (provider == null) {
            throw new PaymentException(ErrorCode.BUSINESS_RULE_VIOLATION, "Phương thức thanh toán chưa được hỗ trợ: " + request.paymentMethod());
        }

        // 3. Tạo bản ghi Payment ở trạng thái INITIATED
        Payment payment = new Payment(order.getId(), request.paymentMethod(), request.paymentMethod().name(), order.getTotalAmount());
        Payment savedPayment = paymentRepository.save(payment);

        // 4. Sinh đường link thanh toán có chữ ký số
        String paymentUrl = provider.createPaymentUrl(savedPayment, order, servletRequest, request.bankCode());

        return new PaymentResponse(
                savedPayment.getId(),
                order.getId(),
                order.getOrderCode(),
                savedPayment.getAmount(),
                savedPayment.getPaymentMethod(),
                savedPayment.getStatus(),
                paymentUrl,
                savedPayment.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public VNPayIpnResponse handleVNPayIpn(Map<String, String> params) {
        log.info("Nhận thông báo Webhook IPN từ VNPay: {}", params);

        String secureHash = params.get("vnp_SecureHash");
        String orderCode = params.get("vnp_TxnRef");
        String transactionNo = params.get("vnp_TransactionNo");
        String amountStr = params.get("vnp_Amount");
        String responseCode = params.get("vnp_ResponseCode");

        // BƯỚC 1: Xác thực chữ ký số HMAC-SHA512
        boolean isValidSignature = VNPayUtils.verifySignature(params, secureHash, vnPayProperties.getHashSecret());
        if (!isValidSignature) {
            log.error("Chữ ký Webhook IPN VNPay không hợp lệ!");
            return VNPayIpnResponse.invalidChecksum();
        }

        // BƯỚC 2: Kiểm tra Idempotency (Chống xử lý lặp lại)
        String providerEventId = orderCode + "_" + transactionNo;
        if (webhookEventRepository.existsByProviderAndProviderEventId("VNPAY", providerEventId)) {
            log.warn("Sự kiện Webhook VNPay {} đã được xử lý trước đó (Idempotency Guard)", providerEventId);
            return VNPayIpnResponse.orderAlreadyConfirmed();
        }

        // BƯỚC 3: Tra cứu Đơn hàng
        var orderOpt = orderRepository.findByOrderCode(orderCode);
        if (orderOpt.isEmpty()) {
            log.error("Không tìm thấy đơn hàng có mã {} từ IPN VNPay", orderCode);
            return VNPayIpnResponse.orderNotFound();
        }
        Order order = orderOpt.get();

        // BƯỚC 4: Kiểm tra toàn vẹn số tiền (Amount Integrity Check)
        BigDecimal vnpAmount = new BigDecimal(amountStr).divide(BigDecimal.valueOf(100));
        if (order.getTotalAmount().compareTo(vnpAmount) != 0) {
            log.error("Sai lệch số tiền! Đơn hàng: {}, VNPay gửi: {}", order.getTotalAmount(), vnpAmount);
            return VNPayIpnResponse.invalidAmount();
        }

        // BƯỚC 5: Kiểm tra trạng thái đơn hàng
        if (order.getStatus() == OrderStatus.PAID) {
            log.warn("Đơn hàng {} đã ở trạng thái PAID", orderCode);
            return VNPayIpnResponse.orderAlreadyConfirmed();
        }

        // BƯỚC 6: Xử lý thành công vs thất bại
        Payment payment = paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(order.getId(), PaymentStatus.INITIATED)
                .orElseGet(() -> new Payment(order.getId(), PaymentMethod.VNPAY, "VNPAY", vnpAmount));

        payment.setTransactionId(transactionNo);

        if ("00".equals(responseCode)) {
            // Thanh toán thành công
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(Instant.now());
            order.setStatus(OrderStatus.PAID);

            // BƯỚC 7: Kích hoạt chuỗi hậu thanh toán (Chốt vé SOLD trong Reservation)
            if (order.getReservationId() != null) {
                reservationService.confirmReservation(order.getReservationId());
                log.info("Đã chốt vé thành công (HELD -> SOLD) cho phiên giữ chỗ {}", order.getReservationId());
            }
        } else {
            // Thanh toán thất bại hoặc khách bấm hủy
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Giao dịch VNPay thất bại cho đơn hàng {} với mã lỗi {}", orderCode, responseCode);
        }

        paymentRepository.save(payment);
        orderRepository.save(order);

        // BƯỚC 8: Lưu bản ghi Webhook Event
        try {
            String payloadJson = objectMapper.writeValueAsString(params);
            PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent("VNPAY", providerEventId, transactionNo, secureHash, payloadJson);
            webhookEvent.setStatus("PROCESSED");
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventRepository.save(webhookEvent);
        } catch (Exception ex) {
            log.error("Lỗi khi lưu webhook event: {}", ex.getMessage());
        }

        log.info("Xử lý IPN VNPay thành công cho đơn hàng {}", orderCode);
        return VNPayIpnResponse.success();
    }

    @Override
    @Transactional(readOnly = true)
    public VNPayReturnResponse handleVNPayReturn(Map<String, String> params) {
        String orderCode = params.get("vnp_TxnRef");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String amountStr = params.get("vnp_Amount");
        String payDate = params.get("vnp_PayDate");
        String responseCode = params.get("vnp_ResponseCode");

        BigDecimal amount = amountStr != null ? new BigDecimal(amountStr).divide(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
        String status = "00".equals(responseCode) ? "SUCCESS" : "FAILED";
        String message = "00".equals(responseCode) ? "Giao dịch thanh toán thành công" : "Giao dịch không thành công hoặc đã bị hủy";

        return new VNPayReturnResponse(orderCode, transactionNo, bankCode, amount, payDate, status, message);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID paymentId, UUID currentUserId, boolean isAdmin) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy thông tin thanh toán"));

        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new OrderingException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng liên quan"));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new PaymentException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem thông tin thanh toán này");
        }

        return new PaymentResponse(
                payment.getId(),
                order.getId(),
                order.getOrderCode(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                null,
                payment.getCreatedAt()
        );
    }
}