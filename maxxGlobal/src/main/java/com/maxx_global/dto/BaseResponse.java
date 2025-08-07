package com.maxx_global.dto;

import java.time.Instant;

public class BaseResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;
    private int code;

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
        return new BaseResponse<>(false, message, null, code);
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
}
