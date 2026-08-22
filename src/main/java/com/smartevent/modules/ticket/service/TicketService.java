package com.smartevent.modules.ticket.service;

import com.smartevent.modules.ticket.dto.response.TicketResponse;

import java.util.List;
import java.util.UUID;

public interface TicketService {

    // 1. Tự động phát hành danh sách vé đơn lẻ khi Order chuyển trạng thái PAID
    List<TicketResponse> issueTicketsForOrder(UUID orderId);

    // 2. Lấy thông tin chi tiết một tấm vé kèm ảnh mã QR Base64
    TicketResponse getTicketById(UUID ticketId, UUID currentUserId, boolean isAdmin);

    // 3. Lấy toàn bộ vé trong "Ví vé" của người dùng hiện tại
    List<TicketResponse> getMyTickets(UUID currentUserId);

    // 4. Ban tổ chức / Admin xem toàn bộ vé đã phát hành của một sự kiện
    List<TicketResponse> getTicketsByEvent(UUID eventId, UUID currentUserId, boolean isAdmin);

    // 5. Làm mới mã QR Token bảo mật (đổi mã băm)
    TicketResponse refreshTicketQr(UUID ticketId, UUID currentUserId);
}