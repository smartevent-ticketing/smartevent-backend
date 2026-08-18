package com.smartevent.modules.event.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class EventException extends BusinessException {

    public EventException(ErrorCode errorCode) {
        super(errorCode);
    }

    public EventException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
