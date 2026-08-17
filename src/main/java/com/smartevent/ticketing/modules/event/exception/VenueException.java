package com.smartevent.ticketing.modules.event.exception;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;

public class VenueException extends BusinessException {

    public VenueException(ErrorCode errorCode) {
        super(errorCode);
    }

    public VenueException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
