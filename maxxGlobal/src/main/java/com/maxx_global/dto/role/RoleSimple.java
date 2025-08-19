package com.maxx_global.dto.role;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Rol basit bilgiler - dropdown için")
public record RoleSimple(
        @Schema(description = "Rol ID'si", example = "1")
        Long id,

        @Schema(description = "Rol adı", example = "ADMIN")
        String name
) {}