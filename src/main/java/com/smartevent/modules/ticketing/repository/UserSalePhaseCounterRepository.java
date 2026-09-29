package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.entity.UserSalePhaseCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSalePhaseCounterRepository extends JpaRepository<UserSalePhaseCounter, UUID> {

    Optional<UserSalePhaseCounter> findByUserIdAndSalePhaseId(UUID userId, UUID salePhaseId);

    /**
     * 1. NATIVE UPSERT: Đảm bảo có dòng dữ liệu cho (userId, salePhaseId) mà không bao giờ bị Duplicate Key Exception.
     */
    @Modifying
    @Query(value = """
        INSERT INTO user_sale_phase_counters (id, user_id, sale_phase_id, held_quantity, purchased_quantity, refunded_quantity, updated_at)
        VALUES (gen_random_uuid(), :userId, :salePhaseId, 0, 0, 0, NOW())
        ON CONFLICT (user_id, sale_phase_id) DO NOTHING
    """, nativeQuery = true)
    int upsertUserCounter(@Param("userId") UUID userId, @Param("salePhaseId") UUID salePhaseId);

    /**
     * 2. ATOMIC ANTI-SCALPING HOLD: Tăng held_quantity nếu (held + purchased - refunded + requested) <= maxPerUser.
     * Trả về: 1 = Hợp lệ cho giữ, 0 = Vượt quá hạn mức max_per_user (Chặn đầu cơ).
     */
    @Modifying
    @Query("""
        UPDATE UserSalePhaseCounter uc
        SET uc.heldQuantity = uc.heldQuantity + :quantity,
            uc.updatedAt = :now
        WHERE uc.userId = :userId
          AND uc.salePhaseId = :salePhaseId
          AND (uc.heldQuantity + uc.purchasedQuantity - uc.refundedQuantity + :quantity) <= :maxPerUser
    """)
    int atomicHoldUserQuantity(
            @Param("userId") UUID userId,
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("maxPerUser") int maxPerUser,
            @Param("now") Instant now
    );

    /**
     * 3. ATOMIC RELEASE: Nhả số vé tạm giữ của người dùng.
     */
    @Modifying
    @Query("""
        UPDATE UserSalePhaseCounter uc
        SET uc.heldQuantity = uc.heldQuantity - :quantity,
            uc.updatedAt = :now
        WHERE uc.userId = :userId
          AND uc.salePhaseId = :salePhaseId
          AND uc.heldQuantity >= :quantity
    """)
    int atomicReleaseUserHeldQuantity(
            @Param("userId") UUID userId,
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 4. ATOMIC CONFIRM: Chuyển từ held sang purchased cho người dùng.
     */
    @Modifying
    @Query("""
        UPDATE UserSalePhaseCounter uc
        SET uc.heldQuantity = uc.heldQuantity - :quantity,
            uc.purchasedQuantity = uc.purchasedQuantity + :quantity,
            uc.updatedAt = :now
        WHERE uc.userId = :userId
          AND uc.salePhaseId = :salePhaseId
          AND uc.heldQuantity >= :quantity
    """)
    int atomicConfirmUserPurchase(
            @Param("userId") UUID userId,
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 5. ATOMIC REFUND: Tăng số lượng đã hoàn tiền.
     */
    @Modifying
    @Query("""
        UPDATE UserSalePhaseCounter uc
        SET uc.refundedQuantity = uc.refundedQuantity + :quantity,
            uc.updatedAt = :now
        WHERE uc.userId = :userId
          AND uc.salePhaseId = :salePhaseId
          AND (uc.purchasedQuantity - uc.refundedQuantity) >= :quantity
    """)
    int atomicProcessUserRefund(
            @Param("userId") UUID userId,
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 6. CUMULATIVE PURCHASED: Tổng số vé user đã mua (sau khi trừ vé đã hoàn) trên toàn bộ sự kiện.
     */
    @Query("""
        SELECT COALESCE(SUM(uc.purchasedQuantity - uc.refundedQuantity), 0)
        FROM UserSalePhaseCounter uc
        JOIN TicketSalePhase tsp ON uc.salePhaseId = tsp.id
        JOIN TicketType tt ON tsp.ticketTypeId = tt.id
        WHERE tt.eventId = :eventId
          AND uc.userId = :userId
    """)
    int countPurchasedQuantityByUserIdAndEventId(@Param("userId") UUID userId, @Param("eventId") UUID eventId);

    /**
     * 7. CUMULATIVE HELD: Tổng số vé user đang giữ trong phiên giữ chỗ trên toàn bộ sự kiện.
     */
    @Query("""
        SELECT COALESCE(SUM(uc.heldQuantity), 0)
        FROM UserSalePhaseCounter uc
        JOIN TicketSalePhase tsp ON uc.salePhaseId = tsp.id
        JOIN TicketType tt ON tsp.ticketTypeId = tt.id
        WHERE tt.eventId = :eventId
          AND uc.userId = :userId
    """)
    int countHeldQuantityByUserIdAndEventId(@Param("userId") UUID userId, @Param("eventId") UUID eventId);

    /**
     * 8. CUMULATIVE OCCUPIED: Tổng số vé user đang chiếm dụng (held + purchased - refunded) trên toàn bộ sự kiện.
     */
    @Query("""
        SELECT COALESCE(SUM(uc.purchasedQuantity - uc.refundedQuantity + uc.heldQuantity), 0)
        FROM UserSalePhaseCounter uc
        JOIN TicketSalePhase tsp ON uc.salePhaseId = tsp.id
        JOIN TicketType tt ON tsp.ticketTypeId = tt.id
        WHERE tt.eventId = :eventId
          AND uc.userId = :userId
    """)
    int countOccupiedTicketsByUserIdAndEventId(@Param("userId") UUID userId, @Param("eventId") UUID eventId);
}