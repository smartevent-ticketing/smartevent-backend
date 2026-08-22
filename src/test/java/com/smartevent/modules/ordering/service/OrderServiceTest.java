package com.smartevent.modules.ordering.service;

import com.smartevent.common.enums.*;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.ordering.dto.request.CreateOrderRequest;
import com.smartevent.modules.ordering.dto.response.OrderResponse;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.exception.OrderingException;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ordering.service.impl.OrderServiceImpl;
import com.smartevent.modules.reservation.entity.Reservation;
import com.smartevent.modules.reservation.entity.ReservationItem;
import com.smartevent.modules.reservation.repository.ReservationItemRepository;
import com.smartevent.modules.reservation.repository.ReservationRepository;
import com.smartevent.modules.reservation.service.ReservationService;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private ReservationItemRepository reservationItemRepository;
    @Mock private ReservationService reservationService;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private TicketSalePhaseRepository ticketSalePhaseRepository;
    @Mock private EventSeatRepository eventSeatRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private UUID userId;
    private UUID reservationId;
    private Reservation validReservation;
    private ReservationItem item1;
    private ReservationItem item2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        reservationId = UUID.randomUUID();

        validReservation = new Reservation(userId, UUID.randomUUID(), Instant.now().plus(10, ChronoUnit.MINUTES), "idemp-1");
        validReservation.setId(reservationId);
        validReservation.setStatus(ReservationStatus.PENDING);

        item1 = new ReservationItem(reservationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, BigDecimal.valueOf(500000));
        item2 = new ReservationItem(reservationId, UUID.randomUUID(), UUID.randomUUID(), null, 2, BigDecimal.valueOf(300000));
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công và đóng băng đúng tổng giá tiền (1.100.000 VNĐ)")
    void createOrder_Success() {
        CreateOrderRequest request = new CreateOrderRequest(reservationId, "Ghi chu", PaymentMethod.VNPAY);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(validReservation));
        when(orderRepository.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(reservationItemRepository.findByReservationId(reservationId)).thenReturn(List.of(item1, item2));
        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });

        OrderResponse response = orderService.createOrderFromReservation(userId, request);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(1100000), response.totalAmount());
        assertEquals(OrderStatus.PENDING_PAYMENT, response.status());
        verify(orderItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Tạo đơn thất bại khi không tìm thấy phiên giữ chỗ")
    void createOrder_ReservationNotFound() {
        CreateOrderRequest request = new CreateOrderRequest(reservationId, null, PaymentMethod.VNPAY);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        OrderingException ex = assertThrows(OrderingException.class,
                () -> orderService.createOrderFromReservation(userId, request));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tạo đơn thất bại khi người tạo không phải chủ sở hữu phiên giữ chỗ")
    void createOrder_AccessDenied() {
        UUID otherUserId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(reservationId, null, PaymentMethod.VNPAY);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(validReservation));

        OrderingException ex = assertThrows(OrderingException.class,
                () -> orderService.createOrderFromReservation(otherUserId, request));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Tạo đơn thất bại khi phiên giữ chỗ đã hết hạn 10 phút")
    void createOrder_ReservationExpired() {
        validReservation.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        CreateOrderRequest request = new CreateOrderRequest(reservationId, null, PaymentMethod.VNPAY);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(validReservation));

        OrderingException ex = assertThrows(OrderingException.class,
                () -> orderService.createOrderFromReservation(userId, request));
        assertEquals(ErrorCode.ORDER_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khách chủ động hủy đơn hàng đang chờ thanh toán -> Thành công và nhả vé")
    void cancelOrder_Success() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, reservationId, "ORD-123", BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500000), Instant.now().plusSeconds(300), null, PaymentMethod.VNPAY);
        order.setId(orderId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        orderService.cancelOrder(orderId, userId, false);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(reservationService, times(1)).cancelReservation(reservationId, userId, false);
    }

    @Test
    @DisplayName("Không cho phép hủy đơn hàng đã thanh toán (PAID)")
    void cancelOrder_InvalidStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order(userId, reservationId, "ORD-123", BigDecimal.valueOf(500000), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(500000), Instant.now().plusSeconds(300), null, PaymentMethod.VNPAY);
        order.setId(orderId);
        order.setStatus(OrderStatus.PAID);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderingException ex = assertThrows(OrderingException.class,
                () -> orderService.cancelOrder(orderId, userId, false));
        assertEquals(ErrorCode.ORDER_INVALID_STATUS, ex.getErrorCode());
    }
}