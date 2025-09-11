package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.product.*;
import com.maxx_global.dto.productImage.ProductImageInfo;
import com.maxx_global.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/products")
@Validated
@Tag(name = "Product Management", description = "Ürün yönetimi için API endpoint'leri. Ortopedi ameliyat malzemeleri yönetimini destekler.")
@SecurityRequirement(name = "Bearer Authentication")
public class ProductController {

    private static final Logger logger = Logger.getLogger(ProductController.class.getName());
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ==================== BASIC PRODUCT ENDPOINTS (WITHOUT DEALER) ====================

    @GetMapping
    @Operation(
            summary = "Tüm ürünleri listele",
            description = "Sayfalama ve sıralama ile tüm aktif ürünleri getirir. Summary format (fiyat bilgisi dahil değil)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürünler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> getAllProducts(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            Page<ProductSummary> products = productService.getAllProducts(page, size, sortBy, sortDirection,authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error fetching products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "ID ile ürün getir",
            description = "Belirtilen ID'ye sahip ürünün detay bilgilerini getirir (fiyat bilgisi dahil değil)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<ProductResponse>> getProductById(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(hidden = true) Authentication authentication) {
        try {
            ProductResponse product = productService.getProductById(id,authentication);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching product by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/active")
    @Operation(
            summary = "Aktif ürünleri listele",
            description = "Sadece aktif durumda olan ürünleri getirir (summary format)"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getActiveProducts( @Parameter(hidden = true) Authentication authentication) {
        try {
            List<ProductSummary> products = productService.getActiveProducts(authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error fetching active products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ProductController.java - Eklenecek endpoint

    @PostMapping("/bulk")
    @Operation(
            summary = "Birden fazla ürünü ID'lere göre getir",
            description = "Verilen ürün ID'leri listesine göre ürünleri getirir. Dealer kullanıcısı için fiyat bilgisi dahil."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürünler başarıyla getirildi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz ID listesi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getBulkProducts(
            @Parameter(description = "Ürün ID'leri listesi", required = true)
            @Valid @RequestBody BulkProductRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            logger.info("Fetching bulk products - count: " + request.productIds().size());

            List<ProductSummary> products = productService.getBulkProducts(request.productIds(), authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error fetching bulk products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/category/{categoryId}")
    @Operation(
            summary = "Kategoriye göre ürünleri listele",
            description = "Belirtilen kategorideki ürünleri getirir (summary format)"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> getProductsByCategory(
            @Parameter(description = "Kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long categoryId,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            Page<ProductSummary> products = productService.getProductsByCategory(
                    categoryId, page, size, sortBy, sortDirection, authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching products by category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori ürünleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search")
    @Operation(
            summary = "Ürün arama",
            description = "Ürün adı, kodu ve açıklamasında arama yapar. Kısmi eşleşmeleri destekler (summary format)"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> searchProducts(
            @Parameter(description = "Arama terimi (minimum 2 karakter)", example = "implant", required = true)
            @RequestParam String q,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductSummary> products = productService.searchProducts(q, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error searching products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/in-stock")
    @Operation(
            summary = "Stokta olan ürünleri listele",
            description = "Stok miktarı 0'dan büyük olan ürünleri getirir (summary format)"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> getInStockProducts(
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductSummary> products = productService.getInStockProducts(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error fetching in-stock products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stokta olan ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/search/advanced")
    @Operation(
            summary = "Gelişmiş ürün arama",
            description = "Çoklu kriterlere göre detaylı ürün araması yapar (summary format)"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> advancedSearch(
            @Parameter(description = "Arama kriterleri", required = true)
            @RequestBody ProductSearchCriteria criteria,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductSummary> products = productService.advancedSearch(criteria, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error in advanced search: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Gelişmiş arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== DEALER-SPECIFIC ENDPOINTS (WITH PRICE INFO) ====================

    @PostMapping("/with-dealer")
    @Operation(
            summary = "Dealer bilgisi ile tüm ürünleri listele",
            description = "Belirtilen dealer için tüm ürünleri fiyat bilgisi ile birlikte getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürünler başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Dealer bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductListItemResponse>>> getAllProductsWithDealer(
            @Parameter(description = "Dealer bilgileri", required = true)
            @Valid @RequestBody ProductWithDealerInfoRequest request,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            Page<ProductListItemResponse> products = productService.getAllProductsWithDealer(
                    request, page, size, sortBy, sortDirection,authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching products with dealer info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Dealer bilgisi ile ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/{id}/with-dealer")
    @Operation(
            summary = "Dealer bilgisi ile ürün detayı getir",
            description = "Belirtilen ürünün dealer bilgisi ve fiyat bilgisi ile birlikte detaylarını getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Ürün veya dealer bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<ProductWithPriceResponse>> getProductByIdWithDealer(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Dealer bilgileri", required = true)
            @Valid @RequestBody ProductWithDealerInfoRequest request,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            ProductWithPriceResponse product = productService.getProductByIdWithDealer(id, request,authentication);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching product with dealer info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Dealer bilgisi ile ürün getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/category/{categoryId}/with-dealer")
    @Operation(
            summary = "Dealer bilgisi ile kategoriye göre ürünleri listele",
            description = "Belirtilen kategorideki ürünleri dealer fiyat bilgisi ile birlikte getirir"
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductListItemResponse>>> getProductsByCategoryWithDealer(
            @Parameter(description = "Kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long categoryId,
            @Parameter(description = "Dealer bilgileri", required = true)
            @Valid @RequestBody ProductWithDealerInfoRequest request,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            Page<ProductListItemResponse> products = productService.getProductsByCategoryWithDealer(
                    categoryId, request, page, size, sortBy, sortDirection,authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching category products with dealer info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Dealer bilgisi ile kategori ürünleri getirilirken hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/search/with-dealer")
    @Operation(
            summary = "Dealer bilgisi ile ürün arama",
            description = "Gelişmiş arama kriterleri ve dealer bilgisi ile ürün arama yapar. Fiyat filtreleme destekler."
    )
    @PreAuthorize("hasPermission(null, 'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductListItemResponse>>> searchProductsWithDealer(
            @Parameter(description = "Dealer bazlı arama kriterleri", required = true)
            @Valid @RequestBody ProductDealerSearchRequest request,
            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductListItemResponse> products = productService.searchProductsWithDealer(
                    request, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error searching products with dealer info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Dealer bilgisi ile ürün arama sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== CRUD OPERATIONS ====================

    @PostMapping
    @Operation(
            summary = "Yeni ürün oluştur",
            description = "Yeni bir ürün kaydı oluşturur. Ürün kodu benzersiz olmalıdır."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Ürün başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya ürün kodu zaten kullanımda"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_CREATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> createProduct(
            @Parameter(description = "Yeni ürün bilgileri", required = true)
            @Valid @RequestBody ProductRequest request) {

        try {
            logger.info("Creating product: " + request.name());
            ProductResponse product = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            logger.warning("Category not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            logger.warning("Validation error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ProductController'a eklenecek tek image upload endpoint'i:

    @PostMapping(
            value = "/{productId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @Operation(
            summary = "Ürüne resim yükle",
            description = "Ürüne tek veya birden fazla resim ekler. Maksimum 10 adet resim yüklenebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resimler başarıyla yüklendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz dosya formatı veya boyutu"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> uploadProductImages(
            @Parameter(description = "Ürün ID'si", example = "6")
            @PathVariable Long productId,

            @Parameter(
                    description = "Resim dosyaları (tek veya birden fazla)",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("images") List<MultipartFile> images,

            @Parameter(description = "Ana resim index'i (0'dan başlar)", example = "0")
            @RequestParam(value = "primaryImageIndex", required = false) Integer primaryImageIndex
    ) {

        try {
            logger.info("Uploading " + images.size() + " image(s) for product: " + productId);

            // Validation
            validateImageFiles(images, primaryImageIndex);

            ProductResponse product = productService.addProductImages(productId, images, primaryImageIndex);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            logger.warning("Product not found: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            logger.warning("Image validation error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error uploading images: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Resim yükleme sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{productId}/images/{imageId}/primary")
    @Operation(
            summary = "Ana resmi değiştir",
            description = "Ürünün ana resmini değiştirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> setPrimaryImage(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,

            @Parameter(description = "Ana resim yapılacak resim ID'si", example = "5", required = true)
            @PathVariable @Min(1) Long imageId) {

        try {
            logger.info("Setting primary image for product: " + productId + ", imageId: " + imageId);

            ProductResponse product = productService.setPrimaryImage(productId, imageId);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error setting primary image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ana resim değiştirme sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{productId}/images/{imageId}")
    @Operation(
            summary = "Ürün resmini sil",
            description = "Belirtilen ürün resmini siler"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> deleteProductImage(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId,

            @Parameter(description = "Silinecek resim ID'si", example = "5", required = true)
            @PathVariable @Min(1) Long imageId) {

        try {
            logger.info("Deleting image: " + imageId + " from product: " + productId);

            ProductResponse product = productService.deleteProductImage(productId, imageId);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting image: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Resim silme sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{productId}/images")
    @Operation(
            summary = "Ürün resimlerini listele",
            description = "Belirtilen ürünün tüm resimlerini listeler"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductImageInfo>>> getProductImages(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long productId) {

        try {
            List<ProductImageInfo> images = productService.getProductImages(productId);
            return ResponseEntity.ok(BaseResponse.success(images));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching product images: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün resimleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

// ===== HELPER METHOD =====

    private void validateImageFiles(List<MultipartFile> images, Integer primaryImageIndex) {
        if (images == null || images.isEmpty()) {
            throw new IllegalArgumentException("En az bir resim dosyası gereklidir");
        }

        if (images.size() > 10) {
            throw new IllegalArgumentException("Maksimum 10 adet resim yükleyebilirsiniz");
        }

        if (primaryImageIndex != null && (primaryImageIndex < 0 || primaryImageIndex >= images.size())) {
            throw new IllegalArgumentException("Ana resim index'i geçersiz");
        }

        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);

            if (image.isEmpty()) {
                throw new IllegalArgumentException("Boş resim dosyası yüklenemez (Index: " + i + ")");
            }

            if (image.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("Resim dosyası 5MB'dan büyük olamaz (Index: " + i + ")");
            }

            String contentType = image.getContentType();
            if (contentType == null || !isValidImageType(contentType)) {
                throw new IllegalArgumentException("Geçersiz resim formatı. Sadece JPG, JPEG, PNG, GIF, WEBP desteklenir (Index: " + i + ")");
            }
        }
    }

    private boolean isValidImageType(String contentType) {
        return contentType.equals("image/jpeg") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/gif") ||
                contentType.equals("image/webp");
    }


    @PutMapping("/{id}")
    @Operation(
            summary = "Ürün güncelle",
            description = "Mevcut bir ürünün bilgilerini günceller"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> updateProduct(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Güncellenmiş ürün bilgileri", required = true)
            @Valid @RequestBody ProductRequest request) {

        try {
            ProductResponse product = productService.updateProduct(id, request);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PatchMapping("/{id}/stock")
    @Operation(
            summary = "Stok güncelle",
            description = "Ürünün stok miktarını günceller"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_UPDATE')")
    public ResponseEntity<BaseResponse<ProductResponse>> updateStock(
            @Parameter(description = "Ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Yeni stok miktarı", example = "100", required = true)
            @RequestParam @Min(0) Integer stockQuantity) {

        try {
            ProductResponse product = productService.updateStock(id, stockQuantity);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating stock: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Stok güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Ürün sil",
            description = "Belirtilen ürünü siler (soft delete)"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_DELETE')")
    public ResponseEntity<BaseResponse<Void>> deleteProduct(
            @Parameter(description = "Silinecek ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error deleting product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/{id}/restore")
    @Operation(
            summary = "Ürün geri yükle",
            description = "Silinmiş olan ürünü geri yükler"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_RESTORE')")
    public ResponseEntity<BaseResponse<ProductResponse>> restoreProduct(
            @Parameter(description = "Geri yüklenecek ürün ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            ProductResponse product = productService.restoreProduct(id);
            return ResponseEntity.ok(BaseResponse.success(product));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error restoring product: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün geri yüklenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== BUSINESS LOGIC ENDPOINTS ====================

    @GetMapping("/expired")
    @Operation(
            summary = "Süresi dolan ürünleri listele",
            description = "Son kullanma tarihi geçmiş ürünleri getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getExpiredProducts() {
        try {
            List<ProductSummary> expiredProducts = productService.getExpiredProducts();
            return ResponseEntity.ok(BaseResponse.success(expiredProducts));

        } catch (Exception e) {
            logger.severe("Error fetching expired products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Süresi dolan ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/expiring")
    @Operation(
            summary = "Yakında süresi dolacak ürünleri listele",
            description = "Belirtilen gün sayısı içinde süresi dolacak ürünleri getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getProductsExpiringInDays(
            @Parameter(description = "Gün sayısı", example = "30")
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {

        try {
            List<ProductSummary> expiringProducts = productService.getProductsExpiringInDays(days);
            return ResponseEntity.ok(BaseResponse.success(expiringProducts));

        } catch (Exception e) {
            logger.severe("Error fetching expiring products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Yakında süresi dolacak ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/low-stock")
    @Operation(
            summary = "Düşük stok ürünlerini listele",
            description = "Belirtilen eşik değerinin altında stoku olan ürünleri getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getLowStockProducts(
            @Parameter(description = "Stok eşik değeri", example = "10")
            @RequestParam(defaultValue = "10") @Min(0) Integer threshold) {

        try {
            List<ProductSummary> lowStockProducts = productService.getLowStockProducts(threshold);
            return ResponseEntity.ok(BaseResponse.success(lowStockProducts));

        } catch (Exception e) {
            logger.severe("Error fetching low stock products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Düşük stok ürünleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/random")
    @Operation(
            summary = "Rastgele ürünler getir",
            description = "Ana sayfa önerileri için rastgele ürünler getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSummary>>> getRandomProducts(
            @Parameter(description = "Getirilen ürün sayısı", example = "6")
            @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {

        try {
            List<ProductSummary> randomProducts = productService.getRandomProducts(limit);
            return ResponseEntity.ok(BaseResponse.success(randomProducts));

        } catch (Exception e) {
            logger.severe("Error fetching random products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Rastgele ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    // ==================== UTILITY ENDPOINTS ====================

    @GetMapping("/filters/materials")
    @Operation(
            summary = "Malzeme listesini getir",
            description = "Filter dropdown'ı için benzersiz malzeme listesini getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<String>>> getDistinctMaterials() {
        try {
            List<String> materials = productService.getDistinctMaterials();
            return ResponseEntity.ok(BaseResponse.success(materials));

        } catch (Exception e) {
            logger.severe("Error fetching materials: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Malzeme listesi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/filters/units")
    @Operation(
            summary = "Birim listesini getir",
            description = "Filter dropdown'ı için benzersiz birim listesini getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<String>>> getDistinctUnits() {
        try {
            List<String> units = productService.getDistinctUnits();
            return ResponseEntity.ok(BaseResponse.success(units));

        } catch (Exception e) {
            logger.severe("Error fetching units: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Birim listesi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/filters/device-classes")
    @Operation(
            summary = "Tıbbi cihaz sınıfları listesini getir",
            description = "Filter dropdown'ı için benzersiz tıbbi cihaz sınıfları listesini getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<String>>> getDistinctMedicalDeviceClasses() {
        try {
            List<String> deviceClasses = productService.getDistinctMedicalDeviceClasses();
            return ResponseEntity.ok(BaseResponse.success(deviceClasses));

        } catch (Exception e) {
            logger.severe("Error fetching device classes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Cihaz sınıfları getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "Ürün istatistiklerini getir",
            description = "Dashboard için ürün istatistiklerini getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<ProductStatistics>> getProductStatistics() {
        try {
            ProductStatistics statistics = productService.getProductStatistics();
            return ResponseEntity.ok(BaseResponse.success(statistics));

        } catch (Exception e) {
            logger.severe("Error fetching product statistics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ürün istatistikleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
    @GetMapping("/search/field")
    @Operation(
            summary = "Field bazında hızlı arama",
            description = "Belirtilen field'da direkt arama yapar. Büyük veri setlerinde performanslı arama için."
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> searchByField(
            @Parameter(description = "Arama yapılacak field adı", example = "code", required = true)
            @RequestParam String fieldName,

            @Parameter(description = "Arama değeri", example = "TI-001", required = true)
            @RequestParam String searchValue,

            @Parameter(description = "Exact match mi yoksa partial match mi?", example = "false")
            @RequestParam(defaultValue = "false") boolean exactMatch,

            @Parameter(description = "Sayfa numarası", example = "0")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,

            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection,
            @Parameter(hidden = true) Authentication authentication) {

        try {
            Page<ProductSummary> products = productService.searchByField(
                    fieldName, searchValue, exactMatch, page, size, sortBy, sortDirection,authentication);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        } catch (Exception e) {
            logger.severe("Error in field search: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Field bazında arama sırasında hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search/fields/available")
    @Operation(
            summary = "Aranabilir field'ları listele",
            description = "Field bazında arama için kullanılabilir field'ları getirir"
    )
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSearchField>>> getSearchableFields() {
        try {
            List<ProductSearchField> fields = productService.getSearchableFields();
            return ResponseEntity.ok(BaseResponse.success(fields));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aranabilir field listesi getirilirken hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/without-images")
    @Operation(
            summary = "Resmi olmayan ürünleri listele",
            description = "Hiç resmi bulunmayan ürünleri getirir (summary format)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resmi olmayan ürünler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<Page<ProductSummary>>> getProductsWithoutImages(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<ProductSummary> products = productService.getProductsWithoutImages(
                    page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error fetching products without images: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Resmi olmayan ürünler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/simple")
    @Operation(
            summary = "Basit ürün listesi",
            description = "Dropdown ve select listeleri için sadece ID, isim ve kod içeren basit ürün listesi döner"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Basit ürün listesi başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasPermission(null,'PRODUCT_READ')")
    public ResponseEntity<BaseResponse<List<ProductSimple>>> getSimpleProducts() {
        try {
            logger.info("GET /api/products/simple - Fetching simple product list");
            List<ProductSimple> products = productService.getSimpleProducts();
            return ResponseEntity.ok(BaseResponse.success(products));

        } catch (Exception e) {
            logger.severe("Error fetching simple products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Basit ürün listesi getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}