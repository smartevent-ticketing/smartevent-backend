package com.smartevent.ticketing.modules.event.exception;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;

public class EventException extends BusinessException {

    public EventException(ErrorCode errorCode) {
        super(errorCode);
    }

    public EventException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}