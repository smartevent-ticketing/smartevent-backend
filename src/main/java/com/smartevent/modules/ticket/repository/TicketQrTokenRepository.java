package com.smartevent.modules.ticket.repository;

import com.smartevent.modules.ticket.entity.TicketQrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketQrTokenRepository extends JpaRepository<TicketQrToken, UUID> {

    // 1. Lấy token QR đang ACTIVE mới nhất của tấm vé
    Optional<TicketQrToken> findFirstByTicketIdAndStatusOrderByIssuedAtDesc(UUID ticketId, String status);

    // 2. Tìm bản ghi QR Token theo chuỗi hash được quét từ máy quét tại cổng
    Optional<TicketQrToken> findByTokenHash(String tokenHash);

    // 3. Lấy toàn bộ lịch sử các mã QR đã từng sinh cho tấm vé
    List<TicketQrToken> findByTicketId(UUID ticketId);
}