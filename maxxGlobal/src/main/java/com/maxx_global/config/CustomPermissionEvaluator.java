package com.maxx_global.config;

import com.maxx_global.security.CustomUserDetails;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;

@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private static final Logger logger = Logger.getLogger(CustomPermissionEvaluator.class.getName());

    // ✅ Yetki hiyerarşisi - ROLE_ prefix'siz tanımlı
    private static final Map<String, Set<String>> PERMISSION_HIERARCHY = Map.of(
            "SYSTEM_ADMIN", Set.of("*"), // Tüm yetkilere sahip
            "SYSTEM", Set.of("*"), // Eski isimlendirme için de wildcard
            "ADMIN", Set.of(
                    "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
                    "USER_MANAGE", "USER_RESTORE",
                    "DEALER_READ", "DEALER_CREATE", "DEALER_UPDATE", "DEALER_DELETE",
                    "DEALER_MANAGE", "DEALER_RESTORE",
                    "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE",
                    "PRODUCT_MANAGE", "PRODUCT_RESTORE",
                    "CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE",
                    "CATEGORY_MANAGE", "CATEGORY_RESTORE",
                    "PRICE_READ", "PRICE_CREATE", "PRICE_UPDATE", "PRICE_DELETE",
                    "PRICE_MANAGE", "PRICE_RESTORE",
                    "ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_DELETE",
                    "ORDER_MANAGE", "ORDER_APPROVE", "ORDER_REJECT", "ORDER_CANCEL",
                    "ORDER_SHIP", "ORDER_COMPLETE",
                    "DISCOUNT_READ", "DISCOUNT_CREATE", "DISCOUNT_UPDATE", "DISCOUNT_DELETE",
                    "DISCOUNT_MANAGE", "DISCOUNT_RESTORE",
                    "REPORT_READ", "REPORT_CREATE", "REPORT_EXPORT", "REPORT_MANAGE",
                    "INVENTORY_READ", "INVENTORY_UPDATE", "INVENTORY_MANAGE",
                    "FINANCE_READ", "FINANCE_MANAGE",
                    "AUDIT_READ", "AUDIT_MANAGE",
                    "NOTIFICATION_READ", "NOTIFICATION_CREATE", "NOTIFICATION_MANAGE",
                    "ANALYTICS_READ", "ANALYTICS_MANAGE",
                    "DASHBOARD_VIEW", "DASHBOARD_ADMIN", "ADMIN_DASHBOARD"
            ),
            "MANAGER", Set.of(
                    "USER_READ", "DEALER_READ", "DEALER_UPDATE",
                    "PRODUCT_READ", "PRODUCT_UPDATE", "PRODUCT_CREATE",
                    "CATEGORY_READ", "CATEGORY_UPDATE", "PRICE_READ", "PRICE_UPDATE",
                    "ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_APPROVE",
                    "ORDER_REJECT", "ORDER_SHIP", "ORDER_COMPLETE",
                    "DISCOUNT_READ", "DISCOUNT_CREATE", "DISCOUNT_UPDATE",
                    "REPORT_READ", "REPORT_CREATE", "REPORT_EXPORT",
                    "INVENTORY_READ", "INVENTORY_UPDATE", "INVENTORY_MANAGE",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ", "NOTIFICATION_CREATE",
                    "ANALYTICS_READ"
            ),
            "USER", Set.of(
                    "PRODUCT_READ", "ORDER_READ", "ORDER_CREATE", "PRICE_READ",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ"
            )
    );

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {


        if (authentication == null || permission == null) {
            logger.warning("Authentication or permission is null - returning false");
            return false;
        }

        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            logger.warning("Principal is not CustomUserDetails: " + authentication.getPrincipal().getClass());
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String permissionName = permission.toString();


        // 1. Direkt permission kontrolü
        if (hasDirectPermission(userDetails, permissionName)) {
             return true;
        }

        // 2. Hiyerarşik permission kontrolü
        boolean hierarchicalResult = hasHierarchicalPermission(userDetails, permissionName);

        return hierarchicalResult;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        logger.info("=== PERMISSION CHECK (with targetId) ===");
        logger.info("User: " + (authentication != null ? authentication.getName() : "null"));
        logger.info("TargetId: " + targetId + ", TargetType: " + targetType);
        logger.info("Permission required: " + (permission != null ? permission.toString() : "null"));

        if (authentication == null || permission == null) {
            return false;
        }

        if (!(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String permissionName = permission.toString();

        // Kendi verisini güncelliyorsa izin ver
        if ("AppUser".equals(targetType) && userDetails.getId().equals(targetId)) {
            logger.info("✅ GRANTED: User accessing own data");
            return true;
        }

        // 1. Direkt permission kontrolü
        if (hasDirectPermission(userDetails, permissionName)) {
             return true;
        }

        // 2. Hiyerarşik permission kontrolü
        boolean result = hasHierarchicalPermission(userDetails, permissionName);
        return result;
    }

    /**
     * Kullanıcının direkt permission'ı var mı kontrol eder
     */
    private boolean hasDirectPermission(CustomUserDetails userDetails, String permissionName) {
        boolean result = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permissionName));

         return result;
    }

    /**
     * Kullanıcının hiyerarşik permission'ı var mı kontrol eder
     */
    private boolean hasHierarchicalPermission(CustomUserDetails userDetails, String permissionName) {
        logger.info("Checking hierarchical permissions for: " + permissionName);

        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> {
                    String authorityName = authority.getAuthority();

                    // ✅ Hem ROLE_ prefix'li hem prefix'siz kontrol et
                    String roleName = authorityName;
                    if (authorityName.startsWith("ROLE_")) {
                        roleName = authorityName.substring(5); // ROLE_SYSTEM_ADMIN -> SYSTEM_ADMIN
                    }

                    // Bu rol için tanımlı permission'ları al
                    Set<String> allowedPermissions = PERMISSION_HIERARCHY.get(roleName);

                    if (allowedPermissions == null) {
                        return false;
                    }

                    logger.info("   📋 Role '" + roleName + "' has permissions: " + allowedPermissions);

                    // Tüm yetkilere sahip mi? (SYSTEM_ADMIN veya SISTEM)
                    if (allowedPermissions.contains("*")) {
                        logger.info("   🔥 WILDCARD PERMISSION: Role '" + roleName + "' has ALL permissions - GRANTED!");
                        return true;
                    }

                    // Spesifik permission var mı?
                    boolean hasSpecific = allowedPermissions.contains(permissionName);
                    logger.info("   📝 Role '" + roleName + "' has specific permission '" + permissionName + "': " + hasSpecific);
                    return hasSpecific;
                });
    }
}