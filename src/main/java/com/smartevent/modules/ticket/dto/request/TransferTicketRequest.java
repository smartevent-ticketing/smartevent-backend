package com.smartevent.modules.ticket.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record TransferTicketRequest(

        @NotBlank(message = "Email người nhận vé không được để trống")
        @Email(message = "Email người nhận không đúng định dạng")
        String recipientEmail,

        String note // Ghi chú hoặc lời nhắn chúc mừng gửi kèm vé
) {}