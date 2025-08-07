package com.maxx_global.service;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.auth.LoginRequest;
import com.maxx_global.dto.auth.LoginResponse;
import com.maxx_global.dto.auth.RegisterRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Role;
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

    public AppUserResponse registerUser(RegisterRequest request) {
        // 1. Email zaten kayıtlı mı kontrol
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email already in use");
        }

        // 2. Dealer varsa getir
        Dealer dealer = null;
        if (request.dealerId() != null) {
            dealer = dealerRepository.findById(request.dealerId()).orElseThrow(() -> new RuntimeException("Dealer not found"));
        }

        // 3. Rolü getir
        Role role = roleRepository.findById(request.roleId()).orElseThrow(() -> new RuntimeException("Role not found"));

        // 4. Kullanıcı nesnesini oluştur
        AppUser user = new AppUser();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPhoneNumber(request.phoneNumber());
        user.setDealer(dealer);
        user.setRoles(Set.of(role));

        // 5. Kaydet
        AppUser savedUser = userRepository.save(user);

        // 6. DTO dönüş
        return appUserMapper.toDto(savedUser);
    }


    public LoginResponse login(LoginRequest request) {
        try {
            // Önce kullanıcının var olup olmadığını kontrol edin
            AppUser user = userRepository.findByEmail(request.email()).orElseThrow(() -> new UsernameNotFoundException("User not found: " + request.email()));


            // Authentication
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.email(), request.password()));


            // JWT üret
            String jwtToken = jwtService.generateToken(new CustomUserDetails(user));

            // User Response map'le
            AppUserResponse userResponse = appUserMapper.toDto(user);

            return new LoginResponse(jwtToken, userResponse);

        } catch (BadCredentialsException e) {

            throw new BadCredentialsException("Invalid email or password");
        } catch (UsernameNotFoundException e) {

            throw e;
        } catch (Exception e) {

            throw new RuntimeException("Authentication failed: " + e.getMessage());
        }
    }
}
