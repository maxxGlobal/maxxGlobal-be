package com.maxx_global.service;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.auth.LoginRequest;
import com.maxx_global.dto.auth.LoginResponse;
import com.maxx_global.dto.auth.RegisterRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Role;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.RoleRepository;
import com.maxx_global.dto.appUser.AppUserMapper;
import com.maxx_global.security.CustomUserDetails;
import com.maxx_global.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service // Logging için
public class AuthService {

    private final AppUserRepository userRepository;
    private final DealerRepository dealerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppUserMapper appUserMapper;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    public AuthService(AppUserRepository userRepository, DealerRepository dealerRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder, AppUserMapper appUserMapper, JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.dealerRepository = dealerRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.appUserMapper = appUserMapper;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }


    public LoginResponse login(LoginRequest request) {
        try {
            // ✅ STATUS KONTROLÜ EKLE
            AppUser user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.email()));

            // Status kontrolü
            if (user.getStatus() == EntityStatus.DELETED) {
                throw new BadCredentialsException("User account has been deleted");
            }

            if (user.getStatus() == EntityStatus.INACTIVE) {
                throw new BadCredentialsException("User account is inactive");
            }

            // Authentication (bu noktada CustomUserDetailsService de kontrol eder)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));

            // JWT üret
            String jwtToken = jwtService.generateToken(new CustomUserDetails(user));
            AppUserResponse userResponse = appUserMapper.toDto(user);
            boolean isDealer = !Objects.isNull(userResponse.dealer());

            return new LoginResponse(jwtToken, userResponse, isDealer);

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid email or password");
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }
}
