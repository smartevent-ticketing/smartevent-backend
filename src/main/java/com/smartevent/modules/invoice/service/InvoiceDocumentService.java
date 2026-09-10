package com.smartevent.modules.invoice.service;

import com.smartevent.modules.identity.entity.User;
import com.smartevent.modules.identity.repository.UserRepository;
import com.smartevent.modules.invoice.entity.Invoice;
import com.smartevent.modules.invoice.entity.InvoiceItem;
import com.smartevent.modules.invoice.repository.InvoiceItemRepository;
import com.smartevent.modules.invoice.support.PdfInvoiceGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvoiceDocumentService {

    private final InvoiceItemRepository invoiceItemRepository;
    private final UserRepository userRepository;
    private final PdfInvoiceGenerator pdfInvoiceGenerator;

    @Transactional(readOnly = true)
    public byte[] generate(Invoice invoice) {
        List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invoice.getId());
        User buyer = userRepository.findById(invoice.getUserId()).orElse(null);
        String buyerName = buyer != null ? buyer.getFullName() : "Khách hàng";
        return pdfInvoiceGenerator.generateInvoicePdf(invoice, items, buyerName, invoice.getBillingEmail());
    }
}
