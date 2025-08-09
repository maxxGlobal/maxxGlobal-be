package com.maxx_global.dto.dealer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DealerRequest(
        @NotBlank(message = "İsim boş olamaz")
        @Size(min = 2, max = 50, message = "İsim 2 ile 50 karakter arasında olmalıdır")
        String name,
        @Pattern(regexp = "^[0-9]+$", message = "Telefon numarası sadece rakamlardan oluşmalıdır")
        @Size(min = 10, max = 20, message = "Telefon numarası 10-20 karakter arasında olmalı")
        String fixedPhone,
        @Pattern(regexp = "^[0-9]+$", message = "Telefon numarası sadece rakamlardan oluşmalıdır")
        @Size(min = 10, max = 20, message = "Telefon numarası 10-20 karakter arasında olmalı")
        String mobilePhone,
        @NotBlank(message = "Email boş olamaz")
        @Email(message = "Geçerli bir email adresi giriniz")
        String email,
        @NotBlank(message = "Geçerli bir adres girin")
        @Size(min = 6, max = 100, message = "Şifre 6 ile 100 karakter arasında olmalıdır")
        String address
) {}
