package com.maxx_global.service;

import com.maxx_global.dto.cart.CartItemRequest;
import com.maxx_global.dto.cart.CartItemResponse;
import com.maxx_global.dto.cart.CartItemUpdateRequest;
import com.maxx_global.dto.cart.CartResponse;
import com.maxx_global.dto.order.OrderProductRequest;
import com.maxx_global.entity.*;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.ApiErrorCode;
import com.maxx_global.exception.BusinessException;
import com.maxx_global.repository.CartItemRepository;
import com.maxx_global.repository.CartRepository;
import com.maxx_global.repository.ProductPriceRepository;
import com.maxx_global.repository.ProductVariantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CartService {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(CartService.class.getName());

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductPriceRepository productPriceRepository;
    private final ProductVariantRepository productVariantRepository;
    private final DealerService dealerService;
    private final LocalizationService localizationService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductPriceRepository productPriceRepository,
                       ProductVariantRepository productVariantRepository,
                       DealerService dealerService,
                       LocalizationService localizationService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productPriceRepository = productPriceRepository;
        this.productVariantRepository = productVariantRepository;
        this.dealerService = dealerService;
        this.localizationService = localizationService;
    }

    @Transactional
    public CartResponse addItem(AppUser user, CartItemRequest request) {
        validateDealer(user, request.dealerId());

        // Kullanıcının fiyat görme yetkisi var mı kontrol et
        boolean hasPricePermission = userHasPricePermission(user);
        logger.info("User price permission check in cart: " + hasPricePermission);

        ProductVariant variant = loadActiveVariant(request.productVariantId());
        ProductPrice productPrice = resolveProductPrice(variant, user.getDealer(), request.productPriceId());

        if (!variant.hasEnoughStock(request.quantity())) {
            throw new BusinessException(ApiErrorCode.INSUFFICIENT_STOCK,
                    variant.getDisplayName(), request.quantity(), variant.getStockQuantity());
        }

        Cart cart = getOrCreateActiveCart(user, request.dealerId());
        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductVariantIdAndStatus(cart.getId(), variant.getId(), EntityStatus.ACTIVE)
                .orElse(null);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProductVariant(variant);
            cartItem.setQuantity(request.quantity());
            cartItem.setProductPrice(productPrice);
            if (productPrice != null) {
                cartItem.setUnitPrice(productPrice.getAmount());
                cartItem.recalculateTotals();
            } else {
                cartItem.setUnitPrice(null);
                cartItem.setTotalPrice(null);
                logger.info("Cart item added WITHOUT prices for user without permission");
            }

            cart.addItem(cartItem);
        } else {
            int newQuantity = cartItem.getQuantity() + request.quantity();
            if (!variant.hasEnoughStock(newQuantity)) {
                throw new BusinessException(ApiErrorCode.INSUFFICIENT_STOCK,
                        variant.getDisplayName(), newQuantity, variant.getStockQuantity());
            }
            cartItem.setQuantity(newQuantity);
            cartItem.setProductPrice(productPrice);
            if (productPrice != null) {
                cartItem.setUnitPrice(productPrice.getAmount());
                cartItem.recalculateTotals();
            } else {
                cartItem.setUnitPrice(null);
                cartItem.setTotalPrice(null);
            }
        }

        cart.touch();
        cartRepository.save(cart);

        Cart refreshed = getActiveCartEntity(user, request.dealerId());
        return mapToResponse(refreshed, user);
    }

    @Transactional
    public CartResponse getActiveCart(AppUser user, Long dealerId) {
        validateDealer(user, dealerId);
        Cart cart = getActiveCartEntity(user, dealerId);
        return mapToResponse(cart, user);
    }

    @Transactional
    public CartResponse updateItemQuantity(AppUser user, Long cartItemId, CartItemUpdateRequest request) {
        if (cartItemId == null || cartItemId <= 0) {
            throw new BusinessException(ApiErrorCode.CART_NOT_FOUND);
        }
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BusinessException(ApiErrorCode.INVALID_QUANTITY);
        }
        // Kullanıcının fiyat görme yetkisi var mı kontrol et
        boolean hasPricePermission = userHasPricePermission(user);

        Cart cart = cartRepository.findByUserIdAndDealerIdAndStatus(
                        user.getId(),
                        user.getDealer().getId(),
                        EntityStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.CART_NOT_FOUND));

        CartItem cartItem = cartItemRepository
                .findByIdAndCartIdAndCartUserIdAndStatus(cartItemId, cart.getId(), user.getId(), EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException(
                    localizationService.getMessage("cart.error.item_not_found", localizationService.getLocaleForUser(user))
                ));

        ProductVariant variant = cartItem.getProductVariant();

        if (!variant.hasEnoughStock(request.quantity())) {
            throw new BusinessException(ApiErrorCode.INSUFFICIENT_STOCK,
                    variant.getDisplayName(), request.quantity(), variant.getStockQuantity());
        }

        cartItem.setQuantity(request.quantity());

        ProductPrice currentPrice = resolveProductPrice(variant, cart.getDealer(), null);
        cartItem.setProductPrice(currentPrice);
        if (currentPrice != null) {
            cartItem.setUnitPrice(currentPrice.getAmount());
            cartItem.recalculateTotals();
        } else {
            cartItem.setUnitPrice(null);
            cartItem.setTotalPrice(null);
        }

        cart.touch();
        cartRepository.save(cart);

        Cart refreshed = getActiveCartEntity(user, cart.getDealer().getId());
        return mapToResponse(refreshed, user);
    }

    @Transactional
    public void removeItem(AppUser user, Long cartItemId) {
        if (cartItemId == null || cartItemId <= 0) {
            throw new BusinessException(ApiErrorCode.CART_NOT_FOUND);
        }
        Cart cart = cartRepository.findByUserIdAndDealerIdAndStatus(
                        user.getId(),
                        user.getDealer().getId(),
                        EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Aktif sepet bulunamadı"));

        CartItem cartItem = cartItemRepository
                .findByIdAndCartIdAndCartUserIdAndStatus(cartItemId, cart.getId(), user.getId(), EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Sepet öğesi bulunamadı"));

        cart.removeItem(cartItem);
        cart.touch();
        cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(AppUser user, Long dealerId) {
        validateDealer(user, dealerId);
        Cart cart = cartRepository.findByUserIdAndDealerIdAndStatus(user.getId(), dealerId, EntityStatus.ACTIVE)
                .orElse(null);
        if (cart != null) {
            cart.getItems().clear();
            cart.touch();
            cartRepository.save(cart);
        }
    }

    @Transactional
    public void markCartAsOrdered(Cart cart) {
        cart.getItems().clear();
        cart.setStatus(EntityStatus.INACTIVE);
        cart.touch();
        cartRepository.save(cart);
    }

    public List<OrderProductRequest> convertCartItemsToOrderProducts(Cart cart) {
        return cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getId))
                .map(item -> new OrderProductRequest(
                        item.getProductVariant().getId(),
                        item.getProductPrice() != null ? item.getProductPrice().getId() : null,
                        item.getQuantity()))
                .collect(Collectors.toList());
    }

    public Cart getValidatedCartForCheckout(Long cartId, AppUser user, Long dealerId) {
        if (cartId == null || cartId <= 0) {
            throw new BusinessException(ApiErrorCode.CART_NOT_FOUND);
        }
        Cart cart = cartRepository.findByIdAndUserIdAndStatus(cartId, user.getId(), EntityStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.CART_NOT_FOUND));

        if (!cart.getDealer().getId().equals(dealerId)) {
            throw new BusinessException(ApiErrorCode.DEALER_MISMATCH);
        }

        if (cart.getItems().isEmpty()) {
            throw new BusinessException(ApiErrorCode.CART_EMPTY);
        }

        return cart;
    }

    private Cart getOrCreateActiveCart(AppUser user, Long dealerId) {
        return cartRepository.findByUserIdAndDealerIdAndStatus(user.getId(), dealerId, EntityStatus.ACTIVE)
                .orElseGet(() -> {
                    dealerService.getDealerById(dealerId);
                    Cart cart = new Cart();
                    cart.setUser(user);
                    cart.setDealer(user.getDealer());
                    cart.setStatus(EntityStatus.ACTIVE);
                    cart.setLastActivityAt(LocalDateTime.now());
                    return cartRepository.save(cart);
                });
    }

    private Cart getActiveCartEntity(AppUser user, Long dealerId) {
        return cartRepository.findByUserIdAndDealerIdAndStatus(user.getId(), dealerId, EntityStatus.ACTIVE)
                .orElseGet(() -> getOrCreateActiveCart(user, dealerId));
    }

    private void validateDealer(AppUser user, Long dealerId) {
        if (user.getDealer() == null) {
            throw new BusinessException(ApiErrorCode.DEALER_MISMATCH);
        }
        if (!user.getDealer().getId().equals(dealerId)) {
            throw new BusinessException(ApiErrorCode.DEALER_MISMATCH);
        }
    }

    private ProductVariant loadActiveVariant(Long variantId) {
        if (variantId == null || variantId <= 0) {
            throw new BusinessException(ApiErrorCode.PRODUCT_VARIANT_NOT_FOUND);
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ApiErrorCode.PRODUCT_VARIANT_NOT_FOUND));
        if (variant.getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException(ApiErrorCode.PRODUCT_VARIANT_INACTIVE);
        }
        if (variant.getProduct() == null || variant.getProduct().getStatus() != EntityStatus.ACTIVE) {
            throw new BusinessException(ApiErrorCode.PRODUCT_INACTIVE);
        }
        return variant;
    }

    private ProductPrice resolveProductPrice(ProductVariant variant, Dealer dealer, Long suppliedPriceId) {
        ProductPrice resolved = productPriceRepository.findByVariantIdAndDealerIdAndCurrency(
                        variant.getId(), dealer.getId(), dealer.getPreferredCurrency())
                .filter(price -> isValidPrice(price, variant, dealer))
                .orElse(null);
        if (suppliedPriceId != null) {
            ProductPrice supplied = productPriceRepository.findById(suppliedPriceId)
                    .orElseThrow(() -> new EntityNotFoundException("Ürün fiyatı bulunamadı: " + suppliedPriceId));
            if (!isValidPrice(supplied, variant, dealer) || resolved == null
                    || !resolved.getId().equals(supplied.getId())) {
                throw new BusinessException(ApiErrorCode.PRICE_MISMATCH);
            }
        }
        return resolved;
    }

    private boolean isValidPrice(ProductPrice price, ProductVariant variant, Dealer dealer) {
        return price.getStatus() == EntityStatus.ACTIVE && Boolean.TRUE.equals(price.getIsActive())
                && price.isValidNow() && price.getProductVariant() != null
                && Objects.equals(price.getProductVariant().getId(), variant.getId())
                && price.getDealer() != null && Objects.equals(price.getDealer().getId(), dealer.getId())
                && price.getCurrency() == dealer.getPreferredCurrency();
    }

    /**
     * Kullanıcının fiyat görme yetkisi olup olmadığını kontrol eder
     */
    private boolean userHasPricePermission(AppUser user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }

        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> "PRICE_READ".equals(permission.getName()));
    }

    private CartResponse mapToResponse(Cart cart, AppUser user) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getId))
                .map(item -> mapItem(item, userHasPricePermission(user)))
                .collect(Collectors.toList());

        // Fiyat yetkisi yoksa subtotal hesaplama
        boolean canShowCompletePrices = userHasPricePermission(user) && itemResponses.stream()
                .allMatch(item -> item.totalPrice() != null);
        BigDecimal subtotal = canShowCompletePrices ? itemResponses.stream()
                .map(CartItemResponse::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add) : null;

        String currency = itemResponses.stream()
                .map(CartItemResponse::currency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(cart.getDealer() != null && cart.getDealer().getPreferredCurrency() != null
                        ? cart.getDealer().getPreferredCurrency().name() : null);

        int totalItems = cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        return new CartResponse(
                cart.getId(),
                cart.getDealer() != null ? cart.getDealer().getId() : null,
                cart.getDealer() != null ? cart.getDealer().getName() : null,
                cart.getLastActivityAt(),
                subtotal,
                currency,
                totalItems,
                itemResponses
        );
    }

    private CartItemResponse mapItem(CartItem item, boolean showPrices) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant != null ? variant.getProduct() : null;

        String imageUrl = null;
        if (product != null && product.getImages() != null && !product.getImages().isEmpty()) {
            imageUrl = product.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .findFirst()
                    .or(() -> product.getImages().stream().findFirst())
                    .map(ProductImage::getImageUrl)
                    .orElse(null);
        }

        return new CartItemResponse(
                item.getId(),
                product != null ? product.getId() : null,
                product != null ? product.getName() : null,
                variant != null ? variant.getId() : null,
                variant != null ? variant.getSku() : null,
                variant != null ? variant.getSize() : null,
                item.getProductPrice() != null ? item.getProductPrice().getId() : null,
                item.getQuantity(),
                variant != null ? variant.getStockQuantity() : null,
                showPrices ? item.getUnitPrice() : null,
                showPrices ? item.getTotalPrice() : null,
                item.getProductPrice() != null && item.getProductPrice().getCurrency() != null
                        ? item.getProductPrice().getCurrency().name()
                        : null,
                imageUrl
        );
    }
}
