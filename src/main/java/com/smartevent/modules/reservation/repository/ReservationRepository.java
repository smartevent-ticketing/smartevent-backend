package com.smartevent.modules.reservation.repository;

import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.modules.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByUserId(UUID userId);

    Optional<Reservation> findByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ReservationStatus status);

    boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ReservationStatus status);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    // Tìm các phiên PENDING đã quá hạn để tự động quét nhả vé
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);
}