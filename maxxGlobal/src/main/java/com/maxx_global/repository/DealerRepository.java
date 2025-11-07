package com.maxx_global.repository;

import com.maxx_global.entity.Dealer;
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
public interface DealerRepository extends JpaRepository<Dealer, Long> {

    // Aktif bayileri getir (status kontrolü ile)
    List<Dealer> findByStatusOrderByNameAsc(EntityStatus status);

    // İsme göre arama (case-insensitive)
    @Query("SELECT d FROM Dealer d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%')) ORDER BY d.name ASC")
    Page<Dealer> findByNameContainingIgnoreCase(@Param("name") String name, Pageable pageable);

    // Genel arama (name, email, phone, mobile, address alanlarında)
    @Query("SELECT d FROM Dealer d WHERE " +
            "(LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.address) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "d.phone LIKE CONCAT('%', :searchTerm, '%') OR " +
            "d.mobile LIKE CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY d.name ASC")
    Page<Dealer> searchDealers(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Aktif bayiler için genel arama
    @Query("SELECT d FROM Dealer d WHERE d.status = :status AND " +
            "(LOWER(d.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(d.address) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "d.phone LIKE CONCAT('%', :searchTerm, '%') OR " +
            "d.mobile LIKE CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY d.name ASC")
    Page<Dealer> searchActiveOrInactiveDealers(@Param("searchTerm") String searchTerm,
                                               @Param("status") EntityStatus status,
                                               Pageable pageable);

    // Email ile bayi bulma
    Optional<Dealer> findByEmailIgnoreCase(String email);

    // Email'in başka bir bayi tarafından kullanılıp kullanılmadığını kontrol et
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // Telefon numarası ile arama
    @Query("SELECT d FROM Dealer d WHERE d.phone = :phone OR d.mobile = :mobile")
    List<Dealer> findByPhoneOrMobile(@Param("phone") String phone, @Param("mobile") String mobile);

    // Aktif bayiler için sayfalama ve sıralama
    Page<Dealer> findByStatusOrderByCreatedAtDesc(EntityStatus status, Pageable pageable);

    // Bayi adı varlık kontrolü (case-insensitive)
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
    boolean existsByEmailIgnoreCase(String email);

    List<Dealer> findDealersByStatus(EntityStatus status);
}