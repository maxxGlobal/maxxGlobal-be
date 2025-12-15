package com.maxx_global.config;

import com.maxx_global.enums.Language;
import com.maxx_global.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Header'dan gelen dil isteğini ve kullanıcının tercih ettiği dili dikkate alan resolver.
 */
public class PreferredLocaleResolver extends AcceptHeaderLocaleResolver {

    private static final Locale DEFAULT_LOCALE = Language.TR.toLocale();

    public PreferredLocaleResolver() {
        setDefaultLocale(DEFAULT_LOCALE);
        setSupportedLocales(Language.supportedLanguages().stream().map(Language::toLocale).collect(Collectors.toList()));
    }

    @Override
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
        Locale headerLocale = resolveFromHeader(request.getHeader("Accept-Language"));
        if (headerLocale != null) {
            return headerLocale;
        }

        Language userLanguage = extractLanguageFromPrincipal();
        if (userLanguage != null) {
            return userLanguage.toLocale();
        }

        return DEFAULT_LOCALE;
    }

    @Override
    public void setLocale(@NonNull HttpServletRequest request, @Nullable HttpServletResponse response, @Nullable Locale locale) {
        super.setLocale(request, response, locale);
    }

    private Locale resolveFromHeader(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }

        List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
        for (Locale.LanguageRange range : ranges) {
            Locale candidate = Locale.forLanguageTag(range.getRange());
            if (Language.supports(candidate)) {
                return Language.fromLocale(candidate)
                        .map(Language::toLocale)
                        .orElse(null);
            }
        }
        return null;
    }

    private Language extractLanguageFromPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return customUserDetails.getPreferredLanguage();
        }
        return null;
    }
}
