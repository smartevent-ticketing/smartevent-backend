package com.smartevent.modules.invoice.dto.request;

import jakarta.validation.constraints.Email;

public record SendInvoiceEmailRequest(

        @Email(message = "Email người nhận không đúng định dạng")
        String recipientEmail, // Nếu để trống sẽ tự lấy billing_email mặc định của đơn hàng

        String note            // Ghi chú đính kèm trong email
) {}