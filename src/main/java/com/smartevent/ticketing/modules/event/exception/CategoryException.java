package com.smartevent.ticketing.modules.event.exception;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;

public class CategoryException extends BusinessException {

    public CategoryException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CategoryException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
