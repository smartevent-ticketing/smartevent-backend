package com.smartevent.modules.ordering.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class OrderingException extends BusinessException {

    public OrderingException(ErrorCode errorCode) {
        super(errorCode);
    }

    public OrderingException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
