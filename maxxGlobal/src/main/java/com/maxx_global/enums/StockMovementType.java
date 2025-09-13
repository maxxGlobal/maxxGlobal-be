package com.maxx_global.enums;

import java.util.Set;

public enum StockMovementType {
    // Mevcut değerler
    STOCK_IN("STOCK_IN", "Stok Girişi", true, true),
    STOCK_OUT("STOCK_OUT", "Stok Çıkışı", false, false),
    SALE("SALE", "Satış", false, false),
    RETURN_IN("RETURN_IN", "İade Girişi", true, false),
    ADJUSTMENT_IN("ADJUSTMENT_IN", "Düzeltme Girişi", true, false),
    ADJUSTMENT_OUT("ADJUSTMENT_OUT", "Düzeltme Çıkışı", false, false),

    // YENİ EKLENECEK DEĞERLER
    PURCHASE("PURCHASE", "Satın Alma", true, true),
    TRANSFER_IN("TRANSFER_IN", "Transfer Girişi", true, false),
    TRANSFER_OUT("TRANSFER_OUT", "Transfer Çıkışı", false, false),
    PRODUCTION_IN("PRODUCTION_IN", "Üretim Girişi", true, true),
    PRODUCTION_OUT("PRODUCTION_OUT", "Üretim Çıkışı", false, false),
    WASTE("WASTE", "Fire/Zayi", false, false),
    DAMAGED("DAMAGED", "Hasar", false, false),
    EXPIRED("EXPIRED", "Süresi Dolmuş", false, false),

    // OTOMATIK HAREKETLER
    ORDER_RESERVED("ORDER_RESERVED", "Sipariş Rezervasyonu", false, false),
    ORDER_CANCELLED_RETURN("ORDER_CANCELLED_RETURN", "İptal Edilen Sipariş İadesi", true, false),

    // EXCEL VE TOPLU İŞLEMLER
    EXCEL_IMPORT("EXCEL_IMPORT", "Excel İmport", true, true),
    EXCEL_UPDATE("EXCEL_UPDATE", "Excel Güncelleme", true, true),
    BULK_UPDATE("BULK_UPDATE", "Toplu Güncelleme", true, true),

    // SİSTEM DÜZELTME HAREKETLERİ
    SYSTEM_CORRECTION("SYSTEM_CORRECTION", "Sistem Düzeltmesi", true, false),
    INITIAL_STOCK("INITIAL_STOCK", "Başlangıç Stoku", true, true),
    STOCK_COUNT("STOCK_COUNT","Stok toplamı" , true,true );

    private final String code;
    private final String displayName;
    private final boolean isStockIncrease;
    private final boolean affectsCost;

    StockMovementType(String code, String displayName, boolean isStockIncrease, boolean affectsCost) {
        this.code = code;
        this.displayName = displayName;
        this.isStockIncrease = isStockIncrease;
        this.affectsCost = affectsCost;
    }

    // Getter metodları
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public boolean isStockIncrease() { return isStockIncrease; }
    public boolean affectsCost() { return affectsCost; }

    /**
     * Order işlemleri için kullanılan hareket tipleri
     */
    public static Set<StockMovementType> getOrderRelatedTypes() {
        return Set.of(SALE, RETURN_IN, ORDER_RESERVED, ORDER_CANCELLED_RETURN);
    }

    /**
     * Manuel müdahale gerektirmeyen otomatik hareket tipleri
     */
    public static Set<StockMovementType> getAutomaticTypes() {
        return Set.of(SALE, RETURN_IN, ORDER_RESERVED, ORDER_CANCELLED_RETURN,
                EXCEL_IMPORT, EXCEL_UPDATE, BULK_UPDATE, SYSTEM_CORRECTION);
    }

    /**
     * Maliyet hesaplamasına etki eden hareket tipleri
     */
    public static Set<StockMovementType> getCostAffectingTypes() {
        return Set.of(STOCK_IN, PURCHASE, PRODUCTION_IN, EXCEL_IMPORT,
                EXCEL_UPDATE, BULK_UPDATE, INITIAL_STOCK);
    }
}