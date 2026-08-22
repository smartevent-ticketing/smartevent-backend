package com.smartevent.modules.event.repository;

import com.smartevent.common.enums.SeatStatus;
import com.smartevent.modules.event.entity.EventSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventSeatRepository extends JpaRepository<EventSeat, UUID> {

    List<EventSeat> findByEventAreaIdOrderByRowNameAscSeatNumberAsc(UUID eventAreaId);

    Page<EventSeat> findByEventAreaId(UUID eventAreaId, Pageable pageable);

    List<EventSeat> findByEventAreaIdAndStatus(UUID eventAreaId, SeatStatus status);

    boolean existsByEventAreaIdAndRowNameAndSeatNumber(UUID eventAreaId, String rowName, String seatNumber);

    long countByEventAreaId(UUID eventAreaId);

    long countByEventAreaIdAndStatus(UUID eventAreaId, SeatStatus status);

    void deleteByEventAreaId(UUID eventAreaId);

    // 🔥 ATOMIC CONDITIONAL UPDATE: Đổi trạng thái ghế nguyên tử, chống Race Condition 100%
    @Modifying
    @Query("UPDATE EventSeat s SET s.status = :toStatus WHERE s.id = :seatId AND s.status = :fromStatus")
    int updateSeatStatusAtomic(
            @Param("seatId") UUID seatId,
            @Param("fromStatus") SeatStatus fromStatus,
            @Param("toStatus") SeatStatus toStatus
    );
}
