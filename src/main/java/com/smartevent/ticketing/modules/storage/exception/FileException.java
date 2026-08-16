package com.smartevent.ticketing.modules.storage.exception;

import com.smartevent.ticketing.common.error.BusinessException;
import com.smartevent.ticketing.common.error.ErrorCode;

public class FileException extends BusinessException {

    public FileException(ErrorCode errorCode) {
        super(errorCode);
    }

    public FileException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
