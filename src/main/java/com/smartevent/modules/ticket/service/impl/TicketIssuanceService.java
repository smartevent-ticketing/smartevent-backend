package com.smartevent.modules.ticket.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.common.util.QrCodeUtils;
import com.smartevent.common.util.TicketSecurityUtils;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.entity.EventSeat;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.outbox.service.OutboxService;
import com.smartevent.modules.ticket.dto.event.TicketIssuedEvent;
import com.smartevent.modules.ticket.dto.response.TicketResponse;
import com.smartevent.modules.ticket.entity.Ticket;
import com.smartevent.modules.ticket.entity.TicketQrToken;
import com.smartevent.modules.ticket.exception.TicketException;
import com.smartevent.modules.ticket.repository.TicketQrTokenRepository;
import com.smartevent.modules.ticket.repository.TicketRepository;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketIssuanceService {

    private final TicketQueryService ticketQueryService;
    private final TicketRepository ticketRepository;
    private final TicketQrTokenRepository qrTokenRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;

    @Transactional
    public List<TicketResponse> issueTicketsForOrder(UUID orderId) {
        log.info("Bắt đầu phát hành vé điện tử cho Order ID: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new TicketException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

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
                String qrBase64 = QrCodeUtils.generateQrCodeBase64(qrTokenHash);
                String eventName = eventRepository.findById(savedTicket.getEventId()).map(Event::getName).orElse("Sự kiện");
                String ownerEmail = userRepository.findById(order.getUserId()).map(User::getEmail).orElse("user@gmail.com");
                String seatCode = savedTicket.getEventSeatId() != null
                        ? eventSeatRepository.findById(savedTicket.getEventSeatId()).map(EventSeat::getSeatNumber).orElse("Ghế tự do") : "Vé đứng";
                String typeName = ticketTypeRepository.findById(savedTicket.getTicketTypeId()).map(TicketType::getName).orElse("Standard");

                outboxService.publishEvent("TICKET", savedTicket.getId(), new TicketIssuedEvent(
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
        return savedTickets.stream().map(ticketQueryService::toResponse).toList();
    }

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
