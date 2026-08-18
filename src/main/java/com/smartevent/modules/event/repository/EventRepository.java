package com.smartevent.modules.event.repository;

import com.smartevent.common.enums.EventStatus;
import com.smartevent.modules.event.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    // Kiểm tra xem Venue đã có sự kiện nào khác diễn ra trong khoảng thời gian này chưa
    @Query("""
        SELECT COUNT(e) > 0 FROM Event e
        WHERE e.venueId = :venueId
          AND e.status IN (com.smartevent.common.enums.EventStatus.PUBLISHED, com.smartevent.common.enums.EventStatus.PENDING_APPROVAL)
          AND (:excludeEventId IS NULL OR e.id != :excludeEventId)
          AND (e.startTime < :endTime AND e.endTime > :startTime)
    """)
    boolean hasVenueTimeConflict(
            @Param("venueId") UUID venueId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludeEventId") UUID excludeEventId
    );
}
