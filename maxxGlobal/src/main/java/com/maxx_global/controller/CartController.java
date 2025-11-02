package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.cart.CartItemRequest;
import com.maxx_global.dto.cart.CartItemUpdateRequest;
import com.maxx_global.dto.cart.CartResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.logging.Logger;

@RestController
@RequestMapping("/api/cart")
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Cart", description = "Sepet yönetimi için API endpoint'leri")
public class CartController {

    private static final Logger logger = Logger.getLogger(CartController.class.getName());

    private final CartService cartService;
    private final AppUserService appUserService;

    public CartController(CartService cartService, AppUserService appUserService) {
        this.cartService = cartService;
        this.appUserService = appUserService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'ORDER_CREATE')")
    @Operation(summary = "Aktif sepeti getir", description = "Kullanıcının belirtilen bayi için aktif sepetini döndürür")
    public ResponseEntity<BaseResponse<CartResponse>> getCart(
            @Parameter(description = "Bayi ID", required = true)
            @RequestParam Long dealerId,
            Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);
        try {
            CartResponse response = cartService.getActiveCart(currentUser, dealerId);
            return ResponseEntity.ok(BaseResponse.success(response));
        } catch (EntityNotFoundException e) {
            logger.warning("Cart not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error fetching cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sepet getirilirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/items")
    @PreAuthorize("hasPermission(null, 'ORDER_CREATE')")
    @Operation(summary = "Sepete ürün ekle")
    public ResponseEntity<BaseResponse<CartResponse>> addItem(
            @Valid @RequestBody CartItemRequest request,
            Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);
        try {
            CartResponse response = cartService.addItem(currentUser, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(response));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error adding item to cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sepete ürün eklenirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(null, 'ORDER_CREATE')")
    @Operation(summary = "Sepet öğesinin miktarını güncelle")
    public ResponseEntity<BaseResponse<CartResponse>> updateItem(
            @PathVariable Long itemId,
            @Valid @RequestBody CartItemUpdateRequest request,
            Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);
        try {
            CartResponse response = cartService.updateItemQuantity(currentUser, itemId, request);
            return ResponseEntity.ok(BaseResponse.success(response));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error updating cart item: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sepet güncellenirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/items/{itemId}")
    @PreAuthorize("hasPermission(null, 'ORDER_CREATE')")
    @Operation(summary = "Sepetten ürün çıkar")
    public ResponseEntity<BaseResponse<Void>> removeItem(
            @PathVariable Long itemId,
            Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);
        try {
            cartService.removeItem(currentUser, itemId);
            return ResponseEntity.ok(BaseResponse.success(null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error removing item from cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sepetten ürün silinirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping
    @PreAuthorize("hasPermission(null, 'ORDER_CREATE')")
    @Operation(summary = "Sepeti temizle")
    public ResponseEntity<BaseResponse<Void>> clearCart(
            @RequestParam Long dealerId,
            Authentication authentication) {

        AppUser currentUser = appUserService.getCurrentUser(authentication);
        try {
            cartService.clearCart(currentUser, dealerId);
            return ResponseEntity.ok(BaseResponse.success(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error clearing cart: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Sepet temizlenirken hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}
