package com.smartevent.modules.invoice.service.impl;

import com.smartevent.common.error.ErrorCode;
import com.smartevent.modules.invoice.dto.request.SendInvoiceEmailRequest;
import com.smartevent.modules.invoice.dto.response.InvoiceDeliveryResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceItemResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceDelivery;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import com.smartevent.modules.invoice.exception.InvoiceException;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.repository.InvoiceRepository;
import com.smartevent.modules.invoice.service.InvoiceDeliveryService;
import com.smartevent.modules.invoice.service.InvoiceDocumentService;
import com.smartevent.modules.invoice.service.InvoiceService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceIssuanceService invoiceIssuanceService;
    private final InvoiceDeliveryService invoiceDeliveryService;
    private final InvoiceDocumentService invoiceDocumentService;

    @Override
    public InvoiceResponse issueInvoiceForOrder(UUID orderId) {
        return invoiceIssuanceService.issueInvoiceForOrder(orderId);
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

        InvoiceDelivery savedDelivery = invoiceDeliveryService.enqueue(invoice, targetEmail);

        log.info("Đã kích hoạt gửi lại hóa đơn {} sang email {}", invoice.getInvoiceCode(), targetEmail);
        return InvoiceDeliveryResponse.fromEntity(savedDelivery);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePdf(UUID invoiceId, UUID currentUserId, boolean isAdmin) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new InvoiceException(ErrorCode.INVOICE_NOT_FOUND, "Không tìm thấy hóa đơn"));

        verifyOwnership(invoice, currentUserId, isAdmin);

        return invoiceDocumentService.generate(invoice);
    }

    private void verifyOwnership(Invoice invoice, UUID currentUserId, boolean isAdmin) {
        if (!isAdmin && !invoice.getUserId().equals(currentUserId)) {
            throw new InvoiceException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập hóa đơn này");
        }
    }

    private InvoiceResponse buildInvoiceResponse(Invoice invoice) {
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        List<InvoiceItemResponse> itemResponses = items.stream().map(InvoiceItemResponse::fromEntity).toList();
        return InvoiceResponse.of(invoice, itemResponses);
    }
}
