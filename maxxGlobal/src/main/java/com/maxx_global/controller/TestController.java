package com.maxx_global.controller;

import com.maxx_global.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/public")
    public ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("Bu endpoint herkese açık - Token gerektirmez");
    }

    @GetMapping("/protected")
    public ResponseEntity<String> protectedEndpoint() {
        return ResponseEntity.ok("Bu endpoint token gerektiriyor - Başarılı!");
    }

    @GetMapping("/user-info")
    public ResponseEntity<Map<String, Object>> getUserInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();

        if (authentication.getPrincipal() instanceof CustomUserDetails) {
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            userInfo.put("userId", userDetails.getId());
            userInfo.put("email", userDetails.getUsername());
            userInfo.put("authorities", userDetails.getAuthorities());
        }

        userInfo.put("authenticated", authentication.isAuthenticated());
        userInfo.put("principal", authentication.getName());

        return ResponseEntity.ok(userInfo);
    }

    @GetMapping("/aaaa")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    public String test() {
        return "ok";
    }
}