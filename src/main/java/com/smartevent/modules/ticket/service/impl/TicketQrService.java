package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.enums.TicketStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.util.TicketSecurityUtils;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketQrService {

    private final TicketMutationGuard mutationGuard;
    private final TicketCredentialService credentialService;
    private final TicketQueryService ticketQueryService;

    @Transactional
    public TicketResponse refreshTicketQr(UUID ticketId, UUID currentUserId) {
        Ticket ticket = mutationGuard.lockUsableEventTicket(ticketId);

        if (!ticket.getCurrentOwnerUserId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.ACCESS_DENIED, "Chỉ chủ sở hữu mới có quyền làm mới mã QR");
        }

        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw new TicketException(ErrorCode.TICKET_INVALID_STATUS, "Vé không ở trạng thái hợp lệ để đổi mã QR");
        }

        credentialService.rotate(ticket);

        return ticketQueryService.toResponse(ticket);
    }
}
