package com.maxx_global.service;

import com.maxx_global.dto.product.ProductMapper;
import com.maxx_global.dto.productPrice.ProductPriceInfo;
import com.maxx_global.dto.product.ProductSummary;
import com.maxx_global.dto.userFavorite.UserFavoriteRequest;
import com.maxx_global.dto.userFavorite.UserFavoriteResponse;
import com.maxx_global.dto.userFavorite.UserFavoriteStatusResponse;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Product;
import com.maxx_global.entity.ProductPrice;
import com.maxx_global.entity.UserFavorite;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.Language;
import com.maxx_global.repository.ProductPriceRepository;
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
import java.util.Optional;
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
    private final ProductPriceRepository productPriceRepository;
    private final LocalizationService localizationService;

    public UserFavoriteService(UserFavoriteRepository userFavoriteRepository,
                               ProductService productService,
                               ProductMapper productMapper,
                               ProductPriceRepository productPriceRepository,
                               LocalizationService localizationService) {
        this.userFavoriteRepository = userFavoriteRepository;
        this.productService = productService;
        this.productMapper = productMapper;
        this.productPriceRepository = productPriceRepository;
        this.localizationService = localizationService;
    }

    /**
     * Kullanıcının favori ürünlerini getir
     */
    public Page<UserFavoriteResponse> getUserFavorites(Long userId, int page, int size, AppUser currentUser) {
        logger.info("Fetching favorites for user: " + userId);

        // Kullanıcı kendi favorilerine mi bakıyor kontrol et
        if (!userId.equals(currentUser.getId())) {
            throw new BadCredentialsException("Sadece kendi favorilerinizi görüntüleyebilirsiniz");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<UserFavorite> favorites = userFavoriteRepository.findUserFavorites(
                userId, EntityStatus.ACTIVE, pageable);

        Language language = localizationService.getLanguageForUser(currentUser);

        Page<UserFavoriteResponse> asd= favorites.map(favorite -> {
            Product product = favorite.getProduct();
            ProductSummary productSummary = productMapper.toSummary(product);

            // Favoriler listesinde olduğu için isFavorite = true, prices ekle
            ProductSummary productWithFavoriteAndPrices = new ProductSummary(
                    productSummary.id(), product.getLocalizedName(language), productSummary.code(),
                    productSummary.categoryName(), productSummary.primaryImageUrl(),
                    productSummary.stockQuantity(), productSummary.unit(),
                    productSummary.isActive(), productSummary.isInStock(),
                    productSummary.status(),
                    true
            );

            return new UserFavoriteResponse(
                    favorite.getId(),
                    productWithFavoriteAndPrices,
                    favorite.getCreatedAt(),
                    favorite.getUpdatedAt()
            );
        });
        logger.info("Fetching favorites for user: " + userId);
        return asd;
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

        return mapToResponse(savedFavorite, product, currentUser);
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
    public List<UserFavoriteResponse> getUserFavoritesByCategory(Long userId, Long categoryId, AppUser currentUser) {
        logger.info("Fetching favorites for user: " + userId + ", category: " + categoryId);

        // Kullanıcı kendi favorilerine mi bakıyor kontrol et
        if (!userId.equals(currentUser.getId())) {
            throw new BadCredentialsException("Sadece kendi favorilerinizi görüntüleyebilirsiniz");
        }

        List<UserFavorite> favorites = userFavoriteRepository.findUserFavoritesByCategory(
                userId, categoryId, EntityStatus.ACTIVE);

        return favorites.stream()
                .map(favorite -> mapToResponse(favorite, currentUser))
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

    /**
     * Kullanıcıya göre fiyat bilgilerini getirir
     */
//    private List<ProductPriceInfo> getPriceInfosForUser(Long productId, AppUser user) {
//        // Eğer kullanıcının dealer'ı varsa fiyat bilgilerini getir
//        if (user.getDealer() != null) {
//            logger.info("Getting price info for favorite product: " + productId + ", dealer: " + user.getDealer().getId() +
//                    ", preferred currency: " + user.getDealer().getPreferredCurrency());
//
//            // Sadece dealer'ın preferred currency'sindeki fiyatı al
//            Optional<ProductPrice> priceOptional = productPriceRepository.findValidPrice(
//                    productId, user.getDealer().getId(), user.getDealer().getPreferredCurrency(), EntityStatus.ACTIVE);
//
//            if (priceOptional.isPresent()) {
//                ProductPrice price = priceOptional.get();
//                return List.of(new ProductPriceInfo(
//                        price.getId(),
//                        price.getCurrency(),
//                        price.getAmount()
//                ));
//            } else {
//                // Bu dealer için bu currency'de fiyat yoksa boş liste döndür
//                logger.info("No price found for favorite product: " + productId + ", dealer: " + user.getDealer().getId() +
//                        ", currency: " + user.getDealer().getPreferredCurrency());
//                return List.of();
//            }
//        }
//
//        // Admin kullanıcısı veya dealer'ı olmayan kullanıcı için null döndür
//        return null;
//    }

    private UserFavoriteResponse mapToResponse(UserFavorite favorite, AppUser currentUser) {
        Language language = localizationService.getLanguageForUser(currentUser);
        Product productEntity = favorite.getProduct();
        ProductSummary product = productMapper.toSummary(productEntity);

        // Fiyat bilgilerini al
   //     List<ProductPriceInfo> priceInfos = getPriceInfosForUser(favorite.getProduct().getId(), currentUser);

        // ProductSummary'yi güncellenmiş constructor ile oluştur
        ProductSummary productWithPrices = new ProductSummary(
                product.id(), productEntity.getLocalizedName(language), product.code(), product.categoryName(),
                product.primaryImageUrl(), product.stockQuantity(), product.unit(),
                product.isActive(), product.isInStock(), product.status(),
                true
        );

        return mapToResponse(favorite, productWithPrices);
    }

    private UserFavoriteResponse mapToResponse(UserFavorite favorite, ProductSummary product, AppUser currentUser) {

        Language language = localizationService.getLanguageForUser(currentUser);

        // ProductSummary'yi güncellenmiş constructor ile oluştur
        ProductSummary productWithPrices = new ProductSummary(
                product.id(), favorite.getProduct().getLocalizedName(language), product.code(), product.categoryName(),
                product.primaryImageUrl(), product.stockQuantity(), product.unit(),
                product.isActive(), product.isInStock(), product.status(),
                true
        );

        return new UserFavoriteResponse(
                favorite.getId(),
                productWithPrices,
                favorite.getCreatedAt(),
                favorite.getUpdatedAt()
        );
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