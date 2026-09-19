package com.maxx_global.exception;

import com.maxx_global.enums.ApiErrorCode;

public class BusinessException extends RuntimeException {
    private final ApiErrorCode errorCode;
    private final Object[] messageArguments;

    public BusinessException(ApiErrorCode errorCode, Object... messageArguments) {
        super(errorCode.name());
        this.errorCode = errorCode;
        this.messageArguments = messageArguments == null ? new Object[0] : messageArguments.clone();
    }

    public ApiErrorCode getErrorCode() { return errorCode; }
    public Object[] getMessageArguments() { return messageArguments.clone(); }
}
