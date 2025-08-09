package com.maxx_global.service;

import com.maxx_global.dto.appUser.AppUserMapper;
import com.maxx_global.dto.appUser.AppUserRequest;
import com.maxx_global.dto.appUser.AppUserResponse;
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

@Service
@Transactional
public class AppUserService {

    private static final Logger logger = Logger.getLogger(AppUserService.class.getName());

    private final AppUserRepository appUserRepository;
    private final DealerRepository dealerRepository;
    private final RoleRepository roleRepository;
    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AppUserService(AppUserRepository appUserRepository,
                          DealerRepository dealerRepository,
                          RoleRepository roleRepository,
                          AppUserMapper appUserMapper,
                          PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.dealerRepository = dealerRepository;
        this.roleRepository = roleRepository;
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Kullanıcı bilgilerini günceller
     * @param userId Güncellenecek kullanıcının ID'si
     * @param updateRequest Güncelleme bilgileri
     * @param currentUser Güncellemeyi yapan kullanıcı
     * @return Güncellenmiş kullanıcı bilgileri
     */
    @PreAuthorize("hasPermission(#userId, 'AppUser', 'UPDATE') or hasPermission(null, 'USER_MANAGE')")
    public AppUserResponse updateUser(Long userId, AppUserRequest updateRequest, AppUser currentUser) {

        // Kullanıcıyı bul
        AppUser existingUser = appUserRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        // Kendi bilgilerini güncelleyip güncellemediğini kontrol et
        boolean isUpdatingSelf = existingUser.getId().equals(currentUser.getId());

        // Email benzersizlik kontrolü
        if (updateRequest.email() != null && !updateRequest.email().equals(existingUser.getEmail())) {
            if (appUserRepository.existsByEmailAndIdNot(updateRequest.email(), userId)) {
                throw new BadCredentialsException("Bu email adresi zaten kullanılıyor: " + updateRequest.email());
            }
        }

        // Dealer güncellemesi - sadece kendi bilgilerini güncellemeyen ve USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if (updateRequest.dealerId() != null && !isUpdatingSelf) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Dealer bilgilerini güncelleme yetkiniz yok");
            }

            Dealer dealer = dealerRepository.findById(updateRequest.dealerId())
                    .orElseThrow(() -> new EntityNotFoundException("Dealer bulunamadı: " + updateRequest.dealerId()));
            existingUser.setDealer(dealer);
        }


        // Role güncellemesi - sadece USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if (updateRequest.roleIds() != null && !updateRequest.roleIds().isEmpty()) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Rol bilgilerini güncelleme yetkiniz yok");
            }

            Set<Role> newRoles = new HashSet<>(roleRepository.findAllById(updateRequest.roleIds()));
            if (newRoles.size() != updateRequest.roleIds().size()) {
                throw new EntityNotFoundException("Bir veya daha fazla rol bulunamadı");
            }
            existingUser.setRoles(newRoles);
        }

        // Status güncellemesi - sadece USER_MANAGE yetkisi olan kullanıcılar yapabilir
        if (updateRequest.status() != null && !isUpdatingSelf) {
            if (!hasUserManagePermission(currentUser)) {
                throw new SecurityException("Status bilgilerini güncelleme yetkiniz yok");
            }
            existingUser.setStatus(EntityStatus.valueOf(updateRequest.status()));
        }

        // MapStruct ile tüm null olmayan alanları güncelle
        appUserMapper.updateEntityFromRequest(updateRequest, existingUser);


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
                .anyMatch(permission -> "USER_MANAGE".equals(permission.getName()));
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
}