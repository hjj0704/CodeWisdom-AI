package com.codewisdom.resource.controller;

import com.codewisdom.common.api.R;
import com.codewisdom.resource.dto.AuthResponse;
import com.codewisdom.resource.dto.LoginRequest;
import com.codewisdom.resource.dto.RegisterRequest;
import com.codewisdom.resource.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public R<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @PostMapping("/login")
    public R<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }
}
