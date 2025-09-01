package com.maxx_global.service;

import com.maxx_global.dto.product.ProductMapper;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.dto.userFavorite.UserFavoriteRequest;
import com.maxx_global.dto.userFavorite.UserFavoriteResponse;
import com.maxx_global.dto.userFavorite.UserFavoriteStatusResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.UserFavorite;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.UserFavoriteRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserFavoriteService {

    private static final Logger logger = Logger.getLogger(UserFavoriteService.class.getName());

    private final UserFavoriteRepository userFavoriteRepository;
    private final ProductService productService;
    private final ProductMapper productMapper;

    public UserFavoriteService(UserFavoriteRepository userFavoriteRepository,
                               ProductService productService,
                               ProductMapper productMapper) {
        this.userFavoriteRepository = userFavoriteRepository;
        this.productService = productService;
        this.productMapper = productMapper;
    }

    /**
     * Kullanıcının favori ürünlerini getir
     */
    public Page<UserFavoriteResponse> getUserFavorites(Long userId, int page, int size) {
        logger.info("Fetching favorites for user: " + userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserFavorite> favorites = userFavoriteRepository.findUserFavorites(
                userId, EntityStatus.ACTIVE, pageable);

        return favorites.map(favorite -> {
            ProductSummary productSummary = productMapper.toSummary(favorite.getProduct());
            // Favoriler listesinde olduğu için isFavorite = true
            ProductSummary productWithFavorite = new ProductSummary(
                    productSummary.id(), productSummary.name(), productSummary.code(),
                    productSummary.categoryName(), productSummary.primaryImageUrl(),
                    productSummary.stockQuantity(), productSummary.unit(),
                    productSummary.isActive(), productSummary.isInStock(),
                    productSummary.status(), true // isFavorite = true
            );

            return new UserFavoriteResponse(
                    favorite.getId(),
                    productWithFavorite,
                    favorite.getCreatedAt(),
                    favorite.getUpdatedAt()
            );
        });
    }

    /**
     * Favorilere ürün ekle
     */
    @Transactional
    public UserFavoriteResponse addToFavorites(Long userId, UserFavoriteRequest request, AppUser currentUser) {
        logger.info("Adding product " + request.productId() + " to favorites for user: " + userId);

        // Kullanıcı kendi favorilerine mi ekliyor kontrol et
        if (!userId.equals(currentUser.getId())) {
            throw new BadCredentialsException("Sadece kendi favorilerinizi yönetebilirsiniz");
        }

        // Ürün var mı kontrol et
        ProductSummary product = productService.getProductSummary(request.productId());

        // Zaten favori mi kontrol et
        if (userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                userId, request.productId(), EntityStatus.ACTIVE).isPresent()) {
            throw new BadCredentialsException("Ürün zaten favorilerinizde: " + product.name());
        }

        // Yeni favori oluştur
        UserFavorite favorite = new UserFavorite();
        favorite.setUser(currentUser);

        // Product entity'sini oluştur (sadece ID gerekli)
        Product productEntity = new Product();
        productEntity.setId(request.productId());
        favorite.setProduct(productEntity);

        favorite.setStatus(EntityStatus.ACTIVE);

        UserFavorite savedFavorite = userFavoriteRepository.save(favorite);
        logger.info("Product added to favorites successfully: " + request.productId());

        return mapToResponse(savedFavorite, product);
    }

    /**
     * Favorilerden ürün çıkar
     */
    @Transactional
    public void removeFromFavorites(Long userId, Long productId, AppUser currentUser) {
        logger.info("Removing product " + productId + " from favorites for user: " + userId);

        // Kullanıcı kendi favorilerine mi müdahale ediyor kontrol et
        if (!userId.equals(currentUser.getId())) {
            throw new BadCredentialsException("Sadece kendi favorilerinizi yönetebilirsiniz");
        }

        UserFavorite favorite = userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                        userId, productId, EntityStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Ürün favorilerinizde bulunamadı"));

        userFavoriteRepository.delete(favorite);

        logger.info("Product removed from favorites successfully: " + productId);
    }

    /**
     * Ürün favori mi kontrol et
     */
    public UserFavoriteStatusResponse checkFavoriteStatus(Long userId, Long productId) {
        logger.info("Checking favorite status for user: " + userId + ", product: " + productId);

        var favorite = userFavoriteRepository.findByUserIdAndProductIdAndStatus(
                userId, productId, EntityStatus.ACTIVE);

        return new UserFavoriteStatusResponse(
                productId,
                favorite.isPresent(),
                favorite.map(UserFavorite::getId).orElse(null)
        );
    }

    /**
     * Kullanıcının favori ürün sayısı
     */
    public Long getUserFavoriteCount(Long userId) {
        return userFavoriteRepository.countUserFavorites(userId, EntityStatus.ACTIVE);
    }

    /**
     * Kategoriye göre kullanıcının favorileri
     */
    public List<UserFavoriteResponse> getUserFavoritesByCategory(Long userId, Long categoryId) {
        logger.info("Fetching favorites for user: " + userId + ", category: " + categoryId);

        List<UserFavorite> favorites = userFavoriteRepository.findUserFavoritesByCategory(
                userId, categoryId, EntityStatus.ACTIVE);

        return favorites.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Birden fazla ürün için favori durumlarını kontrol et
     */
    public Map<Long, Boolean> checkMultipleFavoriteStatus(Long userId, Set<Long> productIds) {
        logger.info("Checking favorite status for user: " + userId + ", products: " + productIds.size());

        List<Long> favoriteProductIds = userFavoriteRepository.findUserFavoriteProductIds(
                userId, EntityStatus.ACTIVE);

        return productIds.stream()
                .collect(Collectors.toMap(
                        productId -> productId,
                        favoriteProductIds::contains
                ));
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private UserFavoriteResponse mapToResponse(UserFavorite favorite) {
        ProductSummary product = productMapper.toSummary(favorite.getProduct());
        return mapToResponse(favorite, product);
    }

    private UserFavoriteResponse mapToResponse(UserFavorite favorite, ProductSummary product) {
        return new UserFavoriteResponse(
                favorite.getId(),
                product,
                favorite.getCreatedAt(),
                favorite.getUpdatedAt()
        );
    }
}