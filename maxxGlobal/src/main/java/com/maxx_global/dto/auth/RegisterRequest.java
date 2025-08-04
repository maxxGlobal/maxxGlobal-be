package com.maxx_global.dto.auth;

public record RegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        Long dealerId, // Kullanıcı bir bayiye bağlı olacaksa
        Long roleId
) {}
