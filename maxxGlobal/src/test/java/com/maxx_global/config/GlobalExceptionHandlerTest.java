package com.maxx_global.config;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.service.LocalizationService;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(new LocalizationService(source)))
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test void businessExceptionHasStableCodeAndStatus() throws Exception {
        mvc.perform(get("/errors/business").header("X-Trace-Id", "trace-1234"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("X-Trace-Id", "trace-1234"))
                .andExpect(jsonPath("$.traceId").value("trace-1234"))
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test void validationContainsCleanFieldErrors() throws Exception {
        mvc.perform(post("/errors/validation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test void notFoundIs404() throws Exception {
        mvc.perform(get("/errors/not-found")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test void accessDeniedIs403() throws Exception {
        mvc.perform(get("/errors/access")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test void authenticationAndJwtAre401WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/errors/auth")).andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-password"))));
        mvc.perform(get("/errors/jwt")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_FAILED"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-token"))));
    }

    @Test void conflictDoesNotLeakSql() throws Exception {
        mvc.perform(get("/errors/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("users_email_key"))));
    }

    @Test void unexpectedNpeIsSafe500() throws Exception {
        mvc.perform(get("/errors/npe")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("compareTo"))));
    }

    @Test void repositoryNullIdMessageIsNotExposedAndTraceIdMatches() throws Exception {
        mvc.perform(get("/errors/repository").header("X-Trace-Id", "repo-trace-1"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Trace-Id", "repo-trace-1"))
                .andExpect(jsonPath("$.traceId").value("repo-trace-1"))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("The given id must not be null"))));
    }

    @Test void englishAndTurkishMessagesAreLocalized() throws Exception {
        mvc.perform(get("/errors/npe").header("Accept-Language", "en"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred while processing the request. Please try again."));
        mvc.perform(get("/errors/npe").header("Accept-Language", "tr"))
                .andExpect(jsonPath("$.message").value("İşlem sırasında beklenmeyen bir sorun oluştu. Lütfen tekrar deneyin."));
    }

    @Test void successContractRemainsCompatible() throws Exception {
        mvc.perform(get("/errors/success")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    record Input(@NotBlank String name) {}

    @RestController
    @RequestMapping("/errors")
    static class ThrowingController {
        @GetMapping("/business") void business() { throw new BusinessException(ApiErrorCode.INSUFFICIENT_STOCK); }
        @PostMapping("/validation") void validation(@Valid @RequestBody Input input) {}
        @GetMapping("/not-found") void notFound() { throw new EntityNotFoundException("hibernate details"); }
        @GetMapping("/access") void access() { throw new AccessDeniedException("internal acl"); }
        @GetMapping("/auth") void auth() { throw new BadCredentialsException("secret-password"); }
        @GetMapping("/jwt") void jwt() { throw new MalformedJwtException("secret-token"); }
        @GetMapping("/conflict") void conflict() { throw new DataIntegrityViolationException("users_email_key SQL"); }
        @GetMapping("/npe") void npe() { throw new NullPointerException("compareTo internal method"); }
        @GetMapping("/repository") void repository() {
            throw new InvalidDataAccessApiUsageException("The given id must not be null");
        }
        @GetMapping("/success") BaseResponse<String> success() { return BaseResponse.success("ok"); }
    }
}
