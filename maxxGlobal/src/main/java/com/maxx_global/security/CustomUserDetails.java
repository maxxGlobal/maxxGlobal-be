package com.maxx_global.security;

import com.maxx_global.entity.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class CustomUserDetails implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private List<SimpleGrantedAuthority> authorities;

    // AppUser constructor
    public CustomUserDetails(AppUser user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }

    // Manuel constructor
    public CustomUserDetails(Long id, String email, String password, List<SimpleGrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.authorities = authorities;
    }

    // ❌ HATA: Bu metodlar boş döndürüyor!
    // ✅ DÜZELTME: Gerçek değerleri döndürmeli
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities; // ❌ List.of() değil!
    }

    @Override
    public String getPassword() {
        return this.password; // ❌ "" değil!
    }

    @Override
    public String getUsername() {
        return this.email; // ❌ "" değil!
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // ❌ super call değil!
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // ❌ super call değil!
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // ❌ super call değil!
    }

    @Override
    public boolean isEnabled() {
        return true; // ❌ super call değil!
    }

    // Getter
    public Long getId() {
        return id;
    }
}