package com.smartevent.modules.ticketing.repository;

import com.smartevent.modules.ticketing.entity.InventoryCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryCounterRepository extends JpaRepository<InventoryCounter, UUID> {

    Optional<InventoryCounter> findBySalePhaseId(UUID salePhaseId);

    List<InventoryCounter> findByEventId(UUID eventId);

    boolean existsBySalePhaseId(UUID salePhaseId);

    /**
     * 1. ATOMIC HOLD: Tăng heldQuantity nếu tồn kho khả dụng (total - held - sold) >= quantity.
     * Trả về số dòng cập nhật: 1 = Thành công, 0 = Hết vé (Không đủ tồn kho).
     */
    @Modifying
    @Query("""
        UPDATE InventoryCounter ic
        SET ic.heldQuantity = ic.heldQuantity + :quantity,
            ic.updatedAt = :now
        WHERE ic.salePhaseId = :salePhaseId
          AND (ic.totalQuantity - ic.heldQuantity - ic.soldQuantity) >= :quantity
    """)
    int atomicHoldQuantity(
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 2. ATOMIC RELEASE: Nhả vé tạm giữ khi hết hạn 10 phút hoặc hủy đơn.
     * Trả về: 1 = Thành công, 0 = Thất bại.
     */
    @Modifying
    @Query("""
        UPDATE InventoryCounter ic
        SET ic.heldQuantity = ic.heldQuantity - :quantity,
            ic.updatedAt = :now
        WHERE ic.salePhaseId = :salePhaseId
          AND ic.heldQuantity >= :quantity
    """)
    int atomicReleaseHeldQuantity(
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 3. ATOMIC CONFIRM PURCHASE: Chuyển vé từ Tạm giữ (held) sang Đã bán (sold).
     * Trả về: 1 = Thành công, 0 = Thất bại.
     */
    @Modifying
    @Query("""
        UPDATE InventoryCounter ic
        SET ic.heldQuantity = ic.heldQuantity - :quantity,
            ic.soldQuantity = ic.soldQuantity + :quantity,
            ic.updatedAt = :now
        WHERE ic.salePhaseId = :salePhaseId
          AND ic.heldQuantity >= :quantity
    """)
    int atomicConfirmPurchase(
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 4. ATOMIC REFUND: Giảm sold_quantity khi đơn hàng được hoàn tiền.
     */
    @Modifying
    @Query("""
        UPDATE InventoryCounter ic
        SET ic.soldQuantity = ic.soldQuantity - :quantity,
            ic.updatedAt = :now
        WHERE ic.salePhaseId = :salePhaseId
          AND ic.soldQuantity >= :quantity
    """)
    int atomicProcessRefund(
            @Param("salePhaseId") UUID salePhaseId,
            @Param("quantity") int quantity,
            @Param("now") Instant now
    );

    /**
     * 5. ATOMIC UPDATE TOTAL: Đồng bộ khi Organizer thay đổi số lượng đợt bán.
     */
    @Modifying
    @Query("""
        UPDATE InventoryCounter ic
        SET ic.totalQuantity = :newTotalQuantity,
            ic.updatedAt = :now
        WHERE ic.salePhaseId = :salePhaseId
          AND (ic.heldQuantity + ic.soldQuantity) <= :newTotalQuantity
    """)
    int atomicUpdateTotalQuantity(
            @Param("salePhaseId") UUID salePhaseId,
            @Param("newTotalQuantity") int newTotalQuantity,
            @Param("now") Instant now
    );
}