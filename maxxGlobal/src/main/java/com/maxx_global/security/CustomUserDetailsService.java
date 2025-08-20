package com.maxx_global.security;

import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Permission;
import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public CustomUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // ✅ STATUS KONTROLÜ EKLE
        if (user.getStatus() == EntityStatus.DELETED) {
            throw new UsernameNotFoundException("User account has been deleted: " + email);
        }

        if (user.getStatus() == EntityStatus.INACTIVE) {
            throw new UsernameNotFoundException("User account is inactive: " + email);
        }

        // Sadece ACTIVE kullanıcılar login olabilir
        Set<String> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {
            authorities.add("ROLE_" + role.getName());
            for (Permission permission : role.getPermissions()) {
                authorities.add(permission.getName());
            }
        }

        List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return new CustomUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                grantedAuthorities
        );
    }

}
