package com.smartevent.modules.invoice.service.impl;

import com.smartevent.common.enums.DeliveryStatus;
import com.smartevent.common.enums.OrderStatus;
import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.event.entity.Event;
import com.smartevent.modules.event.repository.EventRepository;
import com.smartevent.modules.event.repository.EventSeatRepository;
import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.invoice.dto.request.SendInvoiceEmailRequest;
import com.smartevent.modules.invoice.dto.response.InvoiceDeliveryResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceItemResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import com.smartevent.modules.invoice.exception.InvoiceException;
import com.smartevent.modules.invoice.repository.InvoiceDeliveryRepository;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.invoice.service.InvoiceService;
import com.smartevent.modules.ordering.entity.Order;
import com.smartevent.modules.ordering.entity.OrderItem;
import com.smartevent.modules.ordering.repository.OrderItemRepository;
import com.smartevent.modules.ordering.repository.OrderRepository;
import com.smartevent.modules.ticketing.entity.TicketType;
import com.smartevent.modules.ticketing.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceDeliveryRepository invoiceDeliveryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final com.smartevent.modules.invoice.support.PdfInvoiceGenerator pdfInvoiceGenerator;
    private final com.smartevent.modules.outbox.service.OutboxService outboxService;

    @Override
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

        // 6. Khởi tạo bản ghi gửi email tự động (InvoiceDelivery)
        InvoiceDelivery delivery = new InvoiceDelivery(savedInvoice.getId(), billingEmail);
        delivery.setStatus(DeliveryStatus.PENDING);
        InvoiceDelivery savedDelivery = invoiceDeliveryRepository.save(delivery);

        // Ghi Outbox Event gửi Email hóa đơn bất đồng bộ qua RabbitMQ kèm deliveryId
        outboxService.publishEvent("INVOICE", savedInvoice.getId(), new com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent(
                savedInvoice.getId(),
                savedDelivery.getId(),
                savedInvoice.getInvoiceCode(),
                savedInvoice.getOrderId(),
                savedInvoice.getUserId(),
                billingEmail,
                savedInvoice.getTotalAmount()
        ));

        log.info("Xuất hóa đơn thành công! Mã: {} cho Order ID: {}", invoiceCode, orderId);
        return buildInvoiceResponse(savedInvoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(UUID invoiceId, UUID currentUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));

        verifyOwnership(invoice, currentUserId, isAdmin);
        return buildInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByOrderId(UUID orderId, UUID currentUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn của đơn hàng này"));

        verifyOwnership(invoice, currentUserId, isAdmin);
        return buildInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByCode(String invoiceCode, UUID currentUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findByInvoiceCode(invoiceCode)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn với mã: " + invoiceCode));

        verifyOwnership(invoice, currentUserId, isAdmin);
        return buildInvoiceResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getMyInvoices(UUID currentUserId) {
        List<Invoice> invoices = invoiceRepository.findByUserIdOrderByIssuedAtDesc(currentUserId);
        return invoices.stream().map(this::buildInvoiceResponse).toList();
    }

    @Override
    @Transactional
    public InvoiceDeliveryResponse sendInvoiceEmail(UUID invoiceId, UUID currentUserId, SendInvoiceEmailRequest request) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));

        verifyOwnership(invoice, currentUserId, false);

        String targetEmail = (request != null && request.recipientEmail() != null && !request.recipientEmail().isBlank())
                ? request.recipientEmail().trim().toLowerCase()
                : invoice.getBillingEmail();

        InvoiceDelivery delivery = new InvoiceDelivery(invoice.getId(), targetEmail);
        delivery.setStatus(DeliveryStatus.PENDING);
        InvoiceDelivery savedDelivery = invoiceDeliveryRepository.save(delivery);

        // Ghi Outbox Event gửi lại hóa đơn bất đồng bộ
        outboxService.publishEvent("INVOICE", invoice.getId(), new com.smartevent.modules.invoice.dto.event.InvoiceCreatedEvent(
                invoice.getId(),
                invoice.getInvoiceCode(),
                invoice.getOrderId(),
                invoice.getUserId(),
                targetEmail,
                invoice.getTotalAmount()
        ));

        log.info("Đã kích hoạt gửi lại hóa đơn {} sang email {}", invoice.getInvoiceCode(), targetEmail);
        return InvoiceDeliveryResponse.fromEntity(savedDelivery);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePdf(UUID invoiceId, UUID currentUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));

        verifyOwnership(invoice, currentUserId, isAdmin);

        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoiceId);
        User buyer = userRepository.findById(invoice.getUserId()).orElse(null);
        String buyerName = buyer != null ? buyer.getFullName() : "Khách hàng";
        String buyerEmail = buyer != null ? buyer.getEmail() : invoice.getBillingEmail();

        return pdfInvoiceGenerator.generateInvoicePdf(invoice, items, buyerName, buyerEmail);
    }

    // Helper: Kiểm tra quyền xem hóa đơn
    private void verifyOwnership(Invoice invoice, UUID currentUserId, boolean isAdmin) {
        if (!isAdmin && !invoice.getUserId().equals(currentUserId)) {
            throw new InvoiceException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập hóa đơn này");
        }
    }

    // Helper: Xây dựng response kèm danh sách items
    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        List<InvoiceItemResponse> itemResponses = items.stream().map(InvoiceItemResponse::fromEntity).toList();
        return InvoiceResponse.of(invoice, itemResponses);
    }

    // Helper: Mô tả chi tiết mặt hàng vé
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

    // Helper: Sinh mã hóa đơn duy nhất INV-yyyyMMdd-XXXXXXXX
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