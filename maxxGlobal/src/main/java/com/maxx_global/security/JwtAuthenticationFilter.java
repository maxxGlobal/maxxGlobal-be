package com.maxx_global.security;

import com.maxx_global.config.CustomAuthenticationEntryPoint;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService,
                                   CustomAuthenticationEntryPoint authenticationEntryPoint,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // Spring Security decides whether this request is public or requires authentication.
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = authHeader.substring(7);
            if (jwt.isBlank()) {
                throw new BadCredentialsException("Empty bearer token");
            }
            String userEmail = jwtService.extractUsername(jwt);
            if (userEmail == null || userEmail.isBlank()) {
                throw new BadCredentialsException("JWT subject is missing");
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(userEmail);
                if (!Boolean.TRUE.equals(jwtService.isTokenValid(jwt, userDetails))) {
                    throw new BadCredentialsException("JWT validation failed");
                }
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

        } catch (JwtException | AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            logger.warn("JWT authentication failed traceId={} method={} uri={} type={}", MDC.get("traceId"),
                    request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
            if (!response.isCommitted()) {
                authenticationEntryPoint.commence(request, response,
                        new BadCredentialsException("JWT authentication failed", ex));
            }
            return;
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            logger.error("Unexpected authentication filter error traceId={} method={} uri={}", MDC.get("traceId"),
                    request.getMethod(), request.getRequestURI(), ex);
            if (!response.isCommitted()) {
                exceptionResolver.resolveException(request, response, null, ex);
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
