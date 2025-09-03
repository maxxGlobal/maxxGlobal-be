package com.maxx_global.enums;

public enum NotificationType {
    // Sipariş bildirimleri
    ORDER_CREATED("Yeni Sipariş", "order"),
    ORDER_APPROVED("Sipariş Onaylandı", "order"),
    ORDER_REJECTED("Sipariş Reddedildi", "order"),
    ORDER_SHIPPED("Sipariş Kargoya Verildi", "order"),
    ORDER_DELIVERED("Sipariş Teslim Edildi", "order"),
    ORDER_CANCELLED("Sipariş İptal Edildi", "order"),
    ORDER_EDITED("Sipariş Düzenlendi", "order"),

    // Sistem bildirimleri
    SYSTEM_MAINTENANCE("Sistem Bakımı", "system"),
    SYSTEM_UPDATE("Sistem Güncellemesi", "system"),

    // Ürün bildirimleri
    PRODUCT_LOW_STOCK("Düşük Stok", "product"),
    PRODUCT_OUT_OF_STOCK("Stok Bitti", "product"),
    PRODUCT_PRICE_CHANGED("Fiyat Değişikliği", "product"),

    // Kullanıcı bildirimleri
    PROFILE_UPDATED("Profil Güncellendi", "user"),
    PASSWORD_CHANGED("Şifre Değiştirildi", "user"),

    // Genel bilgilendirme
    ANNOUNCEMENT("Duyuru", "info"),
    PROMOTION("Kampanya", "promotion");

    private final String displayName;
    private final String category;

    NotificationType(String displayName, String category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
}