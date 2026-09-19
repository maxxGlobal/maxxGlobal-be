package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.productPrice.ProductPriceResponse;
import com.maxx_global.service.ProductPriceService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ProductPriceControllerTest {
    @Test
    void missingVariantPriceReturnsNotFoundInsteadOfServerError() {
        ProductPriceService service = mock(ProductPriceService.class);
        String message = "Bu varyant için bu bayi ve para biriminde fiyat bulunamadı";
        when(service.getVariantPricesForDealer(3L, 7L)).thenThrow(new EntityNotFoundException(message));

        ResponseEntity<BaseResponse<ProductPriceResponse>> response =
                new ProductPriceController(service).getVariantPricesForDealer(3L, 7L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(message, response.getBody().getMessage());
    }
}
