package com.smartevent.modules.ticket.repository;

import com.smartevent.modules.ticket.entity.TicketTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketTransferRepository extends JpaRepository<TicketTransfer, UUID> {

    // 1. Lấy lịch sử chuyển nhượng của một tấm vé
    List<TicketTransfer> findByTicketIdOrderByTransferredAtDesc(UUID ticketId);

    // 2. Lấy danh sách các giao dịch chuyển vé mà người dùng là người gửi hoặc người nhận
    List<TicketTransfer> findByFromUserIdOrToUserIdOrderByTransferredAtDesc(UUID fromUserId, UUID toUserId);
}