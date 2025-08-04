package com.maxx_global.dto.appUser;
import com.maxx_global.dto.dealer.DealerSummary;

import java.time.LocalDateTime;
import java.util.List;

public record AppUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        DealerSummary dealer, // Sadece bayi özeti
        List<String> roles,   // Rol isimleri
        LocalDateTime createdDate,
        String status
) {}
