package com.smartevent.modules.invoice.service;

import com.smartevent.modules.invoice.dto.request.SendInvoiceEmailRequest;
import com.smartevent.modules.invoice.dto.response.InvoiceDeliveryResponse;
import com.smartevent.modules.invoice.dto.response.InvoiceResponse;

import java.util.List;
import java.util.UUID;

public interface InvoiceService {

    // 1. Tự động xuất hóa đơn điện tử ngay khi đơn hàng chuyển sang PAID (Exactly-Once)
    InvoiceResponse issueInvoiceForOrder(UUID orderId);

    // 2. Lấy chi tiết hóa đơn theo Invoice ID
    InvoiceResponse getInvoiceById(UUID invoiceId, UUID currentUserId, boolean isAdmin);

    // 3. Lấy hóa đơn theo Order ID
    InvoiceResponse getInvoiceByOrderId(UUID orderId, UUID currentUserId, boolean isAdmin);

    // 4. Tra cứu hóa đơn theo mã hiển thị công khai (VD: INV-20260822-ABC12345)
    InvoiceResponse getInvoiceByCode(String invoiceCode, UUID currentUserId, boolean isAdmin);

    // 5. Lấy danh sách toàn bộ hóa đơn của người dùng hiện tại
    List<InvoiceResponse> getMyInvoices(UUID currentUserId);

    // 6. Gửi / Gửi lại hóa đơn qua email của khách hàng
    InvoiceDeliveryResponse sendInvoiceEmail(UUID invoiceId, UUID currentUserId, SendInvoiceEmailRequest request);

    // 7. Xuất file PDF hóa đơn điện tử thật (OpenPDF)
    byte[] downloadInvoicePdf(UUID invoiceId, UUID currentUserId, boolean isAdmin);
}