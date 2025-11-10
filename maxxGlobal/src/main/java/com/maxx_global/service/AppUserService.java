package com.maxx_global.service;

import com.maxx_global.dto.appUser.AppUserMapper;
import com.maxx_global.dto.appUser.AppUserRequest;
import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.auth.RegisterRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppUserService {

    private static final Logger logger = Logger.getLogger(AppUserService.class.getName());

    private final AppUserRepository appUserRepository;
    private final DealerService dealerService;
    private final RoleService roleService;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final DealerRepository dealerRepository;

    public AppUserService(AppUserRepository appUserRepository,
                          DealerService dealerService,
                          RoleService roleService,
                          AppUserMapper appUserMapper,
                          PasswordEncoder passwordEncoder, RoleRepository roleRepository
    ,DealerRepository dealerRepository) {
        this.appUserRepository = appUserRepository;
        this.dealerService = dealerService;
        this.roleService = roleService;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.dealerRepository = dealerRepository;
    }

    public AppUserResponse registerUser(RegisterRequest request) {
        // 1. Email zaten kayıtlı mı kontrol
        if (appUserRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        // 2. Dealer varsa getir (artık opsiyonel)
        Dealer dealer = null;
        if (request.dealerId() != null) {
            dealer = dealerRepository.findById(request.dealerId())
                    .orElseThrow(() -> new RuntimeException("Dealer not found"));
        }

        // 3. Rolü getir
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new RuntimeException("Role not found"));

        // 4. Kullanıcı nesnesini oluştur
        AppUser user = new AppUser();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setAddress(request.address());
        user.setDealer(dealer); // null olabilir artık
        user.setRoles(Set.of(role));
        user.setAuthorizedUser(Boolean.TRUE.equals(request.authorizedUser()));

        Boolean emailNotifications = request.emailNotifications();
        if (emailNotifications != null) {
            user.setEmailNotifications(emailNotifications);
        } else {
            user.setEmailNotifications(true);
        }

        // 5. Kaydet
        AppUser savedUser = appUserRepository.save(user);

        // 6. DTO dönüş
        return appUserMapper.toDto(savedUser);
    }

    /**
     * Kullanıcı bilgilerini günceller
     * @param userId Güncellenecek kullanıcının ID'si
     * @param updateRequest Güncelleme bilgileri
     * @param currentUser Güncellemeyi yapan kullanıcı
     * @return Güncellenmiş kullanıcı bilgileri
     */
    public AppUserResponse updateUser(Long userId, AppUserRequest updateRequest, AppUser currentUser) {

        // Kullanıcıyı bul
        AppUser existingUser = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        // Kendi bilgilerini güncelleyip güncellemediğini kontrol et
        boolean isUpdatingSelf = existingUser.getId().equals(currentUser.getId());
        boolean hasUserManagePermissions = isUpdatingSelf || currentUser.getRoles().stream().anyMatch(role -> role.getPermissions().stream().anyMatch(permission -> permission.getName().equals("SYSTEM_ADMIN")));

        // Email benzersizlik kontrolü
        if (updateRequest.email() != null && !updateRequest.email().equals(existingUser.getEmail())) {
            if (appUserRepository.existsByEmailAndIdNot(updateRequest.email(), userId)) {
                throw new BadCredentialsException("Bu email adresi zaten kullanılıyor: " + updateRequest.email());
            }
        }

        // Dealer güncellemesi - sadece kendi bilgilerini güncellemeyen ve USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if ( !hasUserManagePermissions) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Dealer bilgilerini güncelleme yetkiniz yok");
            }

            Dealer dealer = dealerService.findById(updateRequest.dealerId());
            existingUser.setDealer(dealer);
        }


        // Role güncellemesi - sadece USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if (updateRequest.roleIds() != null && !updateRequest.roleIds().isEmpty()) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Rol bilgilerini güncelleme yetkiniz yok");
            }

            Set<Role> newRoles = new HashSet<>(roleService.findAllById(updateRequest.roleIds()));
            if (newRoles.size() != updateRequest.roleIds().size()) {
                throw new EntityNotFoundException("Bir veya daha fazla rol bulunamadı");
            }
            existingUser.setRoles(newRoles);
        }

        // Status güncellemesi - sadece USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if (updateRequest.status() != null && !hasUserManagePermissions) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Status bilgilerini güncelleme yetkiniz yok");
            }
            existingUser.setStatus(EntityStatus.valueOf(updateRequest.status()));
        }

        // MapStruct ile tüm null olmayan alanları güncelle
        appUserMapper.updateEntityFromRequest(updateRequest, existingUser);

        if (updateRequest.emailNotifications() != null) {
            existingUser.setEmailNotifications(updateRequest.emailNotifications());
        }


        if (updateRequest.password() != null && !updateRequest.password().isBlank()) {
            existingUser.setPassword(passwordEncoder.encode(updateRequest.password()));
        }
        // Kullanıcıyı kaydet
        AppUser savedUser = appUserRepository.save(existingUser);

        return appUserMapper.toDto(savedUser);
    }

    /**
     * Kullanıcıyı ID ile bulur
     */
    @PreAuthorize("hasPermission(#userId, 'AppUser', 'READ') or hasPermission(null, 'USER_MANAGE')")
    public AppUserResponse getUserById(Long userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        return appUserMapper.toDto(user);
    }

    /**
     * Tüm kullanıcıları listeler - sadece USER_MANAGE yetkisi olan kullanıcılar
     */
    // AppUserService'de
    public Page<AppUserResponse> getAllUsers(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching all users - page: " + page + ", size: " + size +
                ", sortBy: " + sortBy + ", direction: " + sortDirection);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> users = appUserRepository.findAll(pageable);
        return users.map(appUserMapper::toDto);
    }

    /**
     * Mevcut kullanıcının USER_MANAGE yetkisi olup olmadığını kontrol eder
     */
    private boolean hasUserManagePermission(AppUser user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> "SYSTEM_ADMIN".equals(permission.getName()));
    }

    public AppUser getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new SecurityException("Kullanıcı doğrulaması gerekli");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof AppUser appUser) {
            return appUser;
        }

        if (principal instanceof UserDetails userDetails) {
            String email = userDetails.getUsername();

            return appUserRepository.findByEmail(email)
                    .orElseThrow(() -> new SecurityException("Kullanıcı bulunamadı: " + email));
        }

        throw new SecurityException("Geçersiz kullanıcı bilgisi");
    }

// AppUserService'e eklenecek metotlar

    // Genel arama (tüm kullanıcılar)
    public Page<AppUserResponse> searchUsers(String searchTerm, int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching users with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> users = appUserRepository.searchUsers(searchTerm, pageable);
        return users.map(appUserMapper::toDto);
    }

    // Aktif kullanıcılarda arama
    public Page<AppUserResponse> searchActiveUsers(String searchTerm, int page, int size, String sortBy, String sortDirection) {
        logger.info("Searching active users with term: " + searchTerm);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> users = appUserRepository.searchActiveUsers(searchTerm, EntityStatus.ACTIVE, pageable);
        return users.map(appUserMapper::toDto);
    }

    public Page<AppUserResponse> getActiveUsers(int page, int size, String sortBy, String sortDirection) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> users = appUserRepository.findByStatus(EntityStatus.ACTIVE, pageable);
        return users.map(appUserMapper::toDto);
    }

    // AppUserService'e ekle
    public Page<AppUserResponse> getUsersByDealer(Long dealerId, int page, int size,
                                                  String sortBy, String sortDirection, boolean activeOnly) {
        logger.info("Fetching users for dealer: " + dealerId + ", activeOnly: " + activeOnly);

        // Bayi varlık kontrolü
        dealerService.findById(dealerId);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> users;
        if (activeOnly) {
            users = appUserRepository.findByDealerIdAndStatus(dealerId, EntityStatus.ACTIVE, pageable);
        } else {
            users = appUserRepository.findByDealerId(dealerId, pageable);
        }

        return users.map(appUserMapper::toDto);
    }
    // AppUserService.java'ya eklenecek metodlar:

    /**
     * Kullanıcıyı siler (soft delete)
     */
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public void deleteUser(Long userId, AppUser currentUser) {
        logger.info("Deleting user: " + userId + " by user: " + currentUser.getId());

        // Kullanıcıyı bul
        AppUser userToDelete = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        // Kendini silmeye çalışıyor mu kontrol et
        if (userToDelete.getId().equals(currentUser.getId())) {
            throw new BadCredentialsException("Kendinizi silemezsiniz");
        }

        // Zaten silinmiş mi kontrol et
        if (userToDelete.getStatus() == EntityStatus.DELETED) {
            throw new BadCredentialsException("Kullanıcı zaten silinmiş durumda");
        }

        // Aktif siparişleri var mı kontrol et
        if (hasActiveOrders(userId)) {
            throw new IllegalStateException("Kullanıcının aktif siparişleri olduğu için silinemez. " +
                    "Önce siparişleri tamamlanmalı veya iptal edilmelidir.");
        }

        // Super admin rolüne sahip son kullanıcı mı kontrol et
        if (isLastSuperAdmin(userToDelete)) {
            throw new IllegalStateException("Son sistem yöneticisi silinemez");
        }

        // Soft delete - status'u DELETED yap
        userToDelete.setStatus(EntityStatus.DELETED);
        appUserRepository.save(userToDelete);

        logger.info("User soft deleted successfully: " + userId);
    }

    /**
     * Silinmiş kullanıcıyı geri yükler
     */
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public AppUserResponse restoreUser(Long userId, AppUser currentUser) {
        logger.info("Restoring user: " + userId + " by user: " + currentUser.getId());

        // Kullanıcıyı bul
        AppUser userToRestore = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        // Silinmiş durumda mı kontrol et
        if (userToRestore.getStatus() != EntityStatus.DELETED) {
            throw new BadCredentialsException("Kullanıcı silinmiş durumda değil. Mevcut durum: " + userToRestore.getStatus());
        }

        // Email çakışması var mı kontrol et (başka aktif kullanıcı aynı email'i kullanıyor mu)
        if (appUserRepository.existsByEmailAndIdNot(userToRestore.getEmail(), userId)) {
            throw new BadCredentialsException("Bu email adresi başka bir kullanıcı tarafından kullanılıyor: " +
                    userToRestore.getEmail() + ". Önce email adresini değiştirin.");
        }

        // Status'u ACTIVE yap
        userToRestore.setStatus(EntityStatus.ACTIVE);
        AppUser restoredUser = appUserRepository.save(userToRestore);

        logger.info("User restored successfully: " + userId);
        return appUserMapper.toDto(restoredUser);
    }

    /**
     * Kullanıcının aktif siparişleri var mı kontrol et
     */
    private boolean hasActiveOrders(Long userId) {
        // OrderRepository'den kullanıcının aktif sipariş sayısını al
        // Şimdilik basit kontrol - gerçek implementasyonda OrderService kullanılabilir
        try {
            List<AppUser> userOrders = appUserRepository.findByDealerId(userId);
            // Bu basit bir kontrol - gerçekte OrderRepository'den:
            // return orderRepository.countActiveOrdersByUserId(userId) > 0;
            return false; // Şimdilik false döndür, ileride OrderService ile kontrol edilecek
        } catch (Exception e) {
            logger.warning("Could not check active orders for user: " + userId + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Son süper admin mi kontrol et
     */
    private boolean isLastSuperAdmin(AppUser user) {
        // Kullanıcının SYSTEM_ADMIN rolü var mı
        boolean isSuperAdmin = user.getRoles().stream()
                .anyMatch(role -> "SYSTEM_ADMIN".equals(role.getName()));

        if (!isSuperAdmin) {
            return false;
        }

        // Sistemde kaç tane aktif SYSTEM_ADMIN var
        List<AppUser> systemAdmins = appUserRepository.findByRoleName("SYSTEM_ADMIN");
        long activeSystemAdminCount = systemAdmins.stream()
                .filter(u -> u.getStatus() == EntityStatus.ACTIVE)
                .count();

        // Eğer sadece 1 tane aktif sistem admini varsa ve o da silinmeye çalışılıyorsa
        return activeSystemAdminCount <= 1;
    }

    /**
     * Silinmiş kullanıcıları getir (admin paneli için)
     */
    @PreAuthorize("hasPermission(null, 'USER_MANAGE')")
    public Page<AppUserResponse> getDeletedUsers(int page, int size, String sortBy, String sortDirection) {
        logger.info("Fetching deleted users");

        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<AppUser> deletedUsers = appUserRepository.findByStatus(EntityStatus.DELETED, pageable);
        return deletedUsers.map(appUserMapper::toDto);
    }

    public List<AppUser> getUsersWithUserPermissions(List<String> permissions) {
        logger.info("Fetching users with USER_READ or USER_DELETE permissions");

        return appUserRepository.findUsersWithUserPermissions(permissions);


    }
}