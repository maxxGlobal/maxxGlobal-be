package com.maxx_global.dto.dealer;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dealer basit bilgiler - dropdown ve select listeleri için")
public record DealerSimple(
        @Schema(description = "Dealer ID'si", example = "1")
        Long id,

        @Schema(description = "Dealer adı", example = "ABC Medikal Ltd.")
        String name
) {}