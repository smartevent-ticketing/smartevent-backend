package com.smartevent.modules.reservation.repository;

import com.smartevent.modules.reservation.entity.ReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationItemRepository extends JpaRepository<ReservationItem, UUID> {

    List<ReservationItem> findByReservationId(UUID reservationId);

    void deleteByReservationId(UUID reservationId);

    Optional<ReservationItem> findByEventSeatId(UUID eventSeatId);
}