package com.smartevent.common.error;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(String entityName, Object identifier) {
        super(ErrorCode.RESOURCE_NOT_FOUND, entityName + " với id '" + identifier + "' không tồn tại.");
    }
}

