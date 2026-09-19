package com.maxx_global.dto;

import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;

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
        boolean english = "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());
        String errorCode;
        String fallbackMessage;
        if (code >= 500) {
            errorCode = "INTERNAL_SERVER_ERROR";
            fallbackMessage = english ? "An unexpected error occurred while processing the request. Please try again."
                    : "İşlem sırasında beklenmeyen bir sorun oluştu. Lütfen tekrar deneyin.";
        } else if (code == 409) {
            errorCode = "CONFLICT";
            fallbackMessage = english ? "This operation conflicts with existing data."
                    : "Bu işlem mevcut kayıtlarla çakışıyor.";
        } else if (code == 404) {
            errorCode = "RESOURCE_NOT_FOUND";
            fallbackMessage = english ? "The requested resource was not found." : "İstenen kayıt bulunamadı.";
        } else if (code == 403) {
            errorCode = "ACCESS_DENIED";
            fallbackMessage = english ? "You are not authorized to perform this action."
                    : "Bu işlemi yapmaya yetkiniz bulunmuyor.";
        } else if (code == 401) {
            errorCode = "AUTHENTICATION_FAILED";
            fallbackMessage = english ? "Authentication failed." : "Kimlik doğrulama başarısız.";
        } else if (code == 422) {
            errorCode = "INVALID_ORDER";
            fallbackMessage = english ? "The request could not be processed." : "İşlem gerçekleştirilemedi.";
        } else {
            errorCode = "VALIDATION_ERROR";
            fallbackMessage = english ? "The submitted information is invalid." : "Gönderilen bilgiler geçerli değil.";
        }
        String safeMessage = message == null || message.isBlank() || isTechnicalMessage(message)
                ? fallbackMessage : message;
        return error(safeMessage, code, errorCode, MDC.get("traceId"), null);
    }

    private static boolean isTechnicalMessage(String message) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("cannot invoke")
                || normalized.contains("id must not be null")
                || normalized.contains("the given id must not be null")
                || normalized.contains("invaliddataaccessapiusageexception")
                || normalized.contains("jpasystemexception")
                || normalized.contains("hibernate")
                || normalized.contains("java.lang.")
                || normalized.contains("org.springframework")
                || normalized.contains("sqlstate")
                || normalized.contains("select ")
                || normalized.contains("insert into")
                || normalized.contains("update ")
                || normalized.contains("delete from");
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
