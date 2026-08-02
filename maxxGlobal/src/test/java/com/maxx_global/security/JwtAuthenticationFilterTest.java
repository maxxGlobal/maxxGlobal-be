package com.maxx_global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxx_global.config.CustomAuthenticationEntryPoint;
import com.maxx_global.config.TraceIdFilter;
import com.maxx_global.service.LocalizationService;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock JwtService jwtService;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock CustomAuthenticationEntryPoint entryPoint;
    @Mock HandlerExceptionResolver exceptionResolver;
    @Mock FilterChain chain;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void missingHeaderIsDelegatedToSpringSecurityInsteadOfWritingManualJson() throws Exception {
        JwtAuthenticationFilter filter = filter();
        MockHttpServletRequest request = request(null);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(entryPoint);
        assertFalse(response.isCommitted());
    }

    @Test
    void malformedAndInvalidSignatureTokensUseAuthenticationEntryPointOnlyOnce() throws Exception {
        for (RuntimeException failure : List.of(
                new ExpiredJwtException(mock(Header.class), mock(Claims.class), "JWT expired at technical timestamp"),
                new MalformedJwtException("technical malformed detail"),
                new SignatureException("JWT signature does not match"))) {
            reset(entryPoint, chain, jwtService);
            when(jwtService.extractUsername("bad-token")).thenThrow(failure);
            MockHttpServletRequest request = request("Bearer bad-token");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter().doFilter(request, response, chain);

            verify(entryPoint, times(1)).commence(eq(request), eq(response), any(BadCredentialsException.class));
            verifyNoInteractions(chain);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    @Test
    void missingTokenUserIsAnAuthenticationFailure() throws Exception {
        when(jwtService.extractUsername("token")).thenReturn("missing@example.com");
        when(userDetailsService.loadUserByUsername("missing@example.com"))
                .thenThrow(new UsernameNotFoundException("database user detail"));
        MockHttpServletRequest request = request("Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(entryPoint).commence(eq(request), eq(response), any(BadCredentialsException.class));
        verifyNoInteractions(chain);
    }

    @Test
    void validTokenBuildsAuthenticationAndContinues() throws Exception {
        CustomUserDetails details = new CustomUserDetails(1L, "user@example.com", "x",
                List.of(new SimpleGrantedAuthority("ORDER_READ")));
        when(jwtService.extractUsername("token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(details);
        when(jwtService.isTokenValid("token", details)).thenReturn(true);
        MockHttpServletRequest request = request("Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(entryPoint, exceptionResolver);
    }

    @Test
    void unexpectedRepositoryFailureIsResolvedAsServerErrorNotAccessDenied() throws Exception {
        RuntimeException failure = new RuntimeException("SQL connection detail");
        when(jwtService.extractUsername("token")).thenThrow(failure);
        MockHttpServletRequest request = request("Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        verify(exceptionResolver).resolveException(request, response, null, failure);
        verifyNoInteractions(entryPoint, chain);
        assertNotEquals(403, response.getStatus());
    }

    @Test
    void authenticationEntryPointReturnsSafe401WithMatchingTraceId() throws Exception {
        LocalizationService localization = mock(LocalizationService.class);
        when(localization.getCurrentRequestLocale()).thenReturn(Locale.ENGLISH);
        when(localization.getMessage("error.authentication", Locale.ENGLISH))
                .thenReturn("The email address or password is incorrect.");
        CustomAuthenticationEntryPoint actual = new CustomAuthenticationEntryPoint(new ObjectMapper(), localization);
        MockHttpServletRequest request = request("Bearer secret-token");
        request.setAttribute(TraceIdFilter.ATTRIBUTE, "jwt-trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        actual.commence(request, response, new BadCredentialsException("JWT signature does not match"));

        assertEquals(401, response.getStatus());
        assertEquals("jwt-trace-1", response.getHeader(TraceIdFilter.HEADER));
        assertTrue(response.getContentAsString().contains("\"traceId\":\"jwt-trace-1\""));
        assertTrue(response.getContentAsString().contains("\"errorCode\":\"AUTHENTICATION_FAILED\""));
        assertFalse(response.getContentAsString().contains("signature"));
    }

    private JwtAuthenticationFilter filter() {
        return new JwtAuthenticationFilter(jwtService, userDetailsService, entryPoint, exceptionResolver);
    }

    private MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        if (authorization != null) request.addHeader("Authorization", authorization);
        return request;
    }
}
