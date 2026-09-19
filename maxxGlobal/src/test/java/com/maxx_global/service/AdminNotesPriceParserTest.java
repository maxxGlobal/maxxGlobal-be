package com.maxx_global.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AdminNotesPriceParserTest {
    @ParameterizedTest
    @CsvSource({
            "0 TRY,0",
            "123.4500 USD,123.4500",
            "-7.25 EUR,-7.25",
            "42 TL,42",
            "9.99,9.99"
    })
    void parsesPlainDecimalAndSupportedCurrencyFormats(String input, String expected) {
        assertEquals(new BigDecimal(expected), AdminNotesPriceParser.parse(input));
    }

    @Test
    void unavailableAndEmptyPricesRemainUnknown() {
        assertNull(AdminNotesPriceParser.parse(null));
        assertNull(AdminNotesPriceParser.parse(""));
        assertNull(AdminNotesPriceParser.parse("Fiyat bilgisi bulunmuyor"));
    }

    @Test
    void malformedPriceIsDiagnosableInsteadOfBecomingZero() {
        assertThrows(IllegalArgumentException.class, () -> AdminNotesPriceParser.parse("not-a-price TRY"));
    }
}
