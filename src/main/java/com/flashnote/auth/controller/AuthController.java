package com.flashnote.auth.controller;

import com.flashnote.auth.dto.LoginRequest;
import com.flashnote.auth.dto.LoginResponse;
import com.flashnote.auth.dto.RegisterRequest;
import com.flashnote.auth.service.AuthService;
import com.flashnote.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("Register success", null);
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@RequestParam("refreshToken") String refreshToken) {
        return ApiResponse.success(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        authService.logout(authHeader);
        return ApiResponse.success("Logout success", null);
    }
}
