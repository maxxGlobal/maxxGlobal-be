package com.maxx_global.dto.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleRequest(
    @NotBlank(message = "Rol adı boş olamaz")
    @Size(min = 2, max = 50, message = "Rol adı 2-50 karakter arasında olmalı")
    String name,

    @NotNull(message = "Permissions listesi null olamaz")
    List<Long> permissionIds // Atanacak permission'ların id'leri
) {}