package com.maxx_global.security;

import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Permission;
import com.maxx_global.entity.Role;
import com.maxx_global.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Set<String> authorities = new HashSet<>();

        for (Role role : user.getRoles()) {
            // "ROLE_" prefix ile birlikte eklenmeli
            authorities.add("ROLE_" + role.getName());

            if (role.getPermissions() != null) {
                authorities.addAll(
                        role.getPermissions().stream()
                                .map(Permission::getName)
                                .collect(Collectors.toSet())
                );
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
