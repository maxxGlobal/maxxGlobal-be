package com.maxx_global.config;

import com.maxx_global.security.CustomUserDetails;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // Global izin kontrolü (örneğin USER_MANAGE)
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission.toString()));
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || permission == null) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // Kendi verisini güncelliyorsa izin ver
        if ("AppUser".equals(targetType) && userDetails.getId().equals(targetId)) {
            return true;
        }

        // Aksi durumda yetkisi var mı kontrol et
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission.toString()));
    }
}
