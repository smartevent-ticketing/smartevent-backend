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
          and (cast(:categoryId as java.util.UUID) is null or exists (select ec from EventCategory ec
               where ec.id.eventId = e.id and ec.id.categoryId = :categoryId))
    """)
    Page<Event> searchPublished(
            @Param("now") Instant now,
            @Param("keyword") String keyword,
            @Param("city") String city,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );

    @Query("""
        select e from Event e
        where e.status = com.smartevent.common.enums.EventStatus.PUBLISHED
          and e.endTime > :now
          and (:keyword is null or lower(e.name) like :keyword
               or lower(coalesce(e.description, '')) like :keyword
               or exists (select v from Venue v where v.id = e.venueId and lower(v.name) like :keyword))
          and (:city is null or lower(coalesce(e.city, '')) like :city
               or exists (select v from Venue v where v.id = e.venueId and lower(v.city) like :city))
          and (cast(:categoryId as java.util.UUID) is null or exists (select ec from EventCategory ec
               where ec.id.eventId = e.id and ec.id.categoryId = :categoryId))
          and (cast(:startFrom as java.time.Instant) is null or e.startTime >= :startFrom)
          and (cast(:startBefore as java.time.Instant) is null or e.startTime < :startBefore)
          and (cast(:maxPrice as java.math.BigDecimal) is null or exists (
            select p.id from TicketType t, TicketSalePhase p, InventoryCounter ic
            where t.eventId = e.id and p.ticketTypeId = t.id and ic.salePhaseId = p.id
              and t.status = com.smartevent.common.enums.TicketTypeStatus.ACTIVE
              and p.status = com.smartevent.common.enums.SalePhaseStatus.ACTIVE
              and p.soldOutAt is null and p.saleStartAt <= :now and p.saleEndAt >= :now
              and (ic.totalQuantity - ic.heldQuantity - ic.soldQuantity) > 0
              and ((:comparison = 'LT' and p.price < :maxPrice)
                or (:comparison = 'LTE' and p.price <= :maxPrice))))
    """)
    Page<Event> searchPublishedFiltered(@Param("now") Instant now,
        @Param("keyword") String keyword, @Param("city") String city,
        @Param("categoryId") UUID categoryId, @Param("startFrom") Instant startFrom,
        @Param("startBefore") Instant startBefore, @Param("maxPrice") java.math.BigDecimal maxPrice,
        @Param("comparison") String comparison, Pageable pageable);

    @Query("""
        select new com.smartevent.modules.event.dto.response.PriceEvidence(t.eventId, t.id, p.id, p.price)
        from TicketType t, TicketSalePhase p, InventoryCounter ic
        where t.eventId in :eventIds and p.ticketTypeId = t.id and ic.salePhaseId = p.id
          and t.status = com.smartevent.common.enums.TicketTypeStatus.ACTIVE
          and p.status = com.smartevent.common.enums.SalePhaseStatus.ACTIVE
          and p.soldOutAt is null and p.saleStartAt <= :now and p.saleEndAt >= :now
          and (ic.totalQuantity - ic.heldQuantity - ic.soldQuantity) > 0
          and (cast(:maxPrice as java.math.BigDecimal) is null or (:comparison = 'LT' and p.price < :maxPrice)
            or (:comparison = 'LTE' and p.price <= :maxPrice))
        order by p.price asc, p.id asc
    """)
    java.util.List<com.smartevent.modules.event.dto.response.PriceEvidence> findPriceEvidence(
        @Param("eventIds") java.util.List<UUID> eventIds, @Param("now") Instant now,
        @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("comparison") String comparison);

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
