package com.maxx_global.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SignatureException;
import java.time.LocalDateTime;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        try {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            if(request.getRequestURI().equals("/api/auth/login")) {
                filterChain.doFilter(request, response);
                return;
            }
            // Token yoksa 401 Unauthorized döndür
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    """
                    {
                        "timestamp": "%s",
                        "status": 401,
                        "error": "UNAUTHORIZED",
                        "message": "JWT token gerekli"
                    }
                    """.formatted(LocalDateTime.now())
            );
            return;
        }

        jwt = authHeader.substring(7);
        userEmail = jwtService.extractUsername(jwt); // token'dan email çıkar

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // BURADA CustomUserDetails oluşturuluyor
            CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(userEmail);

            if (jwtService.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,  // Burada userDetails kesin olmalı
                        null,
                        userDetails.getAuthorities()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        }

        filterChain.doFilter(request, response);
        } catch (Exception ex) {
            // JWT hatasını özel JSON yanıtı ile döndür
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    """
                    {
                        "timestamp": "%s",
                        "status": 403,
                        "error": "JWT geçersiz",
                        "message": "%s"
                    }
                    """.formatted(LocalDateTime.now(), ex.getMessage())
            );
        }
    }
}