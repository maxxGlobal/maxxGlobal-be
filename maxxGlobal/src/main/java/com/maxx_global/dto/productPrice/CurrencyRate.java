package com.maxx_global.dto.productPrice;

public record CurrencyRate(String code, String name, Double forexBuying,
                           Double forexSelling, Double banknoteBuying, Double banknoteSelling) {}
