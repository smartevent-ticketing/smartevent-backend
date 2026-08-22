package com.smartevent.modules.ticket.service;

import com.smartevent.modules.ticket.dto.request.TransferTicketRequest;
import com.smartevent.modules.ticket.dto.response.TicketTransferResponse;

import java.util.UUID;

public interface TicketTransferService {

    // Chuyển nhượng quyền sở hữu vé sang tài khoản người khác & thu hồi mã QR cũ
    TicketTransferResponse transferTicket(UUID ticketId, UUID currentUserId, TransferTicketRequest request);
}