package com.smartevent.modules.ticket.repository;

import com.smartevent.common.enums.CheckinResult;
import com.smartevent.modules.ticket.entity.TicketCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketCheckinRepository extends JpaRepository<TicketCheckin, UUID> {

    // 1. Xem lịch sử quét của một tấm vé (biết vé đã vào lúc mấy giờ, cổng nào)
    List<TicketCheckin> findByTicketIdOrderByCheckedAtDesc(UUID ticketId);

    // 2. Xem toàn bộ lượt check-in của một sự kiện
    List<TicketCheckin> findByEventIdOrderByCheckedAtDesc(UUID eventId);

    // 3. Đếm số lượng quét theo kết quả (VD: đếm tổng số lượt check-in SUCCESS)
    long countByEventIdAndResult(UUID eventId, CheckinResult result);
}