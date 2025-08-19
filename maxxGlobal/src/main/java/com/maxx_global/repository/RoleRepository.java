package com.maxx_global.repository;


import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


// RoleRepository
public interface RoleRepository extends JpaRepository<Role, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    Optional<Role> findByName(String name);

    @Query("SELECT COUNT(u) > 0 FROM AppUser u JOIN u.roles r WHERE r.id = :roleId")
    boolean isRoleInUse(@Param("roleId") Long roleId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") Long id);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions")
    List<Role> findAllWithPermissions();

    @Query("SELECT r FROM Role r WHERE r.name IN :names")
    List<Role> findByNameIn(@Param("names") List<String> names);

    // Soft delete için - status'u DELETED olmayan rolleri getir
    @Query("SELECT r FROM Role r WHERE r.status != :status OR r.status IS NULL")
    List<Role> findByStatusNot(@Param("status") EntityStatus status);

    // Aktif rolleri getir
    @Query("SELECT r FROM Role r WHERE r.status = 'ACTIVE' OR r.status IS NULL")
    List<Role> findActiveRoles();

    // Silinmiş rolleri getir (admin paneli için)
    @Query("SELECT r FROM Role r WHERE r.status = 'DELETED'")
    List<Role> findDeletedRoles();

    @Query("SELECT r FROM Role r WHERE r.status = :status ORDER BY r.name ASC")
    List<Role> findByStatusOrderByNameAsc(@Param("status") EntityStatus status);

}