package com.smartevent.ticketing.modules.identity.exception;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;

public class AuthException extends BusinessException {
    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
