package com.maxx_global.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "İsim boş olamaz")
        @Size(min = 2, max = 50, message = "İsim 2 ile 50 karakter arasında olmalıdır")
        String firstName,

        @NotBlank(message = "Soyisim boş olamaz")
        @Size(min = 2, max = 50, message = "Soyisim 2 ile 50 karakter arasında olmalıdır")
        String lastName,

        @NotBlank(message = "Email boş olamaz")
        @Email(message = "Geçerli bir email adresi giriniz")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}\\[\\]:\";'<>?,./]).{8,}$",
                message = "Şifre en az 1 büyük harf, 1 küçük harf, 1 sayı, 1 sembol içermeli ve en az 8 karakter olmalıdır."
        )
        @Size(min = 8, max = 100, message = "Şifre 6 ile 100 karakter arasında olmalıdır")
        String password,

        @NotBlank(message = "Geçerli bir adres girin")
        @Size(min = 6, max = 100, message = "Geçerli bir adres girin")
        String address,

        @NotBlank(message = "Telefon numarası boş olamaz")
        @Pattern(regexp = "^[0-9]+$", message = "Telefon numarası sadece rakamlardan oluşmalıdır")
        @Size(min = 10, max = 20, message = "Telefon numarası 10-20 karakter arasında olmalı")
        String phoneNumber,

        Long dealerId, // opsiyonel olduğu için validasyon koymadık

        Long roleId,

        Boolean authorizedUser,

        Boolean emailNotifications
) {}

