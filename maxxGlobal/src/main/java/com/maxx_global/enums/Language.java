package com.maxx_global.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Uygulama tarafından desteklenen diller.
 */
public enum Language {
    TR(new Locale("tr", "TR")),
    EN(Locale.US);

    private static final List<Language> SUPPORTED = List.of(values());

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }

    public Locale toLocale() {
        return locale;
    }

    public static Language fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(language -> language.name().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }

    public static Optional<Language> fromLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return SUPPORTED.stream()
                .filter(language -> language.name().equalsIgnoreCase(locale.getLanguage())
                        || language.toLocale().getLanguage().equalsIgnoreCase(locale.getLanguage()))
                .findFirst();
    }

    public static boolean supports(Locale locale) {
        return fromLocale(locale).isPresent();
    }

    public static List<Language> supportedLanguages() {
        return SUPPORTED;
    }
}
