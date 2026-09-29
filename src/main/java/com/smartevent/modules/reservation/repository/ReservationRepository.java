package com.smartevent.modules.reservation.repository;

import com.smartevent.common.enums.ReservationStatus;
import com.smartevent.modules.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    /**
     * Serializes reservation creation for one buyer and event until the surrounding transaction commits.
     * A hash collision can only cause extra waiting; it cannot allow an over-limit purchase.
     * Selecting a constant avoids mapping PostgreSQL's void return type through Hibernate.
     */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:lockKey, 0))", nativeQuery = true)
    Integer lockBuyerEvent(@Param("lockKey") String lockKey);

    @org.springframework.data.jpa.repository.Query("select r.eventId from Reservation r where r.id = :id")
    Optional<UUID> findEventIdById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Reservation> findByEventIdAndStatus(UUID eventId, ReservationStatus status);

    List<Reservation> findByUserId(UUID userId);

    Optional<Reservation> findByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ReservationStatus status);

    boolean existsByUserIdAndEventIdAndStatus(UUID userId, UUID eventId, ReservationStatus status);

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    // Tìm các phiên PENDING đã quá hạn để tự động quét nhả vé
    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    // 🔥 ATOMIC CAS: Chỉ chuyển trạng thái nếu trạng thái hiện tại đúng là fromStatus
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Reservation r SET r.status = :toStatus WHERE r.id = :id AND r.status = :fromStatus")
    int updateStatusAtomic(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("fromStatus") ReservationStatus fromStatus,
            @org.springframework.data.repository.query.Param("toStatus") ReservationStatus toStatus
    );
}
