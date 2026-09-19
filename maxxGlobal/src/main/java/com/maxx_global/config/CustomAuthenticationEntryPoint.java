package com.maxx_global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxx_global.dto.BaseResponse;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.service.LocalizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    private final LocalizationService localizationService;

    public CustomAuthenticationEntryPoint(ObjectMapper objectMapper, LocalizationService localizationService) {
        this.objectMapper = objectMapper;
        this.localizationService = localizationService;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE);
        String traceId = value instanceof String id ? id : UUID.randomUUID().toString();
        ApiErrorCode code = ApiErrorCode.AUTHENTICATION_FAILED;
        response.setHeader(TraceIdFilter.HEADER, traceId);
        response.setStatus(code.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), BaseResponse.error(
                localizationService.getMessage(code.getMessageKey(), localizationService.getCurrentRequestLocale()),
                code.getStatus().value(), code.getCode(), traceId, null));
    }
}
