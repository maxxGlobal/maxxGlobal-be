package com.maxx_global.dto.appUser;

import java.util.List;

public record AppUserRequest(
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String password,
        Long dealerId, // Kullanıcının bağlı olduğu bayi
        List<Long> roleIds // Atanacak rollerin id’leri
) {}
