package com.smartevent.modules.ordering.repository;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.modules.ordering.entity.Order;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select r.eventId from Reservation r, Order o where o.reservationId = r.id and o.orderCode = :code")
    Optional<UUID> findEventIdByOrderCode(@Param("code") String code);

    @Query("select r.eventId from Reservation r, Order o where o.reservationId = r.id and o.id = :id")
    Optional<UUID> findEventIdByOrderId(@Param("id") UUID id);

    @Query("select o.id from Order o, Reservation r where o.reservationId = r.id and r.eventId = :eventId order by o.id")
    List<UUID> findIdsByEventId(@Param("eventId") UUID eventId);

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByReservationId(UUID reservationId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Order> findByStatusAndPaymentDeadlineBefore(OrderStatus status, Instant deadline);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :code")
    Optional<Order> findByOrderCodeForUpdate(@Param("code") String code);

    boolean existsByOrderCode(String orderCode);
}
