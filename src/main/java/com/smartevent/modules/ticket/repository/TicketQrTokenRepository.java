package com.smartevent.modules.ticket.repository;

import com.smartevent.modules.ticket.entity.TicketQrToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketQrTokenRepository extends JpaRepository<TicketQrToken, UUID> {

    @org.springframework.data.jpa.repository.Query("select q.ticketId from TicketQrToken q where q.tokenHash = :token")
    Optional<UUID> findTicketIdByTokenHash(@org.springframework.data.repository.query.Param("token") String token);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update TicketQrToken q set q.status = 'REVOKED', q.revokedAt = :now where q.ticketId = :id and q.status = 'ACTIVE'")
    int revokeActiveTokens(@org.springframework.data.repository.query.Param("id") UUID id,
                           @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    // 1. Lấy token QR đang ACTIVE mới nhất của tấm vé
    Optional<TicketQrToken> findFirstByTicketIdAndStatusOrderByIssuedAtDesc(UUID ticketId, String status);

    // 2. Tìm bản ghi QR Token theo chuỗi hash được quét từ máy quét tại cổng
    Optional<TicketQrToken> findByTokenHash(String tokenHash);

    // 3. Lấy toàn bộ lịch sử các mã QR đã từng sinh cho tấm vé
    List<TicketQrToken> findByTicketId(UUID ticketId);
}
