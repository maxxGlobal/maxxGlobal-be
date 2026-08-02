package com.maxx_global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxx_global.dto.BaseResponse;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.service.LocalizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    private final LocalizationService localizationService;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper, LocalizationService localizationService) {
        this.objectMapper = objectMapper;
        this.localizationService = localizationService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        Object value = request.getAttribute(TraceIdFilter.ATTRIBUTE);
        String traceId = value instanceof String id ? id : UUID.randomUUID().toString();
        response.setHeader(TraceIdFilter.HEADER, traceId);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        ApiErrorCode code = ApiErrorCode.ACCESS_DENIED;
        BaseResponse<Void> body = BaseResponse.error(
                localizationService.getMessage(code.getMessageKey(), localizationService.getCurrentRequestLocale()),
                code.getStatus().value(), code.getCode(), traceId, null);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
