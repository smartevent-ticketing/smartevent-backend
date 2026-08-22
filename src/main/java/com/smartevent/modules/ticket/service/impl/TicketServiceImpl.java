package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.enums.TicketStatus;
import com.smartevent.common.util.QrCodeUtils;
import com.smartevent.common.util.TicketSecurityUtils;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventArea;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventAreaRepository;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticket.service.TicketService;
import com.smartevent.modules.ticketing.entity.TicketSalePhase;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketSalePhaseRepository;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository qrTokenRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventRepository eventRepository;
    private final EventAreaRepository eventAreaRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketSalePhaseRepository salePhaseRepository;
    private final com.smartevent.modules.identity.repository.UserRepository userRepository;
    private final com.smartevent.modules.outbox.service.OutboxService outboxService;

    @Override
    @Transactional
    public List<TicketResponse> issueTicketsForOrder(UUID orderId) {
        log.info("Bắt đầu phát hành vé điện tử cho Order ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new TicketException(com.smartevent.common.error.ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<Ticket> savedTickets = new ArrayList<>();

        for (OrderItem item : items) {
            // Lấy thông tin Event ID từ TicketType
            TicketType ticketType = ticketTypeRepository.findById(item.getTicketTypeId())
                    .orElseThrow(() -> new TicketException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy loại vé"));

            UUID eventId = ticketType.getEventId();
            UUID areaId = ticketType.getEventAreaId();

            // Phát hành N vé tương ứng với quantity của OrderItem
            int quantity = item.getQuantity() != null ? item.getQuantity() : 1;
            for (int i = 0; i < quantity; i++) {
                String ticketCode = generateUniqueTicketCode();

                Ticket ticket = new Ticket(
                        item.getId(),
                        order.getUserId(),
                        order.getUserId(),
                        eventId,
                        item.getEventSeatId(),
                        areaId,
                        item.getTicketTypeId(),
                        item.getSalePhaseId(),
                        ticketCode
                );
                Ticket savedTicket = ticketRepository.save(ticket);

                // Sinh mã Token QR ban đầu và lưu vào DB
                String qrTokenHash = TicketSecurityUtils.generateSecureQrToken(savedTicket.getId(), order.getUserId());
                TicketQrToken qrToken = new TicketQrToken(savedTicket.getId(), qrTokenHash);
                qrTokenRepository.save(qrToken);

                // Ghi Outbox Event gửi Email vé điện tử kèm ảnh QR Base64 bất đồng bộ qua RabbitMQ
                String qrBase64 = com.smartevent.common.util.QrCodeUtils.generateQrCodeBase64(qrTokenHash);
                String eventName = eventRepository.findById(savedTicket.getEventId()).map(Event::getName).orElse("Sự kiện");
                String ownerEmail = userRepository.findById(order.getUserId()).map(com.smartevent.modules.identity.entity.User::getEmail).orElse("user@gmail.com");
                String seatCode = savedTicket.getEventSeatId() != null
                        ? eventSeatRepository.findById(savedTicket.getEventSeatId()).map(EventSeat::getSeatNumber).orElse("Ghế tự do") : "Vé đứng";
                String typeName = ticketTypeRepository.findById(savedTicket.getTicketTypeId()).map(TicketType::getName).orElse("Standard");

                outboxService.publishEvent("TICKET", savedTicket.getId(), new com.smartevent.modules.ticket.dto.event.TicketIssuedEvent(
                        savedTicket.getId(),
                        savedTicket.getTicketCode(),
                        savedTicket.getEventId(),
                        eventName,
                        savedTicket.getCurrentOwnerUserId(),
                        ownerEmail,
                        seatCode,
                        typeName,
                        qrBase64
                ));

                savedTickets.add(savedTicket);
            }
        }

        log.info("Phát hành thành công {} vé cho Order ID: {}", savedTickets.size(), orderId);
        return savedTickets.stream().map(this::mapToTicketResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId, UUID currentUserId, boolean isAdmin) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé điện tử"));

        if (!isAdmin && !ticket.getCurrentOwnerUserId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem tấm vé này");
        }

        return mapToTicketResponse(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(UUID currentUserId) {
        List<Ticket> tickets = ticketRepository.findByCurrentOwnerUserIdOrderByCreatedAtDesc(currentUserId);
        return tickets.stream().map(this::mapToTicketResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByEvent(UUID eventId, UUID currentUserId, boolean isAdmin) {
        if (!isAdmin) {
            Event event = eventRepository.findById(eventId)
                    .orElseThrow(() -> new TicketException(ErrorCode.EVENT_NOT_FOUND, "Không tìm thấy sự kiện"));
            if (!event.getOrganizerId().equals(currentUserId)) {
                throw new TicketException(ErrorCode.ACCESS_DENIED, "Bạn không phải ban tổ chức sự kiện này");
            }
        }

        List<Ticket> tickets = ticketRepository.findByCurrentOwnerUserIdAndEventId(currentUserId, eventId);
        return tickets.stream().map(this::mapToTicketResponse).toList();
    }

    @Override
    @Transactional
    public TicketResponse refreshTicketQr(UUID ticketId, UUID currentUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketException(ErrorCode.TICKET_NOT_FOUND, "Không tìm thấy vé"));

        if (!ticket.getCurrentOwnerUserId().equals(currentUserId)) {
            throw new TicketException(ErrorCode.ACCESS_DENIED, "Chỉ chủ sở hữu mới có quyền làm mới mã QR");
        }

        if (ticket.getStatus() != TicketStatus.ISSUED) {
            throw new TicketException(ErrorCode.TICKET_INVALID_STATUS, "Vé không ở trạng thái hợp lệ để đổi mã QR");
        }

        // 1. Thu hồi toàn bộ token cũ
        List<TicketQrToken> oldTokens = qrTokenRepository.findByTicketId(ticketId);
        for (TicketQrToken oldToken : oldTokens) {
            if (oldToken.isActive()) {
                oldToken.setStatus("REVOKED");
                oldToken.setRevokedAt(java.time.Instant.now());
                qrTokenRepository.save(oldToken);
            }
        }

        // 2. Sinh token QR mới
        String newQrTokenHash = TicketSecurityUtils.generateSecureQrToken(ticketId, currentUserId);
        TicketQrToken newQrToken = new TicketQrToken(ticketId, newQrTokenHash);
        qrTokenRepository.save(newQrToken);

        return mapToTicketResponse(ticket);
    }

    // Helper: Map Ticket Entity -> TicketResponse (Kèm sinh ảnh QR Base64 trong RAM)
    private TicketResponse mapToTicketResponse(Ticket ticket) {
        String eventName = eventRepository.findById(ticket.getEventId()).map(Event::getName).orElse("Unknown Event");
        String areaName = ticket.getEventAreaId() != null
                ? eventAreaRepository.findById(ticket.getEventAreaId()).map(EventArea::getName).orElse(null) : null;
        String seatCode = ticket.getEventSeatId() != null
                ? eventSeatRepository.findById(ticket.getEventSeatId()).map(EventSeat::getSeatNumber).orElse(null) : "Vé đứng tự do";
        String ticketTypeName = ticket.getTicketTypeId() != null
                ? ticketTypeRepository.findById(ticket.getTicketTypeId()).map(TicketType::getName).orElse("General") : "General";
        String phaseName = ticket.getSalePhaseId() != null
                ? salePhaseRepository.findById(ticket.getSalePhaseId()).map(TicketSalePhase::getName).orElse("Standard") : "Standard";

        // Lấy token QR đang Active để render ảnh QR
        String qrContent = qrTokenRepository.findFirstByTicketIdAndStatusOrderByIssuedAtDesc(ticket.getId(), "ACTIVE")
                .map(TicketQrToken::getTokenHash)
                .orElse(ticket.getTicketCode());

        String qrCodeBase64 = QrCodeUtils.generateQrCodeBase64(qrContent);

        return new TicketResponse(
                ticket.getId(),
                ticket.getOrderItemId(),
                ticket.getCurrentOwnerUserId(),
                ticket.getOriginalBuyerUserId(),
                ticket.getEventId(),
                eventName,
                ticket.getEventAreaId(),
                areaName,
                ticket.getEventSeatId(),
                seatCode,
                ticket.getTicketTypeId(),
                ticketTypeName,
                ticket.getSalePhaseId(),
                phaseName,
                ticket.getTicketCode(),
                ticket.getStatus(),
                qrCodeBase64,
                ticket.getIssuedAt(),
                ticket.getUsedAt()
        );
    }

    // Helper: Sinh mã vé duy nhất: TCK-yyyyMMdd-XXXXXXXX
    private String generateUniqueTicketCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String code;
        do {
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            code = "TCK-" + datePart + "-" + randomPart;
        } while (ticketRepository.existsByTicketCode(code));
        return code;
    }
}