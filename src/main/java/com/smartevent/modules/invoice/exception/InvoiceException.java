package com.smartevent.modules.invoice.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

public class InvoiceException extends BusinessException {

    public InvoiceException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvoiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}