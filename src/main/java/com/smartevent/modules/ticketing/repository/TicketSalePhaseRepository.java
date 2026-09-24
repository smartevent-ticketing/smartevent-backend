package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smartevent.common.enums.SalePhaseStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TicketSalePhaseRepository extends JpaRepository<TicketSalePhase, UUID> {

    List<TicketSalePhase> findByTicketTypeId(UUID ticketTypeId);

    List<TicketSalePhase> findByTicketTypeIdOrderBySaleStartAtAscIdAsc(UUID ticketTypeId);

    List<TicketSalePhase> findByTicketTypeIdIn(List<UUID> ticketTypeIds);

    List<TicketSalePhase> findByTicketTypeIdInOrderBySaleStartAtAscIdAsc(List<UUID> ticketTypeIds);

    List<TicketSalePhase> findByStatus(SalePhaseStatus status);

    List<TicketSalePhase> findByStatusAndSaleEndAtBefore(SalePhaseStatus status, Instant now);

    List<TicketSalePhase> findByStatusAndSaleStartAtLessThanEqualAndSaleEndAtAfter(
            SalePhaseStatus status, Instant start, Instant end
    );

    boolean existsByTicketTypeId(UUID ticketTypeId);

    // Tính tổng số lượng vé đã cấu hình trên toàn bộ Khán đài (loại trừ đợt đang sửa)
    @Query("""
        SELECT COALESCE(SUM(sp.quantity), 0) FROM TicketSalePhase sp
        JOIN TicketType tt ON sp.ticketTypeId = tt.id
        WHERE tt.eventAreaId = :eventAreaId
          AND (:excludePhaseId IS NULL OR sp.id != :excludePhaseId)
    """)
    int sumQuantityByEventAreaIdExcluding(
            @Param("eventAreaId") UUID eventAreaId,
            @Param("excludePhaseId") UUID excludePhaseId
    );

    // Kiểm tra chồng lấn thời gian mở bán [saleStartAt, saleEndAt) cùng hạng vé
    @Query("""
        SELECT COUNT(sp) > 0 FROM TicketSalePhase sp
        WHERE sp.ticketTypeId = :ticketTypeId
          AND (:excludePhaseId IS NULL OR sp.id != :excludePhaseId)
          AND sp.saleStartAt < :saleEndAt
          AND sp.saleEndAt > :saleStartAt
    """)
    boolean existsOverlappingPhase(
            @Param("ticketTypeId") UUID ticketTypeId,
            @Param("excludePhaseId") UUID excludePhaseId,
            @Param("saleStartAt") Instant saleStartAt,
            @Param("saleEndAt") Instant saleEndAt
    );

    // Đếm số đợt mở bán của một sự kiện
    @Query("""
        SELECT COUNT(sp) FROM TicketSalePhase sp
        JOIN TicketType tt ON sp.ticketTypeId = tt.id
        WHERE tt.eventId = :eventId
    """)
    long countByEventId(@Param("eventId") UUID eventId);
}