package com.maxx_global.controller;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.auth.LoginRequest;
import com.maxx_global.dto.auth.LoginResponse;
import com.maxx_global.dto.auth.RegisterRequest;
import com.maxx_global.security.CustomUserDetails;
import com.maxx_global.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

//    @PostMapping("/register")
//    public ResponseEntity<AppUserResponse> register(@RequestBody RegisterRequest request) {
//        AppUserResponse createdUser = authService.registerUser(request);
//        return ResponseEntity.ok(createdUser);
//    }

    @GetMapping("/test")
    public ResponseEntity<?> testPrincipal() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails) {
            return ResponseEntity.ok("Principal is CustomUserDetails: " + ((CustomUserDetails) principal).getUsername());
        } else if (principal instanceof String) {
            return ResponseEntity.ok("Principal is String: " + principal);
        } else {
            return ResponseEntity.ok("Principal is other type: " + principal.getClass().getName());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }


}
