package com.maxx_global.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** Narrow parser for the machine-written price fragments in order admin notes. */
final class AdminNotesPriceParser {
    private static final String UNAVAILABLE_PRICE = "Fiyat bilgisi bulunmuyor";
    private static final Set<String> CURRENCIES = Set.of("TRY", "USD", "EUR", "TL");

    private AdminNotesPriceParser() {
    }

    static BigDecimal parse(String value) {
        if (value == null) {
            return null;
        }

        String price = value.trim();
        if (price.isEmpty() || UNAVAILABLE_PRICE.equalsIgnoreCase(price)) {
            return null;
        }

        String[] parts = price.split("\\s+");
        if (parts.length == 2 && CURRENCIES.contains(parts[1].toUpperCase(Locale.ROOT))) {
            price = parts[0];
        } else if (parts.length != 1) {
            throw new IllegalArgumentException("Unexpected admin-note price format");
        }

        return new BigDecimal(price);
    }
}
