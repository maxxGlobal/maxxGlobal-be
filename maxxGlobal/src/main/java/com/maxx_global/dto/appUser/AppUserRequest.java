package com.maxx_global.dto.appUser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AppUserRequest(

        String firstName,
        String lastName,
        @Email(message = "Geçerli bir email adresi giriniz")
        String email,
        @Size(min = 10, max = 15, message = "Parola en az 8 karakter olmalı")
        String phoneNumber,
        @Size(min = 8, max = 100, message = "Parola en az 8 karakter olmalı")
        String password,
        Long dealerId, // Kullanıcının bağlı olduğu bayi
        List<Long> roleIds, // Atanacak rollerin id’leri
        String status,
        String address
) {}
