package com.maxx_global.dto.appUser;

public record UserSummary(
        Long id,
        String firstName,
        String lastName,
        String email
) {}
