package com.smartevent.ticketing.modules.event.repository;

import com.smartevent.ticketing.common.enums.SeatStatus;
import com.smartevent.ticketing.modules.event.entity.EventSeat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}