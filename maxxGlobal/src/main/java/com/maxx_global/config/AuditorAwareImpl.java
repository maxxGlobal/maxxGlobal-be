package com.maxx_global.config;

import com.maxx_global.security.CustomUserDetails;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();

        // Tip kontrolü yapın
        if (principal instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) principal;
            return Optional.of(userDetails.getId());
        } else if (principal instanceof String) {
            // Eğer principal bir String ise (anonymous veya farklı auth durumu)
            // Bu durumda auditor bilgisi alamayız
            return Optional.empty();
        }

        // Diğer durumlar için
        return Optional.empty();
    }

}