package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Sistem uyarıları")
public record AlertsResponse(
        @Schema(description = "Toplam uyarı sayısı", example = "15")
        Integer totalAlerts,

        @Schema(description = "Kritik uyarı sayısı", example = "3")
        Integer criticalAlerts,

        @Schema(description = "Uyarı listesi")
        List<AlertData> alerts
) {}