package com.smartevent.modules.payment.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class PaymentException extends BusinessException {

    public PaymentException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}