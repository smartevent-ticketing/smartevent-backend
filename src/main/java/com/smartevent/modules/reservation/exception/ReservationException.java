package com.smartevent.modules.reservation.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class ReservationException extends BusinessException {

    public ReservationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ReservationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}