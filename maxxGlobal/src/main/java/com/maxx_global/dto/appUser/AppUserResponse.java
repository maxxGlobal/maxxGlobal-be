package com.maxx_global.dto.appUser;
import com.maxx_global.dto.dealer.DealerSummary;
import com.maxx_global.dto.role.RoleResponse;

import java.time.LocalDateTime;
import java.util.List;

public record AppUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        DealerSummary dealer,
        List<RoleResponse> roles,
        LocalDateTime createdAt,
        String status
) {}

