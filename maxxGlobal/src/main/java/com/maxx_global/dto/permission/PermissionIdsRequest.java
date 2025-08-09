package com.maxx_global.dto.permission;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PermissionIdsRequest(
        @NotNull(message = "Permission ID listesi null olamaz")
        @NotEmpty(message = "En az bir permission ID gerekli")
        List<Long> permissionIds
) {}