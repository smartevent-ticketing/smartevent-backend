package com.smartevent.modules.ordering.service;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.reservation.service.ReservationService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {
    @Mock OrderRepository orders;
    @Mock ReservationService reservations;
    OrderLifecycleService service;
    Order order;

    @BeforeEach
    void setUp() {
        service = new OrderLifecycleService(orders, reservations, mock(com.smartevent.modules.event.repository.EventRepository.class));
        order = new Order();
        order.setId(UUID.randomUUID());
        order.setReservationId(UUID.randomUUID());
        order.setPaymentDeadline(Instant.now().plusSeconds(60));
    }

    @Test
    void expiryUsesStateReadUnderLockAndPreservesPaidOrder() {
        order.setStatus(OrderStatus.PAID);
        order.setPaymentDeadline(Instant.now().minusSeconds(60));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        service.expireOrder(order.getId());
        assertEquals(OrderStatus.PAID, order.getStatus());
        verify(orders, never()).save(any());
        verify(orders, never()).findById(any());
    }

    @Test
    void expiryDoesNotExpireAnOrderBeforeItsDeadline() {
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        service.expireOrder(order.getId());
        assertEquals(OrderStatus.PENDING_PAYMENT, order.getStatus());
        verify(orders, never()).save(any());
    }

    @Test
    void successfulConfirmationMarksOrderPaid() {
        when(reservations.confirmReservation(order.getReservationId())).thenReturn(true);
        assertTrue(service.completePayment(order, "txn"));
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void cancelledOrderCannotBeReopenedBySuccessfulPayment() {
        order.setStatus(OrderStatus.CANCELLED);
        assertFalse(service.completePayment(order, "late-txn"));
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertTrue(order.getCustomerNote().contains("late-txn"));
        verifyNoInteractions(reservations);
    }

    @Test
    void duplicateCompletionDoesNotCancelPaidOrder() {
        order.setStatus(OrderStatus.PAID);
        assertFalse(service.completePayment(order, "duplicate"));
        assertEquals(OrderStatus.PAID, order.getStatus());
        verifyNoInteractions(reservations, orders);
    }
}
