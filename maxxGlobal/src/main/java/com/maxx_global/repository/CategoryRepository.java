package com.maxx_global.repository;

import com.maxx_global.entity.Category;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Aktif kategorileri getir
    List<Category> findByStatusOrderByNameAsc(EntityStatus status);

    // Root kategoriler (parent'ı olmayan)
    List<Category> findByParentCategoryIsNullAndStatusOrderByNameAsc(EntityStatus status);

    // Belirli kategorinin alt kategorileri
    List<Category> findByParentCategoryIdAndStatusOrderByNameAsc(Long parentId, EntityStatus status);

    // Kategori adında arama (case-insensitive)
    @Query("SELECT c FROM Category c WHERE (LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :name, '%'))) AND c.status = :status ORDER BY c.name ASC")
    Page<Category> searchByName(@Param("name") String name, @Param("status") EntityStatus status, Pageable pageable);

    // Tüm kategorilerde arama (status kontrolü ile)
    @Query("SELECT c FROM Category c WHERE (LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR LOWER(c.nameEn) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND c.status = :status ORDER BY c.name ASC")
    Page<Category> searchCategories(@Param("searchTerm") String searchTerm, @Param("status") EntityStatus status, Pageable pageable);

    // Kategori adı varlık kontrolü (case-insensitive)
    boolean existsByNameIgnoreCaseAndStatus(String name, EntityStatus status);
    boolean existsByNameIgnoreCaseAndStatusAndIdNot(String name, EntityStatus status, Long id);
    boolean existsByNameEnIgnoreCaseAndStatus(String nameEn, EntityStatus status);
    boolean existsByNameEnIgnoreCaseAndStatusAndIdNot(String nameEn, EntityStatus status, Long id);

    // Parent kategori kontrolü ile varlık kontrolü
    boolean existsByNameIgnoreCaseAndParentCategoryIdAndStatus(String name, Long parentId, EntityStatus status);
    boolean existsByNameIgnoreCaseAndParentCategoryIdAndStatusAndIdNot(String name, Long parentId, EntityStatus status, Long id);
    boolean existsByNameEnIgnoreCaseAndParentCategoryIdAndStatus(String nameEn, Long parentId, EntityStatus status);
    boolean existsByNameEnIgnoreCaseAndParentCategoryIdAndStatusAndIdNot(String nameEn, Long parentId, EntityStatus status, Long id);

    // Leaf kategoriler (alt kategorisi olmayan)
    @Query("SELECT c FROM Category c WHERE c.status = :status AND NOT EXISTS (SELECT 1 FROM Category child WHERE child.parentCategory = c AND child.status = :status) ORDER BY c.name ASC")
    List<Category> findLeafCategories(@Param("status") EntityStatus status);

}