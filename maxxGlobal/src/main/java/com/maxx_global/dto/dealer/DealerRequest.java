package com.maxx_global.dto.dealer;

public record DealerRequest(
        String name,
        String fixedPhone,
        String mobilePhone,
        String email,
        String address
) {}
