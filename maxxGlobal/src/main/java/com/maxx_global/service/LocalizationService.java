package com.maxx_global.service;

import com.maxx_global.entity.AppUser;
import com.maxx_global.enums.Language;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LocalizationService {

    private final MessageSource messageSource;

    public LocalizationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public Locale getDefaultLocale() {
        return Language.TR.toLocale();
    }

    public Locale getCurrentRequestLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        if (locale != null && Language.supports(locale)) {
            return Language.fromLocale(locale).map(Language::toLocale).orElse(getDefaultLocale());
        }
        return getDefaultLocale();
    }

    public Language getCurrentLanguage() {
        return Language.fromLocale(getCurrentRequestLocale()).orElse(Language.TR);
    }

    public Locale getLocaleForUser(AppUser user) {
        Locale requestLocale = LocaleContextHolder.getLocale();
        if (requestLocale != null && Language.supports(requestLocale)) {
            return Language.fromLocale(requestLocale).map(Language::toLocale).orElse(getDefaultLocale());
        }
        if (user != null && user.getPreferredLanguage() != null) {
            return user.getPreferredLanguage().toLocale();
        }
        return getDefaultLocale();
    }

    public Language getLanguageForUser(AppUser user) {
        Locale requestLocale = LocaleContextHolder.getLocale();
        if (requestLocale != null && Language.supports(requestLocale)) {
            return Language.fromLocale(requestLocale).orElse(Language.TR);
        }
        if (user != null && user.getPreferredLanguage() != null) {
            return user.getPreferredLanguage();
        }
        return Language.TR;
    }

    public Language getLanguage(Locale locale) {
        return Language.fromLocale(locale).orElse(getCurrentLanguage());
    }

    public Language getLanguageOrDefault(Language language) {
        return language != null ? language : Language.TR;
    }

    public String getMessage(String code, Object... args) {
        return getMessage(code, getCurrentRequestLocale(), args);
    }

    public String getMessage(String code, Locale locale, Object... args) {
        Locale resolvedLocale = locale != null ? locale : getCurrentRequestLocale();
        return messageSource.getMessage(code, args, resolvedLocale);
    }

    public String resolveText(AppUser user, String textTr, String textEn) {
        Language language = getLanguageForUser(user);
        if (language == Language.EN) {
            return fallback(textEn, textTr);
        }
        return fallback(textTr, textEn);
    }

    private String fallback(String primary, String secondary) {
        if (primary != null && !primary.trim().isEmpty()) {
            return primary;
        }
        return secondary != null ? secondary : "";
    }
}
