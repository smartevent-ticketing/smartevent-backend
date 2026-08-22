package com.smartevent.modules.ticket.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class TicketException extends BusinessException {

    public TicketException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TicketException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}