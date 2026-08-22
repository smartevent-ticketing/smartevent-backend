package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.enums.CheckinResult;
import com.smartevent.common.enums.TicketStatus;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.ticket.dto.request.CheckinRequest;
import com.smartevent.modules.ticket.dto.response.CheckinResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketCheckin;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.repository.TicketCheckinRepository;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.service.CheckinService;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository qrTokenRepository;
    private final TicketCheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final com.smartevent.modules.event.repository.EventRepository eventRepository;

    @Override
    @Transactional
    public CheckinResponse processCheckin(CheckinRequest request, UUID staffUserId) {
        String input = request.ticketCodeOrToken().trim();
        String gate = request.gateName() != null ? request.gateName() : "Cổng chính";
        log.info("Nhân viên ID {} đang quét mã vé: [{}] tại cổng [{}]", staffUserId, input, gate);

        // 0. Kiểm tra sự tồn tại của Event
        var event = eventRepository.findById(request.eventId())
                .orElseThrow(() -> new com.smartevent.modules.ticket.exception.TicketException(
                        com.smartevent.common.error.ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));

        // 1. Phân giải: Input có thể là mã vé cố định (TCK-...) hoặc chuỗi QR Token Hash
        Optional<Ticket> ticketOpt = Optional.empty();

        if (input.startsWith("TCK-QR.")) {
            // Quét từ mã QR động
            Optional<TicketQrToken> tokenOpt = qrTokenRepository.findByTokenHash(input);
            if (tokenOpt.isEmpty() || !"ACTIVE".equalsIgnoreCase(tokenOpt.get().getStatus())) {
                log.warn("Mã QR Token không tồn tại hoặc đã bị thu hồi (REVOKED): {}", input);
                return CheckinResponse.invalid("MÃ QR NÀY ĐÃ BỊ THU HỒI HOẶC KHÔNG HỢP LỆ!", gate);
            }
            ticketOpt = ticketRepository.findById(tokenOpt.get().getTicketId());
        } else {
            // Quét hoặc gõ từ mã vé cố định (TCK-20260822-XXXX)
            ticketOpt = ticketRepository.findByTicketCode(input);
        }

        if (ticketOpt.isEmpty()) {
            log.warn("Không tìm thấy tấm vé tương ứng với input: {}", input);
            return CheckinResponse.invalid("MÃ VÉ KHÔNG TỒN TẠI TRÊN HỆ THỐNG!", gate);
        }

        Ticket ticket = ticketOpt.get();

        // 2. Kiểm tra đối soát Event ID
        if (!ticket.getEventId().equals(request.eventId())) {
            log.warn("Vé ID {} thuộc sự kiện {} nhưng lại quét tại sự kiện {}", ticket.getId(), ticket.getEventId(), request.eventId());
            recordCheckinLog(ticket.getId(), request.eventId(), staffUserId, gate, CheckinResult.INVALID);
            return CheckinResponse.invalid("VÉ NÀY KHÔNG THUỘC SỰ KIỆN NÀY!", gate);
        }

        // 3. Kiểm tra Duplicate (Vé đã quét sử dụng trước đó)
        if (ticket.getStatus() == TicketStatus.USED) {
            String usedTimeStr = ticket.getUsedAt() != null
                    ? DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(ticket.getUsedAt())
                    : "Trước đó";
            log.warn("CẢNH BÁO: Vé ID {} đã được sử dụng lúc {}!", ticket.getId(), usedTimeStr);

            recordCheckinLog(ticket.getId(), request.eventId(), staffUserId, gate, CheckinResult.DUPLICATE);
            String ownerName = getAttendeeName(ticket.getCurrentOwnerUserId());
            return CheckinResponse.duplicate(UUID.randomUUID(), ticket.getId(), ticket.getTicketCode(), gate, ownerName, usedTimeStr);
        }

        // 4. Kiểm tra trạng thái không hợp lệ khác (CANCELLED, REFUNDED, TRANSFERRED)
        if (ticket.getStatus() != TicketStatus.ISSUED) {
            log.warn("Vé ID {} ở trạng thái không hợp lệ: {}", ticket.getId(), ticket.getStatus());
            recordCheckinLog(ticket.getId(), request.eventId(), staffUserId, gate, CheckinResult.INVALID);
            return CheckinResponse.invalid("VÉ ĐÃ BỊ HỦY HOẶC ĐÃ HOÀN TIỀN!", gate);
        }

        // 5. 🔥 ATOMIC CONDITIONAL UPDATE: Chỉ chuyển ISSUED -> USED nếu chưa từng bị quét
        Instant scanTime = Instant.now();
        int affectedRows = ticketRepository.markTicketAsUsedAtomic(ticket.getId(), scanTime);
        if (affectedRows == 0) {
            // Có máy quét khác vừa nhanh tay quét trước đúng mili-giây này!
            recordCheckinLog(ticket.getId(), request.eventId(), staffUserId, gate, CheckinResult.DUPLICATE);
            String ownerName = getAttendeeName(ticket.getCurrentOwnerUserId());
            return CheckinResponse.duplicate(UUID.randomUUID(), ticket.getId(), ticket.getTicketCode(), gate, ownerName, "Vừa quét lúc " + scanTime);
        }

        TicketCheckin checkinLog = recordCheckinLog(ticket.getId(), request.eventId(), staffUserId, gate, CheckinResult.SUCCESS);

        String attendeeName = getAttendeeName(ticket.getCurrentOwnerUserId());
        String seatCode = ticket.getEventSeatId() != null
                ? eventSeatRepository.findById(ticket.getEventSeatId()).map(EventSeat::getSeatNumber).orElse("Ghế tự do") : "Vé đứng";
        String ticketTypeName = ticket.getTicketTypeId() != null
                ? ticketTypeRepository.findById(ticket.getTicketTypeId()).map(TicketType::getName).orElse("General") : "General";

        log.info("Check-in THÀNH CÔNG cho khán giả {} (Vé: {}) tại {}", attendeeName, ticket.getTicketCode(), gate);
        return CheckinResponse.success(checkinLog.getId(), ticket.getId(), ticket.getTicketCode(), gate, attendeeName, seatCode, ticketTypeName);
    }

    private TicketCheckin recordCheckinLog(UUID ticketId, UUID eventId, UUID staffId, String gate, CheckinResult result) {
        TicketCheckin checkin = new TicketCheckin(ticketId, eventId, staffId, gate, result);
        return checkinRepository.save(checkin);
    }

    private String getAttendeeName(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Khách hàng");
    }
}