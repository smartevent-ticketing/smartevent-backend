package com.smartevent.modules.ticketing.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class TicketingException extends BusinessException {

    public TicketingException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TicketingException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}

