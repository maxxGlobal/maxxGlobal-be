package com.maxx_global.dto.stock;

import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.dto.product.ProductSummary;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Stok hareketi detay bilgisi")
public record StockMovementResponse(
        @Schema(description = "Hareket ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün bilgisi")
        ProductSummary product,

        @Schema(description = "Hareket tipi", example = "STOK GİRİŞİ")
        String movementType,

        @Schema(description = "Hareket tipi kodu", example = "STOCK_IN")
        String movementTypeCode,

        @Schema(description = "Miktar", example = "100")
        Integer quantity,

        @Schema(description = "Önceki stok", example = "50")
        Integer previousStock,

        @Schema(description = "Yeni stok", example = "150")
        Integer newStock,

        @Schema(description = "Birim maliyet", example = "25.50")
        BigDecimal unitCost,

        @Schema(description = "Toplam maliyet", example = "2550.00")
        BigDecimal totalCost,

        @Schema(description = "Batch/Lot numarası", example = "BATCH-2024-001")
        String batchNumber,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Referans tipi", example = "MANUAL")
        String referenceType,

        @Schema(description = "Referans ID'si", example = "123")
        Long referenceId,

        @Schema(description = "Doküman numarası", example = "DOC-2024-001")
        String documentNumber,

        @Schema(description = "Notlar")
        String notes,

        @Schema(description = "İşlemi yapan kullanıcı")
        UserSummary performedBy,

        @Schema(description = "Hareket tarihi", example = "2024-01-15T10:30:00")
        LocalDateTime movementDate,

        @Schema(description = "Oluşturulma tarihi", example = "2024-01-15T10:30:00")
        LocalDateTime createdDate,

        @Schema(description = "Durum", example = "AKTIF")
        String status
) {}