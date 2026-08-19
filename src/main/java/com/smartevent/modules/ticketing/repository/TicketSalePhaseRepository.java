package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketSalePhaseRepository extends JpaRepository<TicketSalePhase, UUID> {

    List<TicketSalePhase> findByTicketTypeId(UUID ticketTypeId);

    List<TicketSalePhase> findByTicketTypeIdIn(List<UUID> ticketTypeIds);

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
}