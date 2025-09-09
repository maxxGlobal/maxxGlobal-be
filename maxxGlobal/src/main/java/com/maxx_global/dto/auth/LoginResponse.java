package com.maxx_global.dto.auth;

import com.maxx_global.dto.appUser.AppUserResponse;

public record LoginResponse(
        String token,
        AppUserResponse user,
        boolean isDealer
) {}
