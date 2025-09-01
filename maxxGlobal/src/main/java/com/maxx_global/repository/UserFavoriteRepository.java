package com.maxx_global.repository;

import com.maxx_global.entity.UserFavorite;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    // Kullanıcının favori ürünlerini getir (sayfalama ile)
    @Query("SELECT uf FROM UserFavorite uf " +
            "JOIN FETCH uf.product p " +
            "JOIN FETCH p.category " +
            "LEFT JOIN FETCH p.images " +
            "WHERE uf.user.id = :userId AND uf.status = :status " +
            "AND p.status = :status " +
            "ORDER BY uf.createdAt DESC")
    Page<UserFavorite> findUserFavorites(@Param("userId") Long userId,
                                         @Param("status") EntityStatus status,
                                         Pageable pageable);

    // Kullanıcının favori ürün sayısı
    @Query("SELECT COUNT(uf) FROM UserFavorite uf " +
            "WHERE uf.user.id = :userId AND uf.status = :status " +
            "AND uf.product.status = :status")
    Long countUserFavorites(@Param("userId") Long userId, @Param("status") EntityStatus status);

    // Belirli ürün kullanıcının favorilerinde mi?
    Optional<UserFavorite> findByUserIdAndProductIdAndStatus(Long userId, Long productId, EntityStatus status);

    // Kullanıcının favori ürün ID'lerini getir (performans için)
    @Query("SELECT uf.product.id FROM UserFavorite uf " +
            "WHERE uf.user.id = :userId AND uf.status = :status " +
            "AND uf.product.status = :status")
    List<Long> findUserFavoriteProductIds(@Param("userId") Long userId, @Param("status") EntityStatus status);

    // En çok favorilenen ürünler (istatistik için)
    @Query("SELECT uf.product.id, COUNT(uf) as favoriteCount FROM UserFavorite uf " +
            "WHERE uf.status = :status AND uf.product.status = :status " +
            "GROUP BY uf.product.id " +
            "ORDER BY favoriteCount DESC")
    Page<Object[]> findMostFavoritedProducts(@Param("status") EntityStatus status, Pageable pageable);

    // Kategori bazında kullanıcının favorileri
    @Query("SELECT uf FROM UserFavorite uf " +
            "JOIN FETCH uf.product p " +
            "WHERE uf.user.id = :userId AND p.category.id = :categoryId " +
            "AND uf.status = :status AND p.status = :status " +
            "ORDER BY uf.createdAt DESC")
    List<UserFavorite> findUserFavoritesByCategory(@Param("userId") Long userId,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("status") EntityStatus status);
}