package com.maxx_global.service;

import com.maxx_global.dto.category.*;
import com.maxx_global.entity.Category;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.Language;
import com.maxx_global.repository.CategoryRepository;
import com.maxx_global.security.SecurityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Logger logger = Logger.getLogger(CategoryService.class.getName());

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final LocalizationService localizationService;
    private final SecurityService securityService;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper,
                          LocalizationService localizationService, SecurityService securityService) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
        this.localizationService = localizationService;
        this.securityService = securityService;
    }

    // Tüm kategorileri getir (sayfalama ile)
    public Page<CategoryResponse> getAllCategories(int page, int size, String sortBy, String sortDirection) {


        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Category> categories = categoryRepository.findAll(pageable);
        return categories.map(this::mapToResponse);
    }

    // Aktif kategorileri getir
    public List<CategoryResponse> getActiveCategories() {
        logger.info("Fetching active categories");
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Root kategoriler (ana kategoriler)
    public List<CategoryResponse> getRootCategories() {
        logger.info("Fetching root categories");
        List<Category> categories = categoryRepository.findByParentCategoryIsNullAndStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Alt kategoriler
    public List<CategoryResponse> getSubCategories(Long parentId) {
        logger.info("Fetching subcategories for parent: " + parentId);

        // Parent category kontrolü
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Parent category not found with id: " + parentId));

        List<Category> categories = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(parentId, EntityStatus.ACTIVE);
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Kategori özetleri (dropdown için)
    public List<CategorySummary> getCategorySummaries() {
        logger.info("Fetching category summaries");
        List<Category> categories = categoryRepository.findByStatusOrderByNameAsc(EntityStatus.ACTIVE);
        return categories.stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    // Leaf kategoriler (ürün eklenebilir kategoriler)
    public List<CategoryResponse> getLeafCategories() {
        logger.info("Fetching leaf categories");
        List<Category> categories = categoryRepository.findLeafCategories(EntityStatus.ACTIVE);
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ID ile kategori getir
    public CategoryResponse getCategoryById(Long id) {
        logger.info("Fetching category with id: " + id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
        return mapToResponse(category);
    }

    public Category getCategoryEntityById(Long id) {
        logger.info("Fetching category with id: " + id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));
        return category;
    }

    // Kategori arama
    public Page<CategoryResponse> searchCategories(String searchTerm, int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching categories with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Category> categories = categoryRepository.searchCategories(searchTerm, EntityStatus.ACTIVE, pageable);
        return categories.map(this::mapToResponse);
    }

    // Yeni kategori oluştur
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        logger.info("Creating new category: " + request.name());

        // Kategori adı benzersizlik kontrolü
        validateCategoryNames(request.name(), request.nameEn(), request.parentCategoryId(), null);

        Category category = categoryMapper.toEntity(request);

        // Parent category set et
        if (request.parentCategoryId() != null) {
            Category parent = categoryRepository.findById(request.parentCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent category not found with id: " + request.parentCategoryId()));
            category.setParentCategory(parent);
        }else{
            category.setLeaf(false);
        }

        category.setStatus(EntityStatus.ACTIVE);

        Category savedCategory = categoryRepository.save(category);
        logger.info("Category created successfully with id: " + savedCategory.getId());

        return mapToResponse(savedCategory);
    }

    // Kategori güncelle
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        logger.info("Updating category with id: " + id);

        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));

        // Kategori adı benzersizlik kontrolü (mevcut kategori hariç)
        validateCategoryNames(request.name(), request.nameEn(), request.parentCategoryId(), id);

        // Circular reference kontrolü
        if (request.parentCategoryId() != null && request.parentCategoryId().equals(id)) {
            throw new BadCredentialsException("Category cannot be parent of itself");
        }

        // Güncelleme işlemi
        existingCategory.setName(request.name());
        existingCategory.setNameEn(request.nameEn());
        existingCategory.setDescription(request.description());
        existingCategory.setDescriptionEn(request.descriptionEn());

        // Parent category güncelle
        if (request.parentCategoryId() != null) {
            Category parent = categoryRepository.findById(request.parentCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent category not found with id: " + request.parentCategoryId()));

            // Alt kategorinin üst kategori olmasını engelle
            if (isDescendant(parent, existingCategory)) {
                throw new BadCredentialsException("Cannot set a descendant category as parent");
            }

            existingCategory.setParentCategory(parent);
        } else {
            existingCategory.setParentCategory(null);
        }

        Category updatedCategory = categoryRepository.save(existingCategory);
        logger.info("Category updated successfully with id: " + updatedCategory.getId());

        return mapToResponse(updatedCategory);
    }

    // Kategori sil (soft delete)
    @Transactional
    public void deleteCategory(Long id) {
        logger.info("Deleting category with id: " + id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));

        // Alt kategorisi varsa silinemez
        List<Category> children = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(id, EntityStatus.ACTIVE);
        if (!children.isEmpty()) {
            throw new BadCredentialsException("Cannot delete category with subcategories. Please delete subcategories first.");
        }

        // Soft delete
        category.setStatus(EntityStatus.DELETED);
        categoryRepository.save(category);

        logger.info("Category deleted successfully with id: " + id);
    }

    // Kategori geri yükle
    @Transactional
    public CategoryResponse restoreCategory(Long id) {
        logger.info("Restoring category with id: " + id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Category not found with id: " + id));

        category.setStatus(EntityStatus.ACTIVE);

        Category restoredCategory = categoryRepository.save(category);
        logger.info("Category restored successfully with id: " + id);

        return mapToResponse(restoredCategory);
    }

    public List<CategoryTreeResponse> getCategoryTree(int maxDepth) {
        List<Category> roots = categoryRepository.findByParentCategoryIsNullAndStatusOrderByNameAsc(EntityStatus.ACTIVE);
        Language language = localizationService.getCurrentLanguage();
        boolean includeTranslations = canViewTranslations();

        return roots.stream()
                .map(root -> buildTreeNode(root, 1, maxDepth, language, includeTranslations))
                .collect(Collectors.toList());
    }

    private CategoryTreeResponse buildTreeNode(Category category, int currentDepth, int maxDepth,
                                               Language language, boolean includeTranslations) {
        // Repository'den alt kategorileri çek
        List<Category> directChildren = categoryRepository.findByParentCategoryIdAndStatusOrderByNameAsc(
                category.getId(), EntityStatus.ACTIVE);

        List<CategoryTreeResponse> children = null;

        if (currentDepth < maxDepth && !directChildren.isEmpty()) {
            children = directChildren.stream()
                    .map(child -> buildTreeNode(child, currentDepth + 1, maxDepth, language, includeTranslations))
                    .collect(Collectors.toList());
        }

        boolean hasChildren = !directChildren.isEmpty();

        // Parent bilgilerini set et
        Long parentId = category.getParentCategory() != null ?
                category.getParentCategory().getId() : null;
        String parentName = category.getParentCategory() != null ?
                category.getParentCategory().getLocalizedName(language) : null;
        String parentNameEn = includeTranslations && category.getParentCategory() != null ?
                category.getParentCategory().getNameEn() : null;

        return new CategoryTreeResponse(
                category.getId(),
                category.getLocalizedName(language),
                includeTranslations ? category.getNameEn() : null,
                category.getLocalizedDescription(language),
                includeTranslations ? category.getDescriptionEn() : null,
                parentId,
                parentName,
                parentNameEn,
                hasChildren,
                children
        );
    }

    private CategorySummary mapToSummary(Category category) {
        Language language = localizationService.getCurrentLanguage();
        boolean includeTranslations = canViewTranslations();

        return new CategorySummary(
                category.getId(),
                category.getLocalizedName(language),
                includeTranslations ? category.getNameEn() : null,
                category.getLocalizedDescription(language),
                includeTranslations ? category.getDescriptionEn() : null,
                !category.getChildren().isEmpty()
        );
    }

    private CategoryResponse mapToResponse(Category category) {
        Language language = localizationService.getCurrentLanguage();
        boolean includeTranslations = canViewTranslations();
        Category parent = category.getParentCategory();

        String parentName = parent != null ? parent.getLocalizedName(language) : null;
        String parentNameEn = includeTranslations && parent != null ? parent.getNameEn() : null;

        return new CategoryResponse(
                category.getId(),
                category.getLocalizedName(language),
                includeTranslations ? category.getNameEn() : null,
                category.getLocalizedDescription(language),
                includeTranslations ? category.getDescriptionEn() : null,
                parentName,
                parentNameEn,
                category.getCreatedAt(),
                category.getStatus() != null ? category.getStatus().getDisplayName() : null
        );
    }

    private boolean canViewTranslations() {
        return securityService.hasPermission("CATEGORY_MANAGE")
                || securityService.hasPermission("CATEGORY_UPDATE")
                || securityService.hasPermission("CATEGORY_CREATE");
    }

    // Kategori adı benzersizlik kontrolü
    private void validateCategoryNames(String name, String nameEn, Long parentId, Long excludeId) {
        boolean exists;

        if (excludeId != null) {
            // Güncelleme durumunda
            if (parentId != null) {
                exists = categoryRepository.existsByNameIgnoreCaseAndParentCategoryIdAndStatusAndIdNot(
                        name, parentId, EntityStatus.ACTIVE, excludeId);
            } else {
                exists = categoryRepository.existsByNameIgnoreCaseAndStatusAndIdNot(
                        name, EntityStatus.ACTIVE, excludeId);
            }
        } else {
            // Yeni oluşturma durumunda
            if (parentId != null) {
                exists = categoryRepository.existsByNameIgnoreCaseAndParentCategoryIdAndStatus(
                        name, parentId, EntityStatus.ACTIVE);
            } else {
                exists = categoryRepository.existsByNameIgnoreCaseAndStatus(name, EntityStatus.ACTIVE);
            }
        }

        if (exists) {
            throw new BadCredentialsException("Category name already exists in the same level: " + name);
        }

        if (nameEn == null || nameEn.isBlank()) {
            return;
        }

        boolean englishExists;

        if (excludeId != null) {
            if (parentId != null) {
                englishExists = categoryRepository.existsByNameEnIgnoreCaseAndParentCategoryIdAndStatusAndIdNot(
                        nameEn, parentId, EntityStatus.ACTIVE, excludeId);
            } else {
                englishExists = categoryRepository.existsByNameEnIgnoreCaseAndStatusAndIdNot(
                        nameEn, EntityStatus.ACTIVE, excludeId);
            }
        } else {
            if (parentId != null) {
                englishExists = categoryRepository.existsByNameEnIgnoreCaseAndParentCategoryIdAndStatus(
                        nameEn, parentId, EntityStatus.ACTIVE);
            } else {
                englishExists = categoryRepository.existsByNameEnIgnoreCaseAndStatus(nameEn, EntityStatus.ACTIVE);
            }
        }

        if (englishExists) {
            throw new BadCredentialsException("Category English name already exists in the same level: " + nameEn);
        }
    }

    // Döngüsel referans kontrolü
    private boolean isDescendant(Category potentialAncestor, Category category) {
        Category current = potentialAncestor.getParentCategory();
        while (current != null) {
            if (current.getId().equals(category.getId())) {
                return true;
            }
            current = current.getParentCategory();
        }
        return false;
    }
}