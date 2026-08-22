package com.smartevent.modules.ordering.service.impl;

import com.smartevent.common.api.PageResponse;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.enums.PaymentMethod;
import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderItemResponse;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ordering.service.OrderService;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import com.smartevent.modules.reservation.service.ReservationService;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final ReservationService reservationService;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository ticketSalePhaseRepository;
    private final EventSeatRepository eventSeatRepository;

    @Override
    @Transactional
    public OrderResponse createOrderFromReservation(UUID currentUserId, CreateOrderRequest request) {
        // 1. Kiểm tra Reservation tồn tại và hợp lệ
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên giữ chỗ"));
        if (!reservation.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền tạo đơn hàng từ phiên giữ chỗ này");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new OrderingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Phiên giữ chỗ không ở trạng thái chờ thanh toán");
        }
        if (reservation.isExpired()) {
            throw new OrderingException(ErrorCode.ORDER_EXPIRED, "Phiên giữ chỗ đã hết hạn 10 phút, vui lòng chọn lại vé");
        }
        // 2. Chống tạo đơn trùng: Nếu đã có đơn PENDING_PAYMENT cho reservation này thì tái sử dụng
        var existingOrderOpt = orderRepository.findByReservationId(reservation.getId());
        if (existingOrderOpt.isPresent()) {
            Order existingOrder = existingOrderOpt.get();
            if (existingOrder.isPendingPayment() && !existingOrder.isExpired()) {
                log.info("Tái sử dụng đơn hàng cũ {} cho phiên giữ chỗ {}", existingOrder.getOrderCode(), reservation.getId());
                return buildOrderResponse(existingOrder);
            }
        }
        // 3. Đóng băng dữ liệu giá từ Reservation sang Order
        List<ReservationItem> reservationItems = reservationItemRepository.findByReservationId(reservation.getId());
        if (reservationItems.isEmpty()) {
            throw new OrderingException(ErrorCode.BUSINESS_RULE_VIOLATION, "Phiên giữ chỗ không có vé nào");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ReservationItem item : reservationItems) {
            subtotal = subtotal.add(item.getTotalPrice());
        }
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal feeAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.subtract(discountAmount).add(feeAmount);
        // 4. Sinh mã đơn hàng duy nhất (ORD-yyyyMMdd-XXXXXX)
        String orderCode = generateUniqueOrderCode();
        PaymentMethod paymentMethod = request.paymentMethod() != null ? request.paymentMethod() : PaymentMethod.VNPAY;
        Order order = new Order(
                currentUserId,
                reservation.getId(),
                orderCode,
                subtotal,
                discountAmount,
                feeAmount,
                totalAmount,
                reservation.getExpiresAt(),
                request.customerNote(),
                paymentMethod
        );
        Order savedOrder = orderRepository.save(order);
        // 5. Lưu chi tiết các dòng vé vào order_items
        List<OrderItem> orderItems = new ArrayList<>();
        for (ReservationItem resItem : reservationItems) {
            OrderItem orderItem = new OrderItem(
                    savedOrder.getId(),
                    resItem.getTicketTypeId(),
                    resItem.getSalePhaseId(),
                    resItem.getEventSeatId(),
                    resItem.getQuantity(),
                    resItem.getUnitPrice(),
                    resItem.getTotalPrice()
            );
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);
        log.info("Tạo thành công đơn hàng {} cho người dùng {} với tổng tiền {}", orderCode, currentUserId, totalAmount);
        return buildOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID oderId, UUID currentUserId, boolean isAdmin) {
        Order order = orderRepository.findById(oderId)
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng."));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đơn hàng này");
        }

        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOderByOrderCode(String orderCode, UUID currentUserId, boolean isAdmin) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new OrderingException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy mã đơn hàng"));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem đơn hàng này");
        }

        return buildOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getMyOrders(UUID currentUserId, Pageable pageable) {
        Page<Order> orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(currentUserId, pageable);
        List<OrderResponse> orderResponses = orderPage.getContent().stream()
                .map(this::buildOrderResponse)
                .toList();

        return PageResponse.from(orderPage, orderResponses);
    }

    @Override
    @Transactional
    public void cancelOrder(UUID orderId, UUID currentUserId, boolean isAdmin) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderingException(ErrorCode.ORDER_NOT_FOUND, "Đơn hàng không tồn tại"));

        if (!isAdmin && !order.getUserId().equals(currentUserId)) {
            throw new OrderingException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền hủy đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new OrderingException(ErrorCode.ORDER_INVALID_STATUS, "Chỉ có thể hủy đơn hàng đang chờ thanh toán");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // Đồng bộ hủy phiên giữ chô và nhà kho vé, mở khóa ghế
        if (order.getReservationId() != null) {
            reservationService.cancelReservation(order.getReservationId(), currentUserId, isAdmin);
        }

        log.info("Người dùng {} đã hủy thành công đơn hàng {}", currentUserId, order.getOrderCode());
    }

    @Override
    @Transactional
    public void expireOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            log.info("Đơn hàng {} đã hết hạn thanh toán 10 phút, chuyển trạng thái sang EXPIRED", order.getOrderCode());
        }
    }



    // --- Helper Methods ---

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
    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<OrderItemResponse> itemResponses = new ArrayList<>();
        for (OrderItem item : items) {
            String ticketTypeName = ticketTypeRepository.findById(item.getTicketTypeId())
                    .map(TicketType::getName).orElse("Unknown Ticket Type");
            String phaseName = ticketSalePhaseRepository.findById(item.getSalePhaseId())
                    .map(TicketSalePhase::getName).orElse("Unknown Phase");
            String seatCode = null;
            if (item.getEventSeatId() != null) {
                seatCode = eventSeatRepository.findById(item.getEventSeatId())
                        .map(EventSeat::getSeatNumber).orElse(null);
            }
            itemResponses.add(OrderItemResponse.of(item, ticketTypeName, phaseName, seatCode));
        }
        return OrderResponse.of(order, itemResponses);
    }
}
