package com.smartevent.modules.ordering.repository;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.modules.ordering.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByReservationId(UUID reservationId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Order> findByStatusAndPaymentDeadlineBefore(OrderStatus status, Instant deadline);

    boolean existsByOrderCode(String orderCode);
}
