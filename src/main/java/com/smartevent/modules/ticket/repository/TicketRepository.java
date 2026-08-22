package com.smartevent.modules.ticket.repository;

import com.smartevent.common.enums.TicketStatus;
import com.smartevent.modules.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    // 1. Tìm vé theo mã hiển thị duy nhất (dùng khi check-in hoặc tra cứu)
    Optional<Ticket> findByTicketCode(String ticketCode);

    // 2. Lấy danh sách ví vé của người dùng hiện tại (mới nhất lên đầu)
    List<Ticket> findByCurrentOwnerUserIdOrderByCreatedAtDesc(UUID currentOwnerUserId);

    // 3. Lấy danh sách vé của người dùng trong một sự kiện cụ thể
    List<Ticket> findByCurrentOwnerUserIdAndEventId(UUID currentOwnerUserId, UUID eventId);

    // 4. Lấy tất cả vé thuộc một mục chi tiết đơn hàng
    List<Ticket> findByOrderItemId(UUID orderItemId);

    // 5. Kiểm tra mã vé đã tồn tại trong DB chưa (chống trùng khi sinh mã)
    boolean existsByTicketCode(String ticketCode);

    // 6. Đếm số lượng vé theo trạng thái trong sự kiện (VD: bao nhiêu vé ISSUED, bao nhiêu vé USED)
    long countByEventIdAndStatus(UUID eventId, TicketStatus status);

    // 🔥 ATOMIC CONDITIONAL UPDATE: Chỉ chuyển vé ISSUED -> USED nếu chưa từng bị quét
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Ticket t SET t.status = com.smartevent.common.enums.TicketStatus.USED, t.usedAt = :usedAt WHERE t.id = :ticketId AND t.status = com.smartevent.common.enums.TicketStatus.ISSUED")
    int markTicketAsUsedAtomic(
            @org.springframework.data.repository.query.Param("ticketId") UUID ticketId,
            @org.springframework.data.repository.query.Param("usedAt") java.time.Instant usedAt
    );
}
