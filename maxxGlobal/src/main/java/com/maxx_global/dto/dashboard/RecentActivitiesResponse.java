package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Son aktiviteler")
public record RecentActivitiesResponse(
        @Schema(description = "Aktivite listesi")
        List<ActivityData> activities,

        @Schema(description = "Toplam aktivite sayısı", example = "150")
        Integer totalActivities
) {}