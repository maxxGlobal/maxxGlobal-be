package com.maxx_global.service;

import com.maxx_global.dto.cart.CartItemRequest;
import com.maxx_global.dto.cart.CartItemResponse;
import com.maxx_global.dto.cart.CartItemUpdateRequest;
import com.maxx_global.dto.cart.CartResponse;
import com.maxx_global.dto.order.OrderProductRequest;
import com.maxx_global.entity.*;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.CartItemRepository;
import com.maxx_global.repository.CartRepository;
import com.maxx_global.repository.ProductPriceRepository;
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

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductPriceRepository productPriceRepository;
    private final DealerService dealerService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductPriceRepository productPriceRepository,
                       DealerService dealerService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productPriceRepository = productPriceRepository;
        this.dealerService = dealerService;
    }

    @Transactional
    public CartResponse addItem(AppUser user, CartItemRequest request) {
        validateDealer(user, request.dealerId());

        ProductPrice productPrice = loadActiveProductPrice(request.productPriceId());
        ProductVariant variant = ensureVariant(productPrice);

        if (!Objects.equals(productPrice.getDealer().getId(), request.dealerId())) {
            throw new IllegalArgumentException("Fiyat seçilen bayi ile eşleşmiyor");
        }

        if (!variant.hasEnoughStock(request.quantity())) {
            throw new IllegalArgumentException(String.format(
                    "Yetersiz stok (%s): İstenen %d, mevcut %d",
                    variant.getDisplayName(),
                    request.quantity(),
                    variant.getStockQuantity()
            ));
        }

        Cart cart = getOrCreateActiveCart(user, request.dealerId());
        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductPriceIdAndStatus(cart.getId(), productPrice.getId(), EntityStatus.ACTIVE)
                .orElse(null);

        if (cartItem == null) {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProductVariant(variant);
            cartItem.setProductPrice(productPrice);
            cartItem.setQuantity(request.quantity());
            cartItem.setUnitPrice(productPrice.getAmount());
            cartItem.recalculateTotals();
            cart.addItem(cartItem);
        } else {
            int newQuantity = cartItem.getQuantity() + request.quantity();
            if (!variant.hasEnoughStock(newQuantity)) {
                throw new IllegalArgumentException(String.format(
                        "Yetersiz stok (%s): İstenen %d, mevcut %d",
                        variant.getDisplayName(),
                        newQuantity,
                        variant.getStockQuantity()
                ));
            }
            cartItem.setQuantity(newQuantity);
            cartItem.setUnitPrice(productPrice.getAmount());
            cartItem.recalculateTotals();
        }

        cart.touch();
        cartRepository.save(cart);

        Cart refreshed = getActiveCartEntity(user, request.dealerId());
        return mapToResponse(refreshed);
    }

    @Transactional(readOnly = true)
    public CartResponse getActiveCart(AppUser user, Long dealerId) {
        validateDealer(user, dealerId);
        Cart cart = getActiveCartEntity(user, dealerId);
        return mapToResponse(cart);
    }

    @Transactional
    public CartResponse updateItemQuantity(AppUser user, Long cartItemId, CartItemUpdateRequest request) {
        Cart cart = cartRepository.findByUserIdAndDealerIdAndStatus(
                        user.getId(),
                        user.getDealer().getId(),
                        EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Aktif sepet bulunamadı"));

        CartItem cartItem = cartItemRepository
                .findByIdAndCartIdAndCartUserIdAndStatus(cartItemId, cart.getId(), user.getId(), EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Sepet öğesi bulunamadı"));

        ProductVariant variant = ensureVariant(cartItem.getProductPrice());

        if (!variant.hasEnoughStock(request.quantity())) {
            throw new IllegalArgumentException(String.format(
                    "Yetersiz stok (%s): İstenen %d, mevcut %d",
                    variant.getDisplayName(),
                    request.quantity(),
                    variant.getStockQuantity()
            ));
        }

        cartItem.setQuantity(request.quantity());
        cartItem.setUnitPrice(cartItem.getProductPrice().getAmount());
        cartItem.recalculateTotals();

        cart.touch();
        cartRepository.save(cart);

        Cart refreshed = getActiveCartEntity(user, cart.getDealer().getId());
        return mapToResponse(refreshed);
    }

    @Transactional
    public void removeItem(AppUser user, Long cartItemId) {
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
                .map(item -> new OrderProductRequest(item.getProductPrice().getId(), item.getQuantity()))
                .collect(Collectors.toList());
    }

    public Cart getValidatedCartForCheckout(Long cartId, AppUser user, Long dealerId) {
        Cart cart = cartRepository.findByIdAndUserIdAndStatus(cartId, user.getId(), EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Sepet bulunamadı"));

        if (!cart.getDealer().getId().equals(dealerId)) {
            throw new IllegalArgumentException("Sepet seçilen bayi ile eşleşmiyor");
        }

        if (cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Sepet boş");
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
            throw new IllegalArgumentException("Kullanıcının bağlı olduğu bir bayi yok");
        }
        if (!user.getDealer().getId().equals(dealerId)) {
            throw new IllegalArgumentException("Sadece kendi bayiniz için sepet oluşturabilirsiniz");
        }
    }

    private ProductPrice loadActiveProductPrice(Long productPriceId) {
        ProductPrice productPrice = productPriceRepository.findById(productPriceId)
                .orElseThrow(() -> new EntityNotFoundException("Ürün fiyatı bulunamadı: " + productPriceId));

        if (!productPrice.isValidNow()) {
            throw new IllegalArgumentException("Ürün fiyatı şu anda geçerli değil");
        }

        return productPrice;
    }

    private ProductVariant ensureVariant(ProductPrice productPrice) {
        ProductVariant variant = productPrice.getProductVariant();
        if (variant == null) {
            throw new IllegalStateException("Ürün fiyatı herhangi bir varyanta bağlı değil");
        }
        return variant;
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getId))
                .map(this::mapItem)
                .collect(Collectors.toList());

        BigDecimal subtotal = itemResponses.stream()
                .map(CartItemResponse::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String currency = itemResponses.stream()
                .map(CartItemResponse::currency)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("TRY");

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

    private CartItemResponse mapItem(CartItem item) {
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
                item.getUnitPrice(),
                item.getTotalPrice(),
                item.getProductPrice() != null && item.getProductPrice().getCurrency() != null
                        ? item.getProductPrice().getCurrency().name()
                        : null,
                imageUrl
        );
    }
}
