package com.maxx_global.controller;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.auth.LoginRequest;
import com.maxx_global.dto.auth.LoginResponse;
import com.maxx_global.dto.auth.RegisterRequest;
import com.maxx_global.security.CustomUserDetails;
import com.maxx_global.service.AuthService;
import jakarta.validation.Valid;
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

    //TODO:bu daha sonra değişecek. AppUserControllera taşınacak. Sadece Adminler kullancı ekleyebilece.
    @PostMapping("/register")
    public ResponseEntity<AppUserResponse> register(@Valid @RequestBody RegisterRequest request) {
        AppUserResponse createdUser = authService.registerUser(request);
        return ResponseEntity.ok(createdUser);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }


}
