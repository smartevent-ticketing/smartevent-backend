package com.smartevent.modules.payment.service.impl;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.payment.dto.request.CreatePaymentRequest;
import com.smartevent.modules.payment.dto.response.PaymentResponse;
import com.smartevent.modules.payment.dto.response.VNPayIpnResponse;
import com.smartevent.modules.payment.dto.response.VNPayReturnResponse;
import com.smartevent.modules.payment.entity.Payment;
import com.smartevent.modules.payment.exception.PaymentException;
import com.smartevent.modules.payment.repository.PaymentRepository;
import com.smartevent.modules.payment.service.PaymentGatewayProvider;
import com.smartevent.modules.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final Map<PaymentMethod, PaymentGatewayProvider> gatewayProviders;
    private final VNPayCallbackHandler callbackHandler;
    private final com.smartevent.modules.ordering.service.OrderLifecycleService orderLifecycleService;

    public PaymentServiceImpl(PaymentRepository paymentRepository, OrderRepository orderRepository,
                              List<PaymentGatewayProvider> providers, VNPayCallbackHandler callbackHandler,
                              com.smartevent.modules.ordering.service.OrderLifecycleService orderLifecycleService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.callbackHandler = callbackHandler;
        this.orderLifecycleService = orderLifecycleService;
        this.gatewayProviders = providers.stream()
                .collect(Collectors.toMap(PaymentGatewayProvider::getPaymentMethod, Function.identity()));
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(UUID currentUserId, CreatePaymentRequest request, HttpServletRequest servletRequest) {
        // 1. Kiểm tra đơn hàng
        Order order = orderLifecycleService.lockForPayment(request.orderId())
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
        orderLifecycleService.requirePayableReservation(order);

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

    @Override
    public VNPayIpnResponse handleVNPayIpn(Map<String, String> params) {
        return callbackHandler.handleVNPayIpn(params);
    }

    @Override
    public VNPayReturnResponse handleVNPayReturn(Map<String, String> params) {
        return callbackHandler.handleVNPayReturn(params);
    }
}
