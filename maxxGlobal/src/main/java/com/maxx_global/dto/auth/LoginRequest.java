package com.maxx_global.dto.auth;

public record LoginRequest(
        String email,
        String password
) {}
