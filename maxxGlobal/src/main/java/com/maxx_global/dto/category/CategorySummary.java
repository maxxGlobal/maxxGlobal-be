package com.maxx_global.dto.category;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kategori özet bilgileri - dropdown ve select listeleri için")
public record CategorySummary(
        @Schema(description = "Kategori ID'si", example = "1")
        Long id,

        @Schema(description = "Kategori adı", example = "Implantlar")
        String name,

        @Schema(description = "Alt kategorisi var mı?", example = "true")
        Boolean hasChildren
) {}