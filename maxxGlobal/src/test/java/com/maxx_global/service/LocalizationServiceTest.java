package com.maxx_global.service;

import com.maxx_global.enums.ApiErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LocalizationServiceTest {

    private LocalizationService localizationService;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        localizationService = new LocalizationService(source);
    }

    @Test
    void everyApiErrorCodeHasTurkishAndEnglishMessages() {
        for (ApiErrorCode errorCode : ApiErrorCode.values()) {
            String key = errorCode.getMessageKey();
            String turkish = localizationService.getMessage(key, Locale.forLanguageTag("tr-TR"));
            String english = localizationService.getMessage(key, Locale.ENGLISH);

            assertNotEquals(key, turkish, () -> "Missing Turkish message for " + key);
            assertNotEquals(key, english, () -> "Missing English message for " + key);
        }
    }

    @Test
    void validationMessageResolvesForTurkishLocale() {
        assertEquals("Gönderilen bilgiler geçersiz.",
                localizationService.getMessage("error.validation", Locale.forLanguageTag("tr-TR")));
    }

    @Test
    void unknownMessageKeySafelyFallsBackToKey() {
        assertDoesNotThrow(() -> assertEquals("error.missing_key",
                localizationService.getMessage("error.missing_key", Locale.forLanguageTag("tr-TR"))));
    }
}
