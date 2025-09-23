package com.maxx_global.controller;

import com.maxx_global.dto.productPrice.CurrencyRate;
import com.maxx_global.service.TcmbService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
public class TcmbController {

    private final TcmbService tcmbService;

    public TcmbController(TcmbService tcmbService) {
        this.tcmbService = tcmbService;
    }

    @GetMapping("/api/kurlar")
    public List<CurrencyRate> all() {
        return tcmbService.getLatestRates();
    }

    @GetMapping("/api/kurlar/{code}")
    public CurrencyRate byCode(@PathVariable String code) {
        Optional<CurrencyRate> found = tcmbService.getLatestRates().stream()
                .filter(r -> r.code().equalsIgnoreCase(code))
                .findFirst();
        return found.orElseThrow(() -> new RuntimeException("Kur bulunamadı: " + code));
    }
}
