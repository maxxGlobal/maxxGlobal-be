package com.maxx_global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service("securityService")
public class SecurityService {

    // Gerçek permission'larınıza göre role-permission mapping
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(

            // SYSTEM_ADMIN - Tüm yetkilere sahip
            "SYSTEM_ADMIN", Set.of("*"),

            // ADMIN - Yönetim seviyesi yetkiler
            "ADMIN", Set.of(
                    // User permissions
                    "USER_READ", "USER_CREATE", "USER_UPDATE", "USER_DELETE",
                    "USER_MANAGE", "USER_RESTORE",

                    // Dealer permissions
                    "DEALER_READ", "DEALER_CREATE", "DEALER_UPDATE", "DEALER_DELETE",
                    "DEALER_MANAGE", "DEALER_RESTORE",

                    // Product permissions
                    "PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE",
                    "PRODUCT_MANAGE", "PRODUCT_RESTORE",

                    // Category permissions
                    "CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DELETE",
                    "CATEGORY_MANAGE", "CATEGORY_RESTORE",

                    // Price permissions
                    "PRICE_READ", "PRICE_CREATE", "PRICE_UPDATE", "PRICE_DELETE",
                    "PRICE_MANAGE", "PRICE_RESTORE",

                    // Order permissions
                    "ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE", "ORDER_DELETE",
                    "ORDER_MANAGE", "ORDER_APPROVE", "ORDER_REJECT", "ORDER_CANCEL",
                    "ORDER_SHIP", "ORDER_COMPLETE",

                    // Discount permissions
                    "DISCOUNT_READ", "DISCOUNT_CREATE", "DISCOUNT_UPDATE", "DISCOUNT_DELETE",
                    "DISCOUNT_MANAGE", "DISCOUNT_RESTORE",

                    // Report permissions
                    "REPORT_READ", "REPORT_CREATE", "REPORT_EXPORT", "REPORT_MANAGE",

                    // Inventory permissions
                    "INVENTORY_READ", "INVENTORY_UPDATE", "INVENTORY_MANAGE",

                    // Finance permissions
                    "FINANCE_READ", "FINANCE_MANAGE",

                    // Audit permissions
                    "AUDIT_READ", "AUDIT_MANAGE",

                    // Notification permissions
                    "NOTIFICATION_READ", "NOTIFICATION_CREATE", "NOTIFICATION_MANAGE",

                    // Analytics permissions
                    "ANALYTICS_READ", "ANALYTICS_MANAGE",

                    // Dashboard permissions
                    "DASHBOARD_VIEW", "DASHBOARD_ADMIN", "ADMIN_DASHBOARD"
            ),

            // MANAGER - Orta seviye yönetim yetkiler
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

            // SALES_REPRESENTATIVE - Satış temsilcisi
            "SALES_REPRESENTATIVE", Set.of(
                    "PRODUCT_READ", "ORDER_READ", "ORDER_CREATE", "ORDER_UPDATE",
                    "DEALER_READ", "PRICE_READ", "DISCOUNT_READ", "REPORT_READ",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ", "SALES_REPRESENTATIVE"
            ),

            // WAREHOUSE_STAFF - Depo personeli
            "WAREHOUSE_STAFF", Set.of(
                    "PRODUCT_READ", "ORDER_READ", "ORDER_UPDATE", "ORDER_SHIP", "ORDER_COMPLETE",
                    "INVENTORY_READ", "INVENTORY_UPDATE", "INVENTORY_MANAGE",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ", "WAREHOUSE_STAFF"
            ),

            // ACCOUNTING_STAFF - Muhasebe personeli
            "ACCOUNTING_STAFF", Set.of(
                    "ORDER_READ", "PRICE_READ", "FINANCE_READ", "FINANCE_MANAGE",
                    "REPORT_READ", "REPORT_CREATE", "REPORT_EXPORT",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ", "ACCOUNTING_STAFF"
            ),

            // CUSTOMER_SERVICE - Müşteri hizmetleri
            "CUSTOMER_SERVICE", Set.of(
                    "USER_READ", "DEALER_READ", "ORDER_READ", "ORDER_UPDATE", "ORDER_CANCEL",
                    "PRODUCT_READ", "DASHBOARD_VIEW", "NOTIFICATION_READ",
                    "NOTIFICATION_CREATE", "CUSTOMER_SERVICE"
            ),

            // MARKETING_STAFF - Pazarlama personeli
            "MARKETING_STAFF", Set.of(
                    "PRODUCT_READ", "DISCOUNT_READ", "DISCOUNT_CREATE", "DISCOUNT_UPDATE", "DISCOUNT_MANAGE",
                    "ANALYTICS_READ", "ANALYTICS_MANAGE", "REPORT_READ", "REPORT_CREATE",
                    "NOTIFICATION_READ", "NOTIFICATION_CREATE", "NOTIFICATION_MANAGE",
                    "DASHBOARD_VIEW", "MARKETING_STAFF"
            ),

            // USER - Temel kullanıcı (Dealer kullanıcıları)
            "USER", Set.of(
                    "PRODUCT_READ", "ORDER_READ", "ORDER_CREATE", "PRICE_READ",
                    "DASHBOARD_VIEW", "NOTIFICATION_READ"
            )
    );

    // Role hiyerarşisi
    private static final List<String> ROLE_HIERARCHY = Arrays.asList(
            "USER",
            "SALES_REPRESENTATIVE",
            "CUSTOMER_SERVICE",
            "WAREHOUSE_STAFF",
            "ACCOUNTING_STAFF",
            "MARKETING_STAFF",
            "MANAGER",
            "ADMIN",
            "SYSTEM_ADMIN"
    );

    /**
     * Kullanıcının belirli bir permission'a sahip olup olmadığını kontrol eder
     * Hem direkt permission hem de role-based permission kontrolü yapar
     */
    public boolean hasPermission(String permission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        // 1. Direkt permission kontrolü
        boolean hasDirectPermission = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission));

        if (hasDirectPermission) {
            return true;
        }

        // 2. Role-based permission kontrolü
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> {
                    String roleName = authority.getAuthority();

                    // ROLE_ prefix'ini kaldır
                    if (roleName.startsWith("ROLE_")) {
                        roleName = roleName.substring(5);
                    }

                    Set<String> rolePermissions = ROLE_PERMISSIONS.get(roleName);

                    if (rolePermissions == null) {
                        return false;
                    }

                    // Tüm yetkilere sahip mi?
                    if (rolePermissions.contains("*")) {
                        return true;
                    }

                    return rolePermissions.contains(permission);
                });
    }

    /**
     * Kullanıcının belirli bir role sahip olup olmadığını kontrol eder
     */
    public boolean hasRole(String roleName) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + roleName));
    }

    /**
     * Kullanıcının minimum role seviyesine sahip olup olmadığını kontrol eder
     */
    public boolean hasMinimumRole(String minimumRole) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        int minimumLevel = ROLE_HIERARCHY.indexOf(minimumRole);
        if (minimumLevel == -1) {
            return false;
        }

        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> {
                    String roleName = authority.getAuthority();
                    if (roleName.startsWith("ROLE_")) {
                        roleName = roleName.substring(5);
                    }

                    int userLevel = ROLE_HIERARCHY.indexOf(roleName);
                    return userLevel >= minimumLevel;
                });
    }

    /**
     * Kullanıcının süper admin olup olmadığını kontrol eder
     */
    public boolean isSuperAdmin() {
        return hasRole("SYSTEM_ADMIN");
    }

    /**
     * Kullanıcının admin veya üstü olup olmadığını kontrol eder
     */
    public boolean isAdminOrAbove() {
        return hasMinimumRole("ADMIN");
    }

    /**
     * Kullanıcının kendi verisi üzerinde mi işlem yaptığını kontrol eder
     */
    public boolean isOwnerOrAdmin(Long userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return false;
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        // Kendi verisi mi?
        if (userDetails.getId().equals(userId)) {
            return true;
        }

        // Admin yetkisi var mı?
        return isAdminOrAbove();
    }

    /**
     * Multiple permission'dan herhangi birine sahip mi kontrol eder
     */
    public boolean hasAnyPermission(String... permissions) {
        return Arrays.stream(permissions).anyMatch(this::hasPermission);
    }

    /**
     * Tüm permission'lara sahip mi kontrol eder
     */
    public boolean hasAllPermissions(String... permissions) {
        return Arrays.stream(permissions).allMatch(this::hasPermission);
    }

    /**
     * Belirli departman yetkilerine sahip mi kontrol eder
     */
    public boolean hasDepartmentRole(String... departmentRoles) {
        return Arrays.stream(departmentRoles).anyMatch(this::hasRole);
    }

    /**
     * Kullanıcının hangi departman rolleri var?
     */
    public Set<String> getUserDepartmentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return Set.of();
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        Set<String> departmentRoles = new HashSet<>();

        List<String> departments = Arrays.asList(
                "SALES_REPRESENTATIVE", "WAREHOUSE_STAFF", "ACCOUNTING_STAFF",
                "CUSTOMER_SERVICE", "MARKETING_STAFF"
        );

        userDetails.getAuthorities().forEach(authority -> {
            String roleName = authority.getAuthority();
            if (roleName.startsWith("ROLE_")) {
                roleName = roleName.substring(5);
            }

            if (departments.contains(roleName)) {
                departmentRoles.add(roleName);
            }
        });

        return departmentRoles;
    }

    /**
     * Management seviyesi kontrolü
     */
    public boolean isManagementLevel() {
        return hasAnyRole("SYSTEM_ADMIN", "ADMIN", "MANAGER");
    }

    /**
     * Operasyonel seviye kontrolü
     */
    public boolean isOperationalLevel() {
        return hasDepartmentRole("SALES_REPRESENTATIVE", "WAREHOUSE_STAFF",
                "ACCOUNTING_STAFF", "CUSTOMER_SERVICE", "MARKETING_STAFF");
    }

    /**
     * Multiple role kontrolü
     */
    public boolean hasAnyRole(String... roleNames) {
        return Arrays.stream(roleNames).anyMatch(this::hasRole);
    }

    /**
     * Debug amaçlı - kullanıcının sahip olduğu tüm yetkileri döndürür
     */
    public Set<String> getUserEffectivePermissions() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return Set.of();
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        Set<String> effectivePermissions = new HashSet<>();

        // Direkt permission'ları ekle
        userDetails.getAuthorities().forEach(authority -> {
            String authName = authority.getAuthority();
            if (!authName.startsWith("ROLE_")) {
                effectivePermissions.add(authName);
            }
        });

        // Role-based permission'ları ekle
        userDetails.getAuthorities().forEach(authority -> {
            String roleName = authority.getAuthority();
            if (roleName.startsWith("ROLE_")) {
                roleName = roleName.substring(5);
                Set<String> rolePermissions = ROLE_PERMISSIONS.get(roleName);
                if (rolePermissions != null) {
                    if (rolePermissions.contains("*")) {
                        effectivePermissions.add("ALL_PERMISSIONS_VIA_" + roleName);
                        // Gerçek permission'ları da ekleyelim debug için
                        ROLE_PERMISSIONS.values().forEach(permissions -> {
                            permissions.forEach(perm -> {
                                if (!perm.equals("*")) {
                                    effectivePermissions.add(perm);
                                }
                            });
                        });
                    } else {
                        effectivePermissions.addAll(rolePermissions);
                    }
                }
            }
        });

        return effectivePermissions;
    }
}