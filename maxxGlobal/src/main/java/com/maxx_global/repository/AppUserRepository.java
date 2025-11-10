package com.maxx_global.repository;

import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
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

    // AppUserRepository'ye ekle
    Page<AppUser> findByDealerId(Long dealerId, Pageable pageable);
    Page<AppUser> findByDealerIdAndStatus(Long dealerId, EntityStatus status, Pageable pageable);

    // Bonus - istatistik için
    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.dealer.id = :dealerId AND u.status = :status")
    Long countByDealerIdAndStatus(@Param("dealerId") Long dealerId, @Param("status") EntityStatus status);



    // Rol bazlı kullanıcı arama (super admin kontrolü için)
    @Query("SELECT u FROM AppUser u JOIN u.roles r WHERE r.name = :roleName AND u.status = :status")
    List<AppUser> findByRoleNameAndStatus(@Param("roleName") String roleName, @Param("status") EntityStatus status);

    @Query("SELECT DISTINCT u FROM AppUser u JOIN u.roles r WHERE r.name IN ('ADMIN', 'SUPER_ADMIN')")
    List<AppUser> findAdminAndSuperAdminUsers();

    // Email notification aktif olan admin kullanıcıları
    @Query("SELECT DISTINCT u FROM AppUser u JOIN u.roles r WHERE r.name IN ('ADMIN', 'SUPER_ADMIN') " +
            "AND u.emailNotifications = true AND u.email IS NOT NULL")
    List<AppUser> findAdminUsersForEmailNotification();

    @Query("""
    SELECT DISTINCT u FROM AppUser u
    LEFT JOIN FETCH u.roles r
    LEFT JOIN FETCH r.permissions p
    WHERE u.dealer.id = :dealerId
      AND u.authorizedUser = true
      AND u.emailNotifications = true
      AND u.email IS NOT NULL
      AND u.status = 'ACTIVE'
    """)
    List<AppUser> findAuthorizedUsersForDealer(@Param("dealerId") Long dealerId);

    // Belirli rollere sahip kullanıcıları getir
    @Query("SELECT DISTINCT u FROM AppUser u JOIN u.roles r WHERE r.name IN :roleNames")
    List<AppUser> findByRoleNames(@Param("roleNames") List<String> roleNames);

    // Toplu bildirim için kullanıcı listeleri
    List<AppUser> findByStatus(EntityStatus status);

    @Query("SELECT u FROM AppUser u JOIN u.roles r WHERE r.name = :roleName AND u.status = 'ACTIVE'")
    List<AppUser> findActiveUsersByRoleName(@Param("roleName") String roleName);


    @Query("SELECT u FROM AppUser u WHERE u.dealer.id = :dealerId AND u.status = :status")
    List<AppUser> findByDealerIdAndStatusIs_Active(@Param("dealerId") Long dealerId,@Param("status") EntityStatus status);

    long countByStatus(EntityStatus status);

    @Query("SELECT u FROM AppUser u WHERE u.status = :entityStatus")
    List<AppUser> findByStatusIs_Active(EntityStatus entityStatus);

    // AppUserRepository.java'ya eklenecek
    @Query("""
    SELECT DISTINCT u FROM AppUser u 
    JOIN u.roles r 
    JOIN r.permissions p 
    WHERE p.name IN :permission 
    AND u.status = 'ACTIVE'
    ORDER BY u.firstName ASC, u.lastName ASC
    """)
    List<AppUser> findUsersWithUserPermissions(@Param("permission") List<String> permission);
}
