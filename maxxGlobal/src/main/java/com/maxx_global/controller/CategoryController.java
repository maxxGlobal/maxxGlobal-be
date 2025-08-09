package com.maxx_global.controller;

import com.maxx_global.dto.BaseResponse;
import com.maxx_global.dto.category.CategoryRequest;
import com.maxx_global.dto.category.CategoryResponse;
import com.maxx_global.dto.category.CategorySummary;
import com.maxx_global.dto.category.CategoryTreeResponse;
import com.maxx_global.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/categories")
@Validated
@Tag(name = "Category Management", description = "Kategori yönetimi için API endpoint'leri. Hiyerarşik kategori yapısını destekler.")
@SecurityRequirement(name = "Bearer Authentication")
public class CategoryController {

    private static final Logger logger = Logger.getLogger(CategoryController.class.getName());
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(
            summary = "Tüm kategorileri listele",
            description = "Sayfalama ve sıralama ile tüm kategorileri getirir. Hem ana hem alt kategorileri içerir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<Page<CategoryResponse>>> getAllCategories(
            @Parameter(description = "Sayfa numarası (0'dan başlar)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Sayfa boyutu", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sıralama alanı", example = "name")
            @RequestParam(defaultValue = "name") String sortBy,
            @Parameter(description = "Sıralama yönü", example = "asc")
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            Page<CategoryResponse> categories = categoryService.getAllCategories(page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error fetching categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategoriler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/active")
    @Operation(
            summary = "Aktif kategorileri listele",
            description = "Sadece aktif durumda olan kategorileri getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Aktif kategoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategoryResponse>>> getActiveCategories() {
        try {
            List<CategoryResponse> categories = categoryService.getActiveCategories();
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error fetching active categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Aktif kategoriler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/root")
    @Operation(
            summary = "Ana kategorileri listele",
            description = "Sadece üst seviye kategorileri (parent'ı olmayan) getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ana kategoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategoryResponse>>> getRootCategories() {
        try {
            List<CategoryResponse> categories = categoryService.getRootCategories();
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error fetching root categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Ana kategoriler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/subcategories/{parentId}")
    @Operation(
            summary = "Alt kategorileri listele",
            description = "Belirtilen kategorinin alt kategorilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alt kategoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Parent kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategoryResponse>>> getSubCategories(
            @Parameter(description = "Parent kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long parentId) {
        try {
            List<CategoryResponse> categories = categoryService.getSubCategories(parentId);
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching subcategories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Alt kategoriler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/summaries")
    @Operation(
            summary = "Kategori özetlerini getir",
            description = "Dropdown ve select listeleri için kategori ID, isim ve alt kategori durumu özetlerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori özetleri başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategorySummary>>> getCategorySummaries() {
        try {
            List<CategorySummary> summaries = categoryService.getCategorySummaries();
            return ResponseEntity.ok(BaseResponse.success(summaries));

        } catch (Exception e) {
            logger.severe("Error fetching category summaries: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori özetleri getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/leaf")
    @Operation(
            summary = "Yaprak kategorileri listele",
            description = "Alt kategorisi olmayan kategorileri getirir. Bu kategorilere ürün eklenebilir."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Yaprak kategoriler başarıyla getirildi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategoryResponse>>> getLeafCategories() {
        try {
            List<CategoryResponse> categories = categoryService.getLeafCategories();
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error fetching leaf categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Yaprak kategoriler getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "ID ile kategori getir",
            description = "Belirtilen ID'ye sahip kategorinin detay bilgilerini getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori başarıyla getirildi"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<CategoryResponse>> getCategoryById(
            @Parameter(description = "Kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            CategoryResponse category = categoryService.getCategoryById(id);
            return ResponseEntity.ok(BaseResponse.success(category));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error fetching category by id: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori getirilirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/search")
    @Operation(
            summary = "Kategori arama",
            description = "Kategori adında arama yapar. Kısmi eşleşmeleri destekler."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Arama başarılı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz arama parametresi"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<Page<CategoryResponse>>> searchCategories(
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
            Page<CategoryResponse> categories = categoryService.searchCategories(q, page, size, sortBy, sortDirection);
            return ResponseEntity.ok(BaseResponse.success(categories));

        } catch (Exception e) {
            logger.severe("Error searching categories: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori arama sırasında bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping
    @Operation(
            summary = "Yeni kategori oluştur",
            description = "Yeni bir kategori oluşturur. Aynı seviyede kategori adı benzersiz olmalıdır."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Kategori başarıyla oluşturuldu"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya kategori adı zaten kullanımda"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "404", description = "Parent kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<BaseResponse<CategoryResponse>> createCategory(
            @Parameter(description = "Yeni kategori bilgileri", required = true)
            @Valid @RequestBody CategoryRequest request) {
        try {
            CategoryResponse category = categoryService.createCategory(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.success(category));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error creating category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori oluşturulurken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Kategori güncelle",
            description = "Mevcut bir kategorinin bilgilerini günceller. Döngüsel referans kontrolü yapar."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori başarıyla güncellendi"),
            @ApiResponse(responseCode = "400", description = "Geçersiz veri veya döngüsel referans"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_CREATE')")
    public ResponseEntity<BaseResponse<CategoryResponse>> updateCategory(
            @Parameter(description = "Kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id,
            @Parameter(description = "Güncellenmiş kategori bilgileri", required = true)
            @Valid @RequestBody CategoryRequest request) {

        try {
            CategoryResponse category = categoryService.updateCategory(id, request);
            return ResponseEntity.ok(BaseResponse.success(category));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error updating category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori güncellenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Kategori sil",
            description = "Belirtilen kategoriyi siler (soft delete). Alt kategorisi olan kategori silinemez."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori başarıyla silindi"),
            @ApiResponse(responseCode = "400", description = "Alt kategorisi olan kategori silinemez"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_DELETE')")
    public ResponseEntity<BaseResponse<Void>> deleteCategory(
            @Parameter(description = "Silinecek kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            categoryService.deleteCategory(id);
            return ResponseEntity.ok(BaseResponse.success(null));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));

        } catch (Exception e) {
            logger.severe("Error deleting category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori silinirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @PostMapping("/{id}/restore")
    @Operation(
            summary = "Kategori geri yükle",
            description = "Silinmiş olan kategoriyi geri yükler ve aktif duruma getirir"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Kategori başarıyla geri yüklendi"),
            @ApiResponse(responseCode = "403", description = "Yetki yok"),
            @ApiResponse(responseCode = "404", description = "Kategori bulunamadı"),
            @ApiResponse(responseCode = "500", description = "Sunucu hatası")
    })
    @PreAuthorize("hasAuthority('CATEGORY_RESTORE')")
    public ResponseEntity<BaseResponse<CategoryResponse>> restoreCategory(
            @Parameter(description = "Geri yüklenecek kategori ID'si", example = "1", required = true)
            @PathVariable @Min(1) Long id) {
        try {
            CategoryResponse category = categoryService.restoreCategory(id);
            return ResponseEntity.ok(BaseResponse.success(category));

        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(BaseResponse.error(e.getMessage(), HttpStatus.NOT_FOUND.value()));

        } catch (Exception e) {
            logger.severe("Error restoring category: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori geri yüklenirken bir hata oluştu: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }

    @GetMapping("/tree")
    @Operation(
            summary = "Kategori ağacını getir",
            description = "Hiyerarşik kategori yapısını tree formatında getirir. Lazy loading destekler."
    )
    @PreAuthorize("hasAuthority('CATEGORY_READ')")
    public ResponseEntity<BaseResponse<List<CategoryTreeResponse>>> getCategoryTree(
            @Parameter(description = "Kaç seviye derinliğe kadar yüklensin", example = "2")
            @RequestParam(defaultValue = "2") int depth) {

        try {
            List<CategoryTreeResponse> tree = categoryService.getCategoryTree(depth);
            return ResponseEntity.ok(BaseResponse.success(tree));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(BaseResponse.error("Kategori ağacı getirilirken hata: " + e.getMessage(),
                            HttpStatus.INTERNAL_SERVER_ERROR.value()));
        }
    }
}