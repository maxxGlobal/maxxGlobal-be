package com.maxx_global.dto.appUser;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AppUserRequest(
        String firstName,
        String lastName,
        @Email(message = "Geçerli bir email adresi giriniz")
        String email,
        @Size(min = 10, max = 15, message = "Telefon numarası 10-15 karakter arasında olmalı")
        String phoneNumber,
        @Size(min = 8, max = 100, message = "Parola en az 8 karakter olmalı")
        String password,
        Long dealerId, // Artık zorunlu değil - null olabilir
        List<Long> roleIds,
        String status,
        String address,
        Boolean authorizedUser
) {}