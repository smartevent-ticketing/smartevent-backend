package com.smartevent.modules.identity.exception;

import com.smartevent.common.error.BusinessException;
import com.smartevent.common.error.ErrorCode;

/** The rejection must not roll back revocation of the compromised user's sessions. */
public class TokenReuseException extends BusinessException {
    public TokenReuseException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Phiên đăng nhập đã bị thu hồi hoặc được sử dụng trước đó");
    }
}
