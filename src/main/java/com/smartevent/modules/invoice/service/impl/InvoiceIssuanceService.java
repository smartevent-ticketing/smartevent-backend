package com.smartevent.modules.invoice.service.impl;

import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.invoice.dto.response.InvoiceItemResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import com.smartevent.modules.invoice.exception.InvoiceException;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.invoice.service.InvoiceDeliveryService;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceIssuanceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final InvoiceDeliveryService invoiceDeliveryService;

    @Transactional
    public InvoiceResponse issueInvoiceForOrder(UUID orderId) {
        log.info("Bắt đầu xuất hóa đơn điện tử cho Order ID: {}", orderId);

        // 1. Kiểm tra chống xuất trùng lặp (Idempotency)
        Optional<Invoice> existingInvoice = invoiceRepository.findByOrderId(orderId);
        if (existingInvoice.isPresent()) {
            log.warn("Đơn hàng {} đã có hóa đơn {}, trả về hóa đơn hiện tại", orderId, existingInvoice.get().getInvoiceCode());
            return buildInvoiceResponse(existingInvoice.get());
        }

        // 2. Lấy thông tin đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng"));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvoiceException(ErrorCode.ORDER_INVALID_STATUS, "Chỉ đơn hàng đã thanh toán thành công mới được xuất hóa đơn");
        }

        // 3. Lấy email người mua
        String billingEmail = userRepository.findById(order.getUserId())
                .map(User::getEmail)
                .orElse("customer@example.com");

        // 4. Tạo đầu hóa đơn (Invoice)
        String invoiceCode = generateUniqueInvoiceCode();
        Invoice invoice = new Invoice(
                order.getId(),
                order.getUserId(),
                invoiceCode,
                billingEmail,
                order.getSubtotal(),
                order.getDiscountAmount(),
                order.getFeeAmount(),
                order.getTotalAmount()
        );
        Invoice savedInvoice = invoiceRepository.save(invoice);

        // 5. Bóc tách từng dòng vé (InvoiceItem)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        List<InvoiceItem> invoiceItems = new ArrayList<>();

        for (OrderItem oi : orderItems) {
            String description = buildItemDescription(oi);
            InvoiceItem item = new InvoiceItem(
                    savedInvoice.getId(),
                    oi.getId(),
                    description,
                    oi.getQuantity(),
                    oi.getUnitPrice(),
                    oi.getTotalPrice()
            );
            invoiceItems.add(item);
        }
        invoiceItemRepository.saveAll(invoiceItems);

        invoiceDeliveryService.enqueue(savedInvoice, billingEmail);

        log.info("Xuất hóa đơn thành công! Mã: {} cho Order ID: {}", invoiceCode, orderId);
        return buildInvoiceResponse(savedInvoice);
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        List<InvoiceItemResponse> itemResponses = items.stream().map(InvoiceItemResponse::fromEntity).toList();
        return InvoiceResponse.of(invoice, itemResponses);
    }

    private String buildItemDescription(OrderItem oi) {
        String ticketTypeName = "Vé";
        String eventName = "Sự kiện";

        if (oi.getTicketTypeId() != null) {
            Optional<TicketType> ttOpt = ticketTypeRepository.findById(oi.getTicketTypeId());
            if (ttOpt.isPresent()) {
                ticketTypeName = ttOpt.get().getName();
                if (ttOpt.get().getEventId() != null) {
                    eventName = eventRepository.findById(ttOpt.get().getEventId())
                            .map(Event::getName).orElse("Sự kiện");
                }
            }
        }

        String seatInfo = "";
        if (oi.getEventSeatId() != null) {
            seatInfo = eventSeatRepository.findById(oi.getEventSeatId())
                    .map(s -> " (Ghế: " + s.getSeatNumber() + ")").orElse("");
        }

        return ticketTypeName + " - " + eventName + seatInfo;
    }

    private String generateUniqueInvoiceCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String code;
        do {
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            code = "INV-" + datePart + "-" + randomPart;
        } while (invoiceRepository.existsByInvoiceCode(code));
        return code;
    }
}
