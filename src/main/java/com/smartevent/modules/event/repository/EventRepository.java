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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForShare(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") UUID id);

    Optional<Event> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    Page<Event> findByStatusAndEndTimeAfter(EventStatus status, Instant now, Pageable pageable);

    @Query("""
        select e from Event e
        where e.status = com.smartevent.common.enums.EventStatus.PUBLISHED
          and e.endTime > :now
          and (:keyword is null or lower(e.name) like :keyword
               or lower(coalesce(e.description, '')) like :keyword
               or exists (select v from Venue v where v.id = e.venueId and lower(v.name) like :keyword))
          and (:city is null or lower(coalesce(e.city, '')) like :city
               or exists (select v from Venue v where v.id = e.venueId and lower(v.city) like :city))
          and (:categoryId is null or exists (select ec from EventCategory ec
               where ec.id.eventId = e.id and ec.id.categoryId = :categoryId))
    """)
    Page<Event> searchPublished(
            @Param("now") Instant now,
            @Param("keyword") String keyword,
            @Param("city") String city,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );

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
