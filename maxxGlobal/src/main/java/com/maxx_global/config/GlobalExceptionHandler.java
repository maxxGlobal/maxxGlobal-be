package com.maxx_global.config;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.service.LocalizationService;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LocalizationService localizationService;

    public GlobalExceptionHandler(LocalizationService localizationService) {
        this.localizationService = localizationService;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusiness(BusinessException ex,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        ApiErrorCode code = ex.getErrorCode();
        return error(code, localizationService.getMessage(code.getMessageKey(),
                localizationService.getCurrentRequestLocale(), ex.getMessageArguments()), null, request, response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodValidation(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request,
                                                                      HttpServletResponse response) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(cleanField(error.getField()), error.getDefaultMessage()));
        return error(ApiErrorCode.VALIDATION_ERROR, message(ApiErrorCode.VALIDATION_ERROR), fields, request, response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConstraintValidation(ConstraintViolationException ex,
                                                                          HttpServletRequest request,
                                                                          HttpServletResponse response) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                fields.putIfAbsent(cleanField(violation.getPropertyPath().toString()), violation.getMessage()));
        return error(ApiErrorCode.VALIDATION_ERROR, message(ApiErrorCode.VALIDATION_ERROR), fields, request, response);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<BaseResponse<Void>> handleInvalidInput(Exception ex, HttpServletRequest request,
                                                                  HttpServletResponse response) {
        return error(ApiErrorCode.VALIDATION_ERROR, message(ApiErrorCode.VALIDATION_ERROR), null, request, response);
    }

    @ExceptionHandler({BadCredentialsException.class, JwtException.class})
    public ResponseEntity<BaseResponse<Void>> handleAuthentication(Exception ex, HttpServletRequest request,
                                                                    HttpServletResponse response) {
        return error(ApiErrorCode.AUTHENTICATION_FAILED, message(ApiErrorCode.AUTHENTICATION_FAILED),
                null, request, response);
    }

    @ExceptionHandler({AccessDeniedException.class, SecurityException.class})
    public ResponseEntity<BaseResponse<Void>> handleAccessDenied(Exception ex, HttpServletRequest request,
                                                                  HttpServletResponse response) {
        return error(ApiErrorCode.ACCESS_DENIED, message(ApiErrorCode.ACCESS_DENIED), null, request, response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotFound(EntityNotFoundException ex, HttpServletRequest request,
                                                              HttpServletResponse response) {
        return error(ApiErrorCode.RESOURCE_NOT_FOUND, message(ApiErrorCode.RESOURCE_NOT_FOUND), null, request, response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<BaseResponse<Void>> handleConflict(DataIntegrityViolationException ex,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        logControlled("Data integrity conflict", ex, request);
        return error(ApiErrorCode.CONFLICT, message(ApiErrorCode.CONFLICT), null, request, response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request,
                                                                HttpServletResponse response) {
        String traceId = traceId(request, response);
        logger.error("Unexpected error traceId={} method={} uri={} userId={} exception={}", traceId,
                request.getMethod(), request.getRequestURI(), currentUserId(), ex.getClass().getName(), ex);
        return build(ApiErrorCode.INTERNAL_SERVER_ERROR, message(ApiErrorCode.INTERNAL_SERVER_ERROR),
                null, traceId);
    }

    private ResponseEntity<BaseResponse<Void>> error(ApiErrorCode code, String message,
                                                      Map<String, String> fieldErrors,
                                                      HttpServletRequest request, HttpServletResponse response) {
        return build(code, message, fieldErrors, traceId(request, response));
    }

    private ResponseEntity<BaseResponse<Void>> build(ApiErrorCode code, String message,
                                                      Map<String, String> fieldErrors, String traceId) {
        return ResponseEntity.status(code.getStatus()).body(BaseResponse.error(message, code.getStatus().value(),
                code.getCode(), traceId, fieldErrors));
    }

    private String message(ApiErrorCode code) {
        return localizationService.getMessage(code.getMessageKey(), localizationService.getCurrentRequestLocale());
    }

    private String traceId(HttpServletRequest request, HttpServletResponse response) {
        Object attribute = request.getAttribute(TraceIdFilter.ATTRIBUTE);
        String traceId = attribute instanceof String value ? value : MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) traceId = UUID.randomUUID().toString();
        request.setAttribute(TraceIdFilter.ATTRIBUTE, traceId);
        response.setHeader(TraceIdFilter.HEADER, traceId);
        return traceId;
    }

    private String cleanField(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private Object currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return principal instanceof AppUser user ? user.getId() : null;
    }

    private void logControlled(String text, Exception ex, HttpServletRequest request) {
        logger.warn("{} traceId={} method={} uri={}", text, request.getAttribute(TraceIdFilter.ATTRIBUTE),
                request.getMethod(), request.getRequestURI(), ex);
    }
}
