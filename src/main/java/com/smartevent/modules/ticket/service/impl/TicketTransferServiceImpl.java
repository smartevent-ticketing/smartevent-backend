package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.enums.TicketStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.util.TicketSecurityUtils;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.ticket.dto.request.TransferTicketRequest;
import com.smartevent.modules.ticket.dto.response.TicketTransferResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.entity.TicketTransfer;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.repository.TicketTransferRepository;
import com.smartevent.modules.ticket.service.TicketTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTransferServiceImpl implements TicketTransferService {

    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository qrTokenRepository;
    private final TicketTransferRepository transferRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TicketTransferResponse transferTicket(UUID ticketId, UUID currentUserId, TransferTicketRequest request) {
        log.info("Yêu cầu chuyển nhượng vé ID {} từ User ID {} sang Email {}", ticketId, currentUserId, request.recipientEmail());

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé"));

        // 1. Kiểm tra quyền sở hữu
        if (!ticket.getCurrentOwnerUserId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.ACCESS_DENIED, "Bạn không phải chủ sở hữu tấm vé này");
        }

        // 2. Kiểm tra trạng thái vé có được phép chuyển không (Chỉ ISSUED mới được chuyển)
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw new TicketException(ErrorCode.TICKET_NOT_TRANSFERABLE, "Vé đã sử dụng hoặc đã bị hủy, không thể chuyển nhượng");
        }

        // 3. Tìm tài khoản người nhận theo Email
        User recipient = userRepository.findByEmail(request.recipientEmail().trim().toLowerCase())
                .orElseThrow(() -> new TicketException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy tài khoản người nhận với email: " + request.recipientEmail()));

        if (recipient.getId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.BUSINESS_RULE_VIOLATION, "Không thể tự chuyển nhượng vé cho chính mình");
        }

        // 4. Thu hồi toàn bộ mã QR Token cũ của người bán (REVOKED)
        List<TicketQrToken> oldTokens = qrTokenRepository.findByTicketId(ticketId);
        for (TicketQrToken oldToken : oldTokens) {
            if (oldToken.isActive()) {
                oldToken.setStatus("REVOKED");
                oldToken.setRevokedAt(Instant.now());
                qrTokenRepository.save(oldToken);
            }
        }

        // 5. Chuyển quyền sở hữu vé sang người nhận mới
        ticket.setCurrentOwnerUserId(recipient.getId());
        ticketRepository.save(ticket);

        // 6. Sinh mã Token QR mới tinh cho người nhận
        String newQrHash = TicketSecurityUtils.generateSecureQrToken(ticketId, recipient.getId());
        TicketQrToken newQrToken = new TicketQrToken(ticketId, newQrHash);
        qrTokenRepository.save(newQrToken);

        // 7. Lưu bản ghi lịch sử chuyển nhượng
        TicketTransfer transfer = new TicketTransfer(
                ticketId,
                currentUserId,
                recipient.getId(),
                "DIRECT_TRANSFER",
                null
        );
        TicketTransfer savedTransfer = transferRepository.save(transfer);

        log.info("Chuyển nhượng thành công vé {} sang cho {}", ticket.getTicketCode(), recipient.getEmail());

        return new TicketTransferResponse(
                savedTransfer.getId(),
                ticket.getId(),
                ticket.getTicketCode(),
                currentUserId,
                recipient.getId(),
                recipient.getEmail(),
                savedTransfer.getStatus(),
                savedTransfer.getTransferredAt()
        );
    }
}