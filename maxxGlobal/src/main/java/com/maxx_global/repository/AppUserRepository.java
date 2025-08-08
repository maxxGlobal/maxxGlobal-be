package com.maxx_global.repository;

import com.maxx_global.entity.AppUser;
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
}
