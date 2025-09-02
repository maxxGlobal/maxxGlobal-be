package com.maxx_global.dto.dealer;

import com.maxx_global.dto.appUser.UserSummary;
import com.maxx_global.enums.CurrencyType;

import java.time.LocalDateTime;
import java.util.List;

public record DealerResponse(
        Long id,
        String name,
        String fixedPhone,
        String mobilePhone,
        String email,
        String address,
        List<UserSummary> users, // Bayiye bağlı kullanıcı özetleri
        LocalDateTime createdDate,
        String status,
        CurrencyType preferredCurrency
) {}
