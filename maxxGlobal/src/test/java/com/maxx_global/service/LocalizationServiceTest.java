package com.maxx_global.service;

import com.maxx_global.enums.ApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationServiceTest {
    private final ResourceBundleMessageSource messageSource = messageSource();
    private final LocalizationService localizationService = new LocalizationService(messageSource);

    @Test
    void everyApiErrorCodeHasRealTurkishAndEnglishTranslations() {
        for (ApiErrorCode errorCode : ApiErrorCode.values()) {
            String key = errorCode.getMessageKey();
            String turkish = messageSource.getMessage(key, null, key, Locale.forLanguageTag("tr-TR"));
            String english = messageSource.getMessage(key, null, key, Locale.ENGLISH);

            assertNotEquals(key, turkish, "Missing Turkish translation for " + key);
            assertNotEquals(key, english, "Missing English translation for " + key);
            assertFalse(turkish.isBlank());
            assertFalse(english.isBlank());
        }
    }

    @Test
    void unknownMessageCodeFallsBackWithoutThrowing() {
        assertEquals("missing.localization.key",
                localizationService.getMessage("missing.localization.key", Locale.forLanguageTag("tr-TR")));
    }

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}
