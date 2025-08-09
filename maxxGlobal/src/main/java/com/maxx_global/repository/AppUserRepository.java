package com.maxx_global.repository;

import com.maxx_global.entity.AppUser;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    @Query("""
    SELECT u FROM AppUser u
    LEFT JOIN FETCH u.roles r
    LEFT JOIN FETCH r.permissions
    WHERE u.email = :email
    """)
    Optional<AppUser> findByEmailWithRolesAndPermissions(@Param("email") String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("SELECT u FROM AppUser u WHERE u.dealer.id = :dealerId")
    List<AppUser> findByDealerId(Long dealerId);

    @Query("SELECT u FROM AppUser u JOIN u.roles r WHERE r.name = :roleName")
    List<AppUser> findByRoleName(String roleName);

    // AppUserRepository'ye eklenecek
    @Query("SELECT u FROM AppUser u WHERE " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.address) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY u.firstName ASC")
    Page<AppUser> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Aktif kullanıcılarda arama için
    @Query("SELECT u FROM AppUser u WHERE u.status = :status AND " +
            "(LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.address) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
            "ORDER BY u.firstName ASC")
    Page<AppUser> searchActiveUsers(@Param("searchTerm") String searchTerm,
                                    @Param("status") EntityStatus status,
                                    Pageable pageable);


    Page<AppUser> findByStatus(EntityStatus status, Pageable pageable);

}
