package com.maxx_global.service;

import com.maxx_global.dto.cart.CartItemRequest;
import com.maxx_global.dto.cart.CartItemUpdateRequest;
import com.maxx_global.entity.*;
import com.maxx_global.enums.*;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceNullablePriceIdTest {
    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductPriceRepository productPriceRepository;
    @Mock ProductVariantRepository productVariantRepository;
    @Mock DealerService dealerService;
    @Mock LocalizationService localizationService;
    private CartService service;
    private AppUser user;
    private Cart cart;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, cartItemRepository, productPriceRepository,
                productVariantRepository, dealerService, localizationService);
        Dealer dealer = new Dealer();
        dealer.setId(1L);
        dealer.setPreferredCurrency(CurrencyType.TRY);
        user = new AppUser();
        user.setId(3L);
        user.setDealer(dealer);
        Product product = new Product();
        product.setStatus(EntityStatus.ACTIVE);
        variant = new ProductVariant();
        variant.setId(2L);
        variant.setStatus(EntityStatus.ACTIVE);
        variant.setStockQuantity(10);
        variant.setProduct(product);
        cart = new Cart();
        cart.setId(4L);
        cart.setUser(user);
        cart.setDealer(dealer);
        cart.setStatus(EntityStatus.ACTIVE);
    }

    @Test
    void addAndUpdateUnpricedItemNeverQueriesNullablePriceId() {
        when(productVariantRepository.findById(2L)).thenReturn(Optional.of(variant));
        when(productPriceRepository.findByVariantIdAndDealerIdAndCurrency(2L, 1L, CurrencyType.TRY))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndDealerIdAndStatus(3L, 1L, EntityStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductVariantIdAndStatus(4L, 2L, EntityStatus.ACTIVE))
                .thenReturn(Optional.empty());

        service.addItem(user, new CartItemRequest(1L, 2L, null, 1));
        CartItem item = cart.getItems().iterator().next();
        item.setId(5L);
        when(cartItemRepository.findByIdAndCartIdAndCartUserIdAndStatus(5L, 4L, 3L, EntityStatus.ACTIVE))
                .thenReturn(Optional.of(item));
        service.updateItemQuantity(user, 5L, new CartItemUpdateRequest(2));

        assertEquals(2, item.getQuantity());
        assertNull(item.getProductPrice());
        assertNull(item.getUnitPrice());
        assertNull(item.getTotalPrice());
        verify(productPriceRepository, never()).findById(isNull());
        verify(productPriceRepository, never()).findByIdAndStatus(isNull(), any());
    }

    @Test
    void nullVariantAndItemIdsFailBeforeRepositoryCalls() {
        BusinessException variantError = assertThrows(BusinessException.class,
                () -> service.addItem(user, new CartItemRequest(1L, null, null, 1)));
        assertEquals(ApiErrorCode.PRODUCT_VARIANT_NOT_FOUND, variantError.getErrorCode());
        assertThrows(BusinessException.class,
                () -> service.removeItem(user, null));
        verify(productVariantRepository, never()).findById(isNull());
        verify(cartItemRepository, never()).findByIdAndCartIdAndCartUserIdAndStatus(
                isNull(), anyLong(), anyLong(), any());
    }
}
