package com.smartevent.modules.ordering.service;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.reservation.service.ReservationService;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final ReservationService reservationService;
    private final com.smartevent.modules.event.repository.EventRepository eventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> lockForPayment(String orderCode) {
        var eventId = orderRepository.findEventIdByOrderCode(orderCode);
        if (eventId.isEmpty()) return Optional.empty();
        eventRepository.findByIdForShare(eventId.get()).orElseThrow();
        return orderRepository.findByOrderCodeForUpdate(orderCode);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Order> lockForPayment(UUID orderId) {
        var eventId = orderRepository.findEventIdByOrderId(orderId);
        if (eventId.isEmpty()) return Optional.empty();
        eventRepository.findByIdForShare(eventId.get()).orElseThrow();
        return orderRepository.findByIdForUpdate(orderId);
    }

    public void requirePayableReservation(Order order) {
        if (order.getReservationId() == null || !reservationService.isPayable(order.getReservationId())) {
            throw new OrderingException(ErrorCode.ORDER_INVALID_STATUS, "Phiên giữ vé hoặc sự kiện không còn hợp lệ để thanh toán");
        }
    }

    /** Called while cancellation holds the event write lock. Paid orders retain their financial status. */
    @Transactional(propagation = Propagation.MANDATORY)
    public java.util.List<UUID> cancelForEvent(UUID eventId) {
        java.util.List<UUID> paidOrders = new java.util.ArrayList<>();
        for (UUID id : orderRepository.findIdsByEventId(eventId)) {
            Order order = orderRepository.findByIdForUpdate(id).orElseThrow();
            if (order.isPendingPayment()) {
                order.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(order);
            } else if (order.isPaid()) {
                paidOrders.add(id);
            }
        }
        return paidOrders;
    }

    @Transactional
    public void cancelOrder(UUID orderId, UUID currentUserId, boolean isAdmin) {

        Order order = orderRepository.findByIdForUpdate(orderId)
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

    @Transactional
    public void expireOrder(UUID orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order != null && order.getStatus() == OrderStatus.PENDING_PAYMENT && order.isExpired()) {
            order.setStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            log.info("Đơn hàng {} đã hết hạn thanh toán 10 phút, chuyển trạng thái sang EXPIRED", order.getOrderCode());
        }
    }

    /** The caller holds the order lock for the entire payment transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean completePayment(Order order, String transactionNo) {
        if (order.isPaid()) return false;
        boolean confirmed = order.isPendingPayment() && !order.isExpired() && order.getReservationId() != null;
        if (confirmed && order.getReservationId() != null) {
            confirmed = reservationService.confirmReservation(order.getReservationId());
        }
        if (confirmed) {
            order.setStatus(OrderStatus.PAID);
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCustomerNote("LATE_PAYMENT_EXPIRED: VNPay đã trừ tiền thành công (Mã GD: " + transactionNo
                    + ") nhưng đơn hàng hoặc phiên giữ chỗ không còn hợp lệ. Cần đối soát hoàn tiền.");
        }
        orderRepository.save(order);
        return confirmed;
    }
}
