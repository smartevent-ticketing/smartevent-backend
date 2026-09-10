package com.smartevent.modules.ordering.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ordering.service.OrderLifecycleService;
import com.smartevent.modules.ordering.service.OrderService;
import com.smartevent.modules.reservation.service.ReservationCheckoutService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderQueryService orderQueryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationCheckoutService reservationCheckoutService;
    private final OrderLifecycleService orderLifecycleService;

    @Override
    @Transactional
    public OrderResponse createOrderFromReservation(UUID currentUserId, CreateOrderRequest request) {
        // 1. Kiểm tra Reservation tồn tại và hợp lệ
        var reservation = reservationCheckoutService.lockSnapshot(request.reservationId())
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên giữ chỗ"));
        if (!reservation.userId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền tạo đơn hàng từ phiên giữ chỗ này");
        }
        if (reservation.status() != ReservationStatus.PENDING) {
            throw new OrderingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Phiên giữ chỗ không ở trạng thái chờ thanh toán");
        }
        if (reservation.isExpired()) {
            throw new OrderingException(ErrorCode.ORDER_EXPIRED, "Phiên giữ chỗ đã hết hạn 10 phút, vui lòng chọn lại vé");
        }
        // 2. Chống tạo đơn trùng: Nếu đã có đơn PENDING_PAYMENT cho reservation này thì tái sử dụng
        var existingOrderOpt = orderRepository.findByReservationId(reservation.id());
        if (existingOrderOpt.isPresent()) {
            Order existingOrder = existingOrderOpt.get();
            if (existingOrder.isPendingPayment() && !existingOrder.isExpired()) {
                log.info("Tái sử dụng đơn hàng cũ {} cho phiên giữ chỗ {}", existingOrder.getOrderCode(), reservation.id());
                return orderQueryService.toResponse(existingOrder);
            }
            throw new OrderingException(ErrorCode.ORDER_INVALID_STATUS,
                    "Phiên giữ chỗ này đã có đơn hàng. Vui lòng chọn lại vé để tạo đơn mới");
        }
        // 3. Đóng băng dữ liệu giá từ Reservation sang Order
        var reservationItems = reservation.items();
        if (reservationItems.isEmpty()) {
            throw new OrderingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Phiên giữ chỗ không có vé nào");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : reservationItems) {
            subtotal = subtotal.add(item.totalPrice());
        }
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(feeAmount);
        // 4. Sinh mã đơn hàng duy nhất (ORD-yyyyMMdd-XXXXXX)
        String orderCode = generateUniqueOrderCode();
        PaymentMethod paymentMethod = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.VNPAY;
        Order order = new Order(
                currentUserId,
                reservation.id(),
                orderCode,
                subtotal,
                discountAmount,
                feeAmount,
                totalAmount,
                reservation.expiresAt(),
                request.customerNote(),
                paymentMethod
        );
        Order savedOrder = orderRepository.save(order);
        // 5. Lưu chi tiết các dòng vé vào order_items
        List<OrderItem> orderItems = new ArrayList<>();
        for (var resItem : reservationItems) {
            OrderItem orderItem = new OrderItem(
                    savedOrder.getId(),
                    resItem.ticketTypeId(),
                    resItem.salePhaseId(),
                    resItem.eventSeatId(),
                    resItem.quantity(),
                    resItem.unitPrice(),
                    resItem.totalPrice()
            );
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);
        log.info("Tạo thành công đơn hàng {} cho người dùng {} với tổng tiền {}", orderCode, currentUserId, totalAmount);
        return orderQueryService.toResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID oderId, UUID currentUserId, boolean isAdmin) {
        Order order = orderRepository.findById(oderId)
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng."));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đơn hàng này");
        }

        return orderQueryService.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOderByOrderCode(String orderCode, UUID currentUserId, boolean isAdmin) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mã đơn hàng"));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đơn hàng này");
        }

        return orderQueryService.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(UUID currentUserId, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(orderQueryService::toResponse)
                .toList();

        return PageResponse.from(orderPage, orderResponses);
    }

    @Override
    public void cancelOrder(UUID orderId, UUID currentUserId, boolean isAdmin) {
        orderLifecycleService.cancelOrder(orderId, currentUserId, isAdmin);
    }

    @Override
    public void expireOrder(UUID orderId) {
        orderLifecycleService.expireOrder(orderId);
    }

    // --- Helper Methods ---
    private String generateUniqueOrderCode() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomHex = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String orderCode = "ORD-" + datePrefix + "-" + randomHex;
        while (orderRepository.existsByOrderCode(orderCode)) {
            randomHex = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            orderCode = "ORD-" + datePrefix + "-" + randomHex;
        }
        return orderCode;
    }

}
