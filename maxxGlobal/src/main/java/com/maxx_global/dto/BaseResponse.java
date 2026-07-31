package com.maxx_global.dto;

import java.time.Instant;
import java.util.Map;

public class BaseResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;
    private int code;
    private String errorCode;
    private String traceId;
    private Map<String, String> fieldErrors;

    public BaseResponse(boolean success, String message, T data, int code) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = Instant.now();
        this.code = code;
    }

    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(true, null, data, 200);
    }

    public static <T> BaseResponse<T> error(String message, int code) {
        return error(message, code, null, null, null);
    }

    public static <T> BaseResponse<T> error(String message, int status, String errorCode,
                                            String traceId, Map<String, String> fieldErrors) {
        BaseResponse<T> response = new BaseResponse<>(false, message, null, status);
        response.errorCode = errorCode;
        response.traceId = traceId;
        response.fieldErrors = fieldErrors;
        return response;
    }

    // Getters
    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getCode() {
        return code;
    }

    public String getErrorCode() { return errorCode; }
    public String getTraceId() { return traceId; }
    public Map<String, String> getFieldErrors() { return fieldErrors; }
}
