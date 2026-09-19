package com.maxx_global.service;

import com.maxx_global.dto.dealer.DealerResponse;
import com.maxx_global.dto.productPrice.ProductPriceMapper;
import com.maxx_global.dto.productPrice.ProductPriceResponse;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductPriceServiceTest {
    private final ProductPriceRepository priceRepository = mock(ProductPriceRepository.class);
    private final ProductPriceMapper priceMapper = mock(ProductPriceMapper.class);
    private final ProductService productService = mock(ProductService.class);
    private final DealerService dealerService = mock(DealerService.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final ProductPriceService service = new ProductPriceService(
            priceRepository, priceMapper, productService, dealerService, variantRepository);

    @Test
    void missingPreferredCurrencyPriceThrowsNotFoundWithoutCallingMapper() {
        when(dealerService.getDealerById(7L)).thenReturn(dealer(CurrencyType.TRY));
        when(priceRepository.findByProductVariantAndDealerIdAndStatus(3L, 7L, EntityStatus.ACTIVE))
                .thenReturn(List.of(price(CurrencyType.USD, true)));

        EntityNotFoundException error = assertThrows(EntityNotFoundException.class,
                () -> service.getVariantPricesForDealer(3L, 7L));

        assertEquals("Bu varyant için bu bayi ve para biriminde fiyat bulunamadı", error.getMessage());
        verifyNoInteractions(priceMapper);
    }

    @Test
    void inactivePreferredCurrencyPriceIsNotReturned() {
        when(dealerService.getDealerById(7L)).thenReturn(dealer(CurrencyType.EUR));
        when(priceRepository.findByProductVariantAndDealerIdAndStatus(3L, 7L, EntityStatus.ACTIVE))
                .thenReturn(List.of(price(CurrencyType.EUR, false)));

        assertThrows(EntityNotFoundException.class, () -> service.getVariantPricesForDealer(3L, 7L));
        verifyNoInteractions(priceMapper);
    }

    @Test
    void activeValidPreferredCurrencyPriceIsMapped() {
        ProductPrice price = price(CurrencyType.EUR, true);
        ProductPriceResponse response = mock(ProductPriceResponse.class);
        when(dealerService.getDealerById(7L)).thenReturn(dealer(CurrencyType.EUR));
        when(priceRepository.findByProductVariantAndDealerIdAndStatus(3L, 7L, EntityStatus.ACTIVE))
                .thenReturn(List.of(price));
        when(priceMapper.toResponseSingle(price)).thenReturn(response);

        assertSame(response, service.getVariantPricesForDealer(3L, 7L));
        verify(priceMapper).toResponseSingle(price);
    }

    private DealerResponse dealer(CurrencyType currency) {
        return new DealerResponse(7L, "Dealer", null, null, null, null,
                List.of(), null, "ACTIVE", currency);
    }

    private ProductPrice price(CurrencyType currency, boolean active) {
        ProductPrice price = new ProductPrice();
        price.setCurrency(currency);
        price.setAmount(BigDecimal.TEN);
        price.setStatus(EntityStatus.ACTIVE);
        price.setIsActive(active);
        return price;
    }
}
