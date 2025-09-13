//package com.maxx_global.event;
//
//import com.maxx_global.entity.Product;
//import com.maxx_global.entity.AppUser;
//import com.maxx_global.enums.StockMovementType;
//
///**
// * Ürün stok değişikliği eventi
// * Bu event StockTracker tarafından dinlenir ve otomatik stok hareketi oluşturur
// */
//public record ProductStockChangeEvent(Product product, Integer oldStock, Integer newStock,
//                                      StockMovementType movementType, String reason, AppUser performedBy,
//                                      String referenceType, Long referenceId) {
//
//    @Override
//    public String toString() {
//        return "ProductStockChangeEvent{" +
//                "product=" + (product != null ? product.getCode() : "null") +
//                ", oldStock=" + oldStock +
//                ", newStock=" + newStock +
//                ", movementType=" + movementType +
//                ", reason='" + reason + '\'' +
//                ", performedBy=" + (performedBy != null ? performedBy.getId() : "null") +
//                ", referenceType='" + referenceType + '\'' +
//                ", referenceId=" + referenceId +
//                '}';
//    }
//}